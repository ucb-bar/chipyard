//******************************************************************************
// Copyright (c) 2012 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISCV Processor Datapath: Rename Logic
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// Supports 1-cycle and 2-cycle latencies. (aka, passthrough versus registers between ren1 and ren2).
//    - ren1: read the map tables and allocate a new physical register from the freelist.
//    - ren2: read the busy table for the physical operands.
//
// Ren1 data is provided as an output to be fed directly into the ROB.

package boom.v4.exu

import chisel3._
import chisel3.util._

import freechips.rocketchip.util._
import org.chipsalliance.cde.config.Parameters
import boom.v4.common._
import boom.v4.util._

abstract class AbstractRenameStage(
  plWidth: Int,
  numWbPorts: Int)
  (implicit p: Parameters) extends BoomModule
{
  val io = IO(new Bundle {
    val ren_stalls = Output(Vec(plWidth, Bool()))

    val kill = Input(Bool())

    val dec_fire  = Input(Vec(plWidth, Bool())) // will commit state updates
    val dec_uops  = Input(Vec(plWidth, new MicroOp()))

    // physical specifiers available AND busy/ready status available.
    val ren2_mask = Vec(plWidth, Output(Bool())) // mask of valid instructions
    val ren2_uops = Vec(plWidth, Output(new MicroOp()))

    // branch resolution (execute)
    val brupdate = Input(new BrUpdateInfo())

    val dis_fire  = Input(Vec(coreWidth, Bool()))
    val dis_ready = Input(Bool())

    // wakeup ports
    val wakeups = Flipped(Vec(numWbPorts, Valid(new Wakeup)))
    val child_rebusys = Input(UInt(aluWidth.W))

    // commit stage
    val com_valids = Input(Vec(plWidth, Bool()))
    val com_uops = Input(Vec(plWidth, new MicroOp()))
    val rollback = Input(Bool())

    val debug_rob_empty = Input(Bool())
  })

  def BypassAllocations(uop: MicroOp, older_uops: Seq[MicroOp], alloc_reqs: Seq[Bool]): MicroOp = { uop }

  //-------------------------------------------------------------
  // Pipeline State & Wires

  // Stage 1
  val ren1_fire       = Wire(Vec(plWidth, Bool()))
  val ren1_uops       = Wire(Vec(plWidth, new MicroOp))


  // Stage 2
  val ren2_fire       = io.dis_fire
  val ren2_ready      = io.dis_ready
  val ren2_valids     = Wire(Vec(plWidth, Bool()))
  val ren2_uops       = Wire(Vec(plWidth, new MicroOp))
  val ren2_alloc_reqs = Wire(Vec(plWidth, Bool()))
  val ren2_alloc_fire = (ren2_fire zip ren2_alloc_reqs).map({case (f, r) => f && r})
  val ren2_br_tags    = Wire(Vec(plWidth+1, Valid(UInt(brTagSz.W))))

  //-------------------------------------------------------------
  // pipeline registers

  for (w <- 0 until plWidth) {
    ren1_fire(w)          := io.dec_fire(w)
    ren1_uops(w)          := io.dec_uops(w)
  }

  ren2_br_tags(0).valid := false.B
  ren2_br_tags(0).bits  := DontCare
  for (w <- 0 until plWidth) {
    val r_valid  = RegInit(false.B)
    val r_uop    = Reg(new MicroOp)
    val next_uop = Wire(new MicroOp)

    next_uop := r_uop

    when (io.kill) {
      r_valid := false.B
    } .elsewhen (ren2_ready) {
      r_valid := ren1_fire(w)
      next_uop := ren1_uops(w)
    } .otherwise {
      r_valid := r_valid && !ren2_fire(w) // clear bit if uop gets dispatched
      next_uop := r_uop
    }

    assert(!(r_valid && r_uop.lrs1_rtype === RT_FIX && r_uop.lrs1 === 0.U))
    assert(!(r_valid && r_uop.lrs2_rtype === RT_FIX && r_uop.lrs2 === 0.U))

    r_uop := GetNewUopAndBrMask(BypassAllocations(next_uop, ren2_uops, ren2_alloc_fire), io.brupdate)

    ren2_valids(w) := r_valid
    ren2_uops(w)   := r_uop

    ren2_br_tags(w+1).valid := ren2_fire(w) && ren2_uops(w).allocate_brtag
    ren2_br_tags(w+1).bits  := ren2_uops(w).br_tag
  }

  //-------------------------------------------------------------
  // Outputs

  io.ren2_mask := ren2_valids


}


/**
 * Rename stage that connets the map table, free list, and busy table.
 * Can be used in both the FP pipeline and the normal execute pipeline.
 *
 * @param plWidth pipeline width
 * @param numWbPorts number of int writeback ports
 * @param numWbPorts number of FP writeback ports
 */
