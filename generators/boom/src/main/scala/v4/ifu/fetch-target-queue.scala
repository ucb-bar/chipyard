//******************************************************************************
// Copyright (c) 2015 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Fetch Target Queue (FTQ)
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// Each entry in the FTQ holds the fetch address and branch prediction snapshot state.
//
// TODO:
// * reduce port counts.

package boom.v4.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.util.{Str}

import boom.v4.common._
import boom.v4.exu._
import boom.v4.util._

/**
 * FTQ Parameters used in configurations
 *
 * @param nEntries # of entries in the FTQ
 */
case class FtqParameters(
  nEntries: Int = 16
)

/**
 * Bundle to add to the FTQ RAM and to be used as the pass in IO
 */
class FTQBundle(implicit p: Parameters) extends BoomBundle
  with HasBoomFrontendParameters
{
  // IDX of instruction that was predicted taken, if any
  val cfi_idx   = Valid(UInt(log2Ceil(fetchWidth).W))
  // Was the CFI in this bundle found to be taken? or not
  val cfi_taken = Bool()
  // Was this CFI mispredicted by the branch prediction pipeline?
  val cfi_mispredicted = Bool()
  // What type of CFI was taken out of this bundle
  val cfi_type = UInt(CFI_SZ.W)
  // mask of branches which were visible in this fetch bundle
  val br_mask   = UInt(fetchWidth.W)
  // This CFI is likely a CALL
  val cfi_is_call   = Bool()
  // This CFI is likely a RET
  val cfi_is_ret    = Bool()
  // Is the NPC after the CFI +4 or +2
  val cfi_npc_plus4 = Bool()
  // What was the top of the RAS that this bundle saw?
  val ras_top = UInt(vaddrBitsExtended.W)
  val ras_idx = UInt(log2Ceil(nRasEntries).W)

  // Which bank did this start from?
  val start_bank = UInt(1.W)
}

class FTQInfo(implicit p: Parameters) extends BoomBundle
{
  val valid     = Bool()
  val entry     = new FTQBundle
  val ghist     = new GlobalHistory
  val pc        = UInt(vaddrBitsExtended.W)
}

/**
 * Queue to store the fetch PC and other relevant branch predictor signals that are inflight in the
 * processor.
 *
 * @param num_entries # of entries in the FTQ
 */