class RenameStage(
  plWidth: Int,
  numPhysRegs: Int,
  numWbPorts: Int,
  float: Boolean)
(implicit p: Parameters) extends AbstractRenameStage(plWidth, numWbPorts)(p)
{
  val int = !float
  val pregSz = log2Ceil(numPhysRegs)
  val rtype = if (float) RT_FLT else RT_FIX

  //-------------------------------------------------------------
  // Helper Functions

  override def BypassAllocations(uop: MicroOp, older_uops: Seq[MicroOp], alloc_reqs: Seq[Bool]): MicroOp = {
    if (older_uops.size == 0) {
      uop
    } else {
      val bypassed_uop = Wire(new MicroOp)
      bypassed_uop := uop

      val bypass_hits_rs1 = (older_uops zip alloc_reqs) map { case (r,a) => a && r.ldst === uop.lrs1 }
      val bypass_hits_rs2 = (older_uops zip alloc_reqs) map { case (r,a) => a && r.ldst === uop.lrs2 }
      val bypass_hits_rs3 = (older_uops zip alloc_reqs) map { case (r,a) => a && r.ldst === uop.lrs3 }
      val bypass_hits_dst = (older_uops zip alloc_reqs) map { case (r,a) => a && r.ldst === uop.ldst }

      val bypass_sel_rs1 = PriorityEncoderOH(bypass_hits_rs1.reverse).reverse
      val bypass_sel_rs2 = PriorityEncoderOH(bypass_hits_rs2.reverse).reverse
      val bypass_sel_rs3 = PriorityEncoderOH(bypass_hits_rs3.reverse).reverse
      val bypass_sel_dst = PriorityEncoderOH(bypass_hits_dst.reverse).reverse

      val do_bypass_rs1 = bypass_hits_rs1.reduce(_||_)
      val do_bypass_rs2 = bypass_hits_rs2.reduce(_||_)
      val do_bypass_rs3 = bypass_hits_rs3.reduce(_||_)
      val do_bypass_dst = bypass_hits_dst.reduce(_||_)

      val bypass_pdsts = older_uops.map(_.pdst)

      when (do_bypass_rs1) { bypassed_uop.prs1       := Mux1H(bypass_sel_rs1, bypass_pdsts) }
      when (do_bypass_rs2) { bypassed_uop.prs2       := Mux1H(bypass_sel_rs2, bypass_pdsts) }
      when (do_bypass_rs3) { bypassed_uop.prs3       := Mux1H(bypass_sel_rs3, bypass_pdsts) }
      when (do_bypass_dst) { bypassed_uop.stale_pdst := Mux1H(bypass_sel_dst, bypass_pdsts) }

      bypassed_uop.prs1_busy := uop.prs1_busy || do_bypass_rs1
      bypassed_uop.prs2_busy := uop.prs2_busy || do_bypass_rs2
      bypassed_uop.prs3_busy := uop.prs3_busy || do_bypass_rs3

      if (int) {
        bypassed_uop.prs3      := DontCare
        bypassed_uop.prs3_busy := false.B
      }

      bypassed_uop
    }
  }

  //-------------------------------------------------------------
  // Rename Structures

  val maptable = Module(new RenameMapTable(
    plWidth,
    32,
    numPhysRegs,
    false,
    float))
  val freelist = Module(
    if (
      (enableColumnALUWrites && int) ||
      (enableBankedFPFreelist && float)
    ) {
      new BankedRenameFreeList(
        plWidth,
        numPhysRegs
      )
    } else {
      new RenameFreeList(
        plWidth,
        plWidth,
        numPhysRegs,
        false
      )
    }
  )
  val busytable = Module(new RenameBusyTable(
    plWidth,
    numPhysRegs,
    numWbPorts,
    float))





  // Commit/Rollback
  val com_valids      = Wire(Vec(plWidth, Bool()))

  for (w <- 0 until plWidth) {
    ren2_alloc_reqs(w)    := ren2_uops(w).dst_rtype === rtype && ren2_valids(w)

    com_valids(w)         := io.com_uops(w).dst_rtype === rtype && io.com_valids(w)
  }

  //-------------------------------------------------------------
  // Rename Table

  // Maptable inputs.
  val map_reqs   = Wire(Vec(plWidth, new MapReq(lregSz)))
  val remap_reqs = Wire(Vec(plWidth, new RemapReq(lregSz, pregSz)))
  val com_remap_reqs = Wire(Vec(plWidth, new RemapReq(lregSz, pregSz)))
  // Generate maptable requests.
  for ((((ren1,ren2),com),w) <- (ren1_uops zip ren2_uops zip io.com_uops).zipWithIndex) {
    map_reqs(w).lrs1 := ren1.lrs1
    map_reqs(w).lrs2 := ren1.lrs2
    map_reqs(w).lrs3 := ren1.lrs3
    map_reqs(w).ldst := ren1.ldst

    remap_reqs(w).valid := ren2_alloc_fire(w)
    remap_reqs(w).ldst := ren2.ldst
    remap_reqs(w).pdst := ren2.pdst

    com_remap_reqs(w).valid := com_valids(w)
    com_remap_reqs(w).ldst := io.com_uops(w).ldst
    com_remap_reqs(w).pdst := io.com_uops(w).pdst
  }

  // Hook up inputs.
  maptable.io.map_reqs    := map_reqs
  maptable.io.remap_reqs  := remap_reqs
  maptable.io.ren_br_tags := ren2_br_tags
  maptable.io.brupdate    := io.brupdate
  maptable.io.rollback    := io.rollback
  maptable.io.com_remap_reqs := com_remap_reqs

  // Maptable outputs.
  for ((uop, w) <- ren1_uops.zipWithIndex) {
    val mappings = maptable.io.map_resps(w)

    uop.prs1       := mappings.prs1
    uop.prs2       := mappings.prs2
    uop.prs3       := mappings.prs3 // only FP has 3rd operand
    uop.stale_pdst := mappings.stale_pdst
  }



  //-------------------------------------------------------------
  // Free List

  // Freelist inputs.
  freelist.io.initial_allocation := Cat(~(0.U((numPhysRegs-32).W)), 0.U(32.W))
  freelist.io.reqs := ren2_alloc_fire
  for (w <- 0 until plWidth) {
    freelist.io.despec(w).valid       := com_valids(w)
    freelist.io.despec(w).bits        := io.com_uops(w).pdst
    freelist.io.dealloc(w).valid      := com_valids(w)
    freelist.io.dealloc(w).bits       := io.com_uops(w).stale_pdst
  }
  freelist.io.ren_br_tags := ren2_br_tags
  freelist.io.brupdate := io.brupdate
  freelist.io.rollback := io.rollback

  // Freelist outputs.
  for ((uop, w) <- ren2_uops.zipWithIndex) {
    val preg = freelist.io.alloc_pregs(w).bits
    uop.pdst := preg
    uop.dis_col_sel := (if (coreWidth > 1) UIntToOH(preg(log2Ceil(coreWidth)-1,0)) else 0.U(1.W))
  }
  assert (!RegNext(io.rollback) || PopCount(freelist.io.debug_freelist) === (numPhysRegs - 32).U,
    "[freelist] Leaking physical registers.")

  //-------------------------------------------------------------
  // Busy Table

  busytable.io.ren_uops := ren2_uops  // expects pdst to be set up.
  busytable.io.rebusy_reqs := ren2_alloc_fire
  busytable.io.wakeups := io.wakeups
  busytable.io.child_rebusys := io.child_rebusys


  assert (!(io.wakeups.map(x => x.valid && x.bits.uop.dst_rtype =/= rtype).reduce(_||_)),
   "[rename] Wakeup has wrong rtype.")

  for ((uop, w) <- ren2_uops.zipWithIndex) {
    val busy = busytable.io.busy_resps(w)

    uop.prs1_busy := uop.lrs1_rtype === rtype && busy.prs1_busy
    uop.prs2_busy := uop.lrs2_rtype === rtype && busy.prs2_busy
    uop.prs3_busy := uop.frs3_en && busy.prs3_busy

    val valid = ren2_valids(w)
    assert (!(valid && busy.prs1_busy && rtype === RT_FIX && uop.lrs1_rtype === RT_FIX && uop.lrs1 === 0.U), "[rename] x0 is busy??")
    assert (!(valid && busy.prs2_busy && rtype === RT_FIX && uop.lrs2_rtype === RT_FIX && uop.lrs2 === 0.U), "[rename] x0 is busy??")
  }

  //-------------------------------------------------------------
  // Outputs

  for (w <- 0 until plWidth) {
    val can_allocate = freelist.io.alloc_pregs(w).valid

    // Push back against Decode stage if Rename1 can't proceed.
    io.ren_stalls(w) := (ren2_uops(w).dst_rtype === rtype) && !can_allocate

    val bypassed_uop = Wire(new MicroOp)
    bypassed_uop := BypassAllocations(ren2_uops(w), ren2_uops.take(w), ren2_alloc_reqs.take(w))

    io.ren2_uops(w) := GetNewUopAndBrMask(bypassed_uop, io.brupdate)
  }

}

class PredRenameStage(
  plWidth: Int,
  numWbPorts: Int)
  (implicit p: Parameters) extends AbstractRenameStage(plWidth, numWbPorts)(p)
{

  override def BypassAllocations(uop: MicroOp, older_uops: Seq[MicroOp], alloc_reqs: Seq[Bool]): MicroOp = {
    if (older_uops.size == 0) {
      uop
    } else {
      val bypassed_uop = Wire(new MicroOp)
      bypassed_uop := uop


      val bypass_sel = PriorityEncoderOH(alloc_reqs.reverse).reverse

      val do_bypass = alloc_reqs.reduce(_||_)

      val bypass_pdsts = older_uops.map(_.ftq_idx)

      when (do_bypass) { bypassed_uop.ppred       := Mux1H(bypass_sel, bypass_pdsts) }

      bypassed_uop.ppred_busy := (uop.ppred_busy || do_bypass) && uop.is_sfb_shadow

      bypassed_uop
    }
  }

  val ren1_current_ftq_idx = Reg(UInt(log2Ceil(ftqSz).W))
  var next_ftq_idx = ren1_current_ftq_idx
  for (w <- 0 until plWidth) {
    ren1_uops(w).ppred := next_ftq_idx
    when (ren1_uops(w).is_sfb_br) {
      ren1_uops(w).pdst := ren1_uops(w).ftq_idx
    }
    next_ftq_idx = Mux(ren1_uops(w).is_sfb_br && ren1_fire(w), ren1_uops(w).ftq_idx, next_ftq_idx)
  }
  ren1_current_ftq_idx := next_ftq_idx

  val busy_table = RegInit(VecInit(0.U(ftqSz.W).asBools))
  val to_busy = WireInit(VecInit(0.U(ftqSz.W).asBools))
  val unbusy = WireInit(VecInit(0.U(ftqSz.W).asBools))

  for (w <- 0 until plWidth) {
    ren2_alloc_reqs(w) := ren2_uops(w).is_sfb_br && ren2_valids(w)

    io.ren2_uops(w) := BypassAllocations(ren2_uops(w), ren2_uops.take(w), ren2_alloc_reqs.take(w))
    io.ren_stalls(w) := false.B

    when (ren2_alloc_reqs(w) && ren2_fire(w)) {
      to_busy(ren2_uops(w).ftq_idx) := true.B
    }
    ren2_uops(w).ppred_busy := ren2_uops(w).is_sfb_shadow && busy_table(ren2_uops(w).ppred) && !unbusy(ren2_uops(w).ppred)
  }

  for (w <- 0 until numWbPorts) {
    when (io.wakeups(w).valid) {
      unbusy(io.wakeups(w).bits.uop.pdst) := true.B
    }
  }


  busy_table := ((busy_table.asUInt | to_busy.asUInt) & ~unbusy.asUInt).asBools
}