class FetchTargetQueue(implicit p: Parameters) extends BoomModule
  with HasBoomCoreParameters
  with HasBoomFrontendParameters
{
  val num_entries = ftqSz
  private val idx_sz = log2Ceil(num_entries)

  val io = IO(new BoomBundle {
    // Enqueue one entry for every fetch cycle.
    val enq = Flipped(Decoupled(new FetchBundle()))
    // Pass to FetchBuffer (newly fetched instructions).
    val enq_idx = Output(UInt(idx_sz.W))
    // ROB tells us the youngest committed ftq_idx to remove from FTQ.
    val deq = Flipped(Valid(UInt(idx_sz.W)))

    // Give PC info to BranchUnit.
    val arb_ftq_reqs = Input(Vec(3, UInt(log2Ceil(ftqSz).W)))
    val rrd_ftq_resps = Output(Vec(3, new FTQInfo))

    val com_pc    = Output(UInt(vaddrBitsExtended.W))


    // Used to regenerate PC for trace port stuff in FireSim
    // Don't tape this out, this blows up the FTQ
    val debug_ftq_idx  = Input(Vec(coreWidth, UInt(log2Ceil(ftqSz).W)))
    val debug_fetch_pc = Output(Vec(coreWidth, UInt(vaddrBitsExtended.W)))

    val redirect = Input(Valid(UInt(idx_sz.W)))

    val brupdate = Input(new BrUpdateInfo)

    val bpdupdate = Output(Valid(new BranchPredictionUpdate))

    val ras_update = Output(Bool())
    val ras_update_idx = Output(UInt(log2Ceil(nRasEntries).W))
    val ras_update_pc  = Output(UInt(vaddrBitsExtended.W))

  })
  val bpd_ptr    = RegInit(0.U(idx_sz.W))
  val deq_ptr    = RegInit(0.U(idx_sz.W))
  val enq_ptr    = RegInit(1.U(idx_sz.W))

  val full = ((WrapInc(WrapInc(enq_ptr, num_entries), num_entries) === bpd_ptr) ||
              (WrapInc(enq_ptr, num_entries) === bpd_ptr))


  val pcs      = Reg(Vec(num_entries, UInt(vaddrBitsExtended.W)))
  val meta     = SyncReadMem(num_entries, Vec(nBanks, UInt(bpdMaxMetaLength.W)))
  val ram      = Reg(Vec(num_entries, new FTQBundle))
  val ghist    = Seq.fill(2) { SyncReadMem(num_entries, new GlobalHistory) }
  val lhist    = if (useLHist) {
    Some(SyncReadMem(num_entries, Vec(nBanks, UInt(localHistoryLength.W))))
  } else {
    None
  }

  val do_enq = io.enq.fire


  // This register lets us initialize the ghist to 0
  val prev_ghist = RegInit((0.U).asTypeOf(new GlobalHistory))
  val prev_entry = RegInit((0.U).asTypeOf(new FTQBundle))
  val prev_pc    = RegInit(0.U(vaddrBitsExtended.W))
  when (do_enq) {

    pcs(enq_ptr)           := io.enq.bits.pc

    val new_entry = Wire(new FTQBundle)

    new_entry.cfi_idx   := io.enq.bits.cfi_idx
    // Initially, if we see a CFI, it is assumed to be taken.
    // Branch resolutions may change this
    new_entry.cfi_taken     := io.enq.bits.cfi_idx.valid
    new_entry.cfi_mispredicted := false.B
    new_entry.cfi_type      := io.enq.bits.cfi_type
    new_entry.cfi_is_call   := io.enq.bits.cfi_is_call
    new_entry.cfi_is_ret    := io.enq.bits.cfi_is_ret
    new_entry.cfi_npc_plus4 := io.enq.bits.cfi_npc_plus4
    new_entry.ras_top       := io.enq.bits.ras_top
    new_entry.ras_idx       := io.enq.bits.ghist.ras_idx
    new_entry.br_mask       := io.enq.bits.br_mask & io.enq.bits.mask
    new_entry.start_bank    := bank(io.enq.bits.pc)

    val new_ghist = Mux(io.enq.bits.ghist.current_saw_branch_not_taken,
      io.enq.bits.ghist,
      prev_ghist.update(
        prev_entry.br_mask,
        prev_entry.cfi_taken,
        prev_entry.br_mask(prev_entry.cfi_idx.bits),
        prev_entry.cfi_idx.bits,
        prev_entry.cfi_idx.valid,
        prev_pc,
        prev_entry.cfi_is_call,
        prev_entry.cfi_is_ret
      )
    )

    lhist.map( l => l.write(enq_ptr, io.enq.bits.lhist))
    ghist.map( g => g.write(enq_ptr, new_ghist))
    meta.write(enq_ptr, io.enq.bits.bpd_meta)
    ram(enq_ptr) := new_entry

    prev_pc    := io.enq.bits.pc
    prev_entry := new_entry
    prev_ghist := new_ghist

    enq_ptr := WrapInc(enq_ptr, num_entries)
  }

  io.enq_idx := enq_ptr

  io.bpdupdate.valid := false.B
  io.bpdupdate.bits  := DontCare

  when (io.deq.valid) {
    deq_ptr := io.deq.bits
  }

  // This register avoids a spurious bpd update on the first fetch packet
  val first_empty = RegInit(true.B)

  // We can update the branch predictors when we know the target of the
  // CFI in this fetch bundle

  val ras_update = WireInit(false.B)
  val ras_update_pc = WireInit(0.U(vaddrBitsExtended.W))
  val ras_update_idx = WireInit(0.U(log2Ceil(nRasEntries).W))
  io.ras_update     := RegNext(ras_update)
  io.ras_update_pc  := RegNext(ras_update_pc)
  io.ras_update_idx := RegNext(ras_update_idx)

  val bpd_update_mispredict = RegInit(false.B)
  val bpd_update_repair = RegInit(false.B)
  val bpd_repair_idx = Reg(UInt(log2Ceil(ftqSz).W))
  val bpd_end_idx = Reg(UInt(log2Ceil(ftqSz).W))
  val bpd_repair_pc = Reg(UInt(vaddrBitsExtended.W))

  val bpd_idx = Mux(io.redirect.valid, io.redirect.bits,
    Mux(bpd_update_repair || bpd_update_mispredict, bpd_repair_idx, bpd_ptr))
  val bpd_entry = RegNext(ram(bpd_idx))
  val bpd_ghist = ghist(0).read(bpd_idx, true.B)
  val bpd_lhist = if (useLHist) {
    lhist.get.read(bpd_idx, true.B)
  } else {
    VecInit(Seq.fill(nBanks) { 0.U })
  }
  val bpd_meta  = meta.read(bpd_idx, true.B) // TODO fix these SRAMs
  val bpd_pc    = RegNext(pcs(bpd_idx))
  val bpd_target = RegNext(pcs(WrapInc(bpd_idx, num_entries)))

  when (io.redirect.valid) {
    bpd_update_mispredict := false.B
    bpd_update_repair     := false.B
  } .elsewhen (RegNext(io.brupdate.b2.mispredict)) {
    bpd_update_mispredict := true.B
    bpd_repair_idx        := RegNext(io.brupdate.b2.uop.ftq_idx)
    bpd_end_idx           := RegNext(enq_ptr)
  } .elsewhen (bpd_update_mispredict) {
    bpd_update_mispredict := false.B
    bpd_update_repair     := true.B
    bpd_repair_idx        := WrapInc(bpd_repair_idx, num_entries)
  } .elsewhen (bpd_update_repair && RegNext(bpd_update_mispredict)) {
    bpd_repair_pc         := bpd_pc
    bpd_repair_idx        := WrapInc(bpd_repair_idx, num_entries)
  } .elsewhen (bpd_update_repair) {
    bpd_repair_idx        := WrapInc(bpd_repair_idx, num_entries)
    when (WrapInc(bpd_repair_idx, num_entries) === bpd_end_idx ||
      bpd_pc === bpd_repair_pc)  {
      bpd_update_repair := false.B
    }

  }


  val do_commit_update     = (!bpd_update_mispredict &&
                              !bpd_update_repair &&
                               bpd_ptr =/= deq_ptr &&
                               enq_ptr =/= WrapInc(bpd_ptr, num_entries) &&
                              !io.brupdate.b2.mispredict &&
                              !io.redirect.valid && !RegNext(io.redirect.valid))
  val do_mispredict_update = bpd_update_mispredict
  val do_repair_update     = bpd_update_repair

  when (RegNext(do_commit_update || do_repair_update || do_mispredict_update)) {
    val cfi_idx = bpd_entry.cfi_idx.bits
    val valid_repair = bpd_pc =/= bpd_repair_pc

    io.bpdupdate.valid := (!first_empty &&
                           (bpd_entry.cfi_idx.valid || bpd_entry.br_mask =/= 0.U) &&
                           !(RegNext(do_repair_update) && !valid_repair))
    io.bpdupdate.bits.is_mispredict_update := RegNext(do_mispredict_update)
    io.bpdupdate.bits.is_repair_update     := RegNext(do_repair_update)
    io.bpdupdate.bits.pc      := bpd_pc
    io.bpdupdate.bits.btb_mispredicts := 0.U
    io.bpdupdate.bits.br_mask := Mux(bpd_entry.cfi_idx.valid,
      MaskLower(UIntToOH(cfi_idx)) & bpd_entry.br_mask, bpd_entry.br_mask)
    io.bpdupdate.bits.cfi_idx := bpd_entry.cfi_idx
    io.bpdupdate.bits.cfi_mispredicted := bpd_entry.cfi_mispredicted
    io.bpdupdate.bits.cfi_taken  := bpd_entry.cfi_taken
    io.bpdupdate.bits.target     := bpd_target
    io.bpdupdate.bits.cfi_is_br  := bpd_entry.br_mask(cfi_idx)
    io.bpdupdate.bits.cfi_is_jal := bpd_entry.cfi_type === CFI_JAL || bpd_entry.cfi_type === CFI_JALR
    io.bpdupdate.bits.ghist      := bpd_ghist
    io.bpdupdate.bits.lhist      := bpd_lhist
    io.bpdupdate.bits.meta       := bpd_meta

    first_empty := false.B
  }

  when (do_commit_update) {
    bpd_ptr := WrapInc(bpd_ptr, num_entries)
  }

  io.enq.ready := RegNext(!full || do_commit_update)

  val redirect_idx = io.redirect.bits
  val redirect_entry = ram(redirect_idx)
  val redirect_new_entry = WireInit(redirect_entry)

  when (io.redirect.valid) {
    enq_ptr    := WrapInc(io.redirect.bits, num_entries)

    when (io.brupdate.b2.mispredict) {
    val new_cfi_idx = (io.brupdate.b2.uop.pc_lob ^
      Mux(redirect_entry.start_bank === 1.U, 1.U << log2Ceil(bankBytes), 0.U))(log2Ceil(fetchWidth), 1)
      redirect_new_entry.cfi_idx.valid    := true.B
      redirect_new_entry.cfi_idx.bits     := new_cfi_idx
      redirect_new_entry.cfi_mispredicted := true.B
      redirect_new_entry.cfi_taken        := io.brupdate.b2.taken
      redirect_new_entry.cfi_is_call      := redirect_entry.cfi_is_call && redirect_entry.cfi_idx.bits === new_cfi_idx
      redirect_new_entry.cfi_is_ret       := redirect_entry.cfi_is_ret  && redirect_entry.cfi_idx.bits === new_cfi_idx
    }

    ras_update     := true.B
    ras_update_pc  := redirect_entry.ras_top
    ras_update_idx := redirect_entry.ras_idx

  } .elsewhen (RegNext(io.redirect.valid)) {
    prev_entry := RegNext(redirect_new_entry)
    prev_ghist := bpd_ghist
    prev_pc    := bpd_pc

    ram(RegNext(io.redirect.bits)) := RegNext(redirect_new_entry)
  }

  //-------------------------------------------------------------
  // **** Core Read PCs ****
  //-------------------------------------------------------------

  for (i <- 0 until 3) {
    val idx = Mux(reset.asBool, 0.U(log2Ceil(ftqSz).W), io.arb_ftq_reqs(i))
    val is_enq = (idx === enq_ptr) && io.enq.fire
    val get_entry = ram(idx)

    io.rrd_ftq_resps(i).entry     := RegNext(get_entry)
    if (i == 0) {
      io.rrd_ftq_resps(i).ghist   := ghist(1).read(idx, true.B)
    } else {
      io.rrd_ftq_resps(i).ghist   := DontCare
    }
    io.rrd_ftq_resps(i).pc        := RegNext(Mux(is_enq, io.enq.bits.pc, pcs(idx)))
    io.rrd_ftq_resps(i).valid     := RegNext(idx =/= enq_ptr || is_enq)
  }

  io.com_pc := RegNext(pcs(Mux(io.deq.valid, io.deq.bits, deq_ptr)))

  for (w <- 0 until coreWidth) {
    io.debug_fetch_pc(w) := RegNext(pcs(io.debug_ftq_idx(w)))
  }
}