class ImmRenameStage(plWidth: Int, numWbPorts: Int)(implicit p: Parameters) extends AbstractRenameStage(plWidth, numWbPorts)(p)
{

  val freelist = Module(new RenameFreeList(
    plWidth,
    numWbPorts,
    numImmPhysRegs,
    true
  ))

  for (w <- 0 until plWidth) {
    val imm = ImmGen(ren2_uops(w).imm_packed, ren2_uops(w).imm_sel)
    val imm_hi = imm >> (immPregSz-1)
    val imm_lo = imm(immPregSz-1, 0)
    val short_imm = imm_hi === 0.U || ~imm_hi === 0.U

    ren2_alloc_reqs(w) := ren2_uops(w).imm_rename

    assert(!ren2_alloc_fire(w) || ren2_uops(w).iq_type(IQ_ALU) || ren2_uops(w).iq_type(IQ_MEM) || ren2_uops(w).iq_type(IQ_UNQ))


    val can_allocate = freelist.io.alloc_pregs(w).valid
    // Push back against Decode stage if Rename1 can't proceed.
    io.ren_stalls(w) := ren2_uops(w).imm_rename && !can_allocate

    io.ren2_uops(w) := GetNewUopAndBrMask(ren2_uops(w), io.brupdate)
    when (ren2_uops(w).imm_rename) {
      io.ren2_uops(w).pimm := freelist.io.alloc_pregs(w).bits
    }

  }

  freelist.io.initial_allocation := ~(0.U(numImmPhysRegs.W))
  freelist.io.reqs := ren2_alloc_fire
  for (w <- 0 until numWbPorts) {
    freelist.io.despec(w).valid       := false.B
    freelist.io.despec(w).bits        := DontCare

    freelist.io.dealloc(w).valid := io.wakeups(w).valid
    freelist.io.dealloc(w).bits  := io.wakeups(w).bits.uop.pimm
  }
  freelist.io.ren_br_tags := ren2_br_tags
  freelist.io.brupdate    := io.brupdate
  freelist.io.rollback    := io.rollback
  assert (!RegNext(io.rollback) || PopCount(freelist.io.debug_freelist) === (numImmPhysRegs).U,
    "[freelist] Leaking physical registers.")
}
