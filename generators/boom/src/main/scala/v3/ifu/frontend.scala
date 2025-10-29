//******************************************************************************
// Copyright (c) 2017 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Frontend
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._

import boom.v3.common._
import boom.v3.exu.{CommitExceptionSignals, BranchDecode, BrUpdateInfo, BranchDecodeSignals}
import boom.v3.util._


class FrontendResp(implicit p: Parameters) extends BoomBundle()(p) {
  val pc = UInt(vaddrBitsExtended.W)  // ID stage PC
  val data = UInt((fetchWidth * coreInstBits).W)
  val mask = UInt(fetchWidth.W)
  val xcpt = new FrontendExceptions
  val ghist = new GlobalHistory

  // fsrc provides the prediction FROM a branch in this packet
  // tsrc provides the prediction TO this packet
  val fsrc = UInt(BSRC_SZ.W)
  val tsrc = UInt(BSRC_SZ.W)
}

class GlobalHistory(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomFrontendParameters
{
  // For the dual banked case, each bank ignores the contribution of the
  // last bank to the history. Thus we have to track the most recent update to the
  // history in that case
  val old_history = UInt(globalHistoryLength.W)

  val current_saw_branch_not_taken = Bool()

  val new_saw_branch_not_taken = Bool()
  val new_saw_branch_taken     = Bool()

  val ras_idx = UInt(log2Ceil(nRasEntries).W)

  def histories(bank: Int) = {
    if (nBanks == 1) {
      old_history
    } else {
      require(nBanks == 2)
      if (bank == 0) {
        old_history
      } else {
        Mux(new_saw_branch_taken                            , old_history << 1 | 1.U,
        Mux(new_saw_branch_not_taken                        , old_history << 1,
                                                              old_history))
      }
    }
  }

  def ===(other: GlobalHistory): Bool = {
    ((old_history === other.old_history) &&
     (new_saw_branch_not_taken === other.new_saw_branch_not_taken) &&
     (new_saw_branch_taken === other.new_saw_branch_taken)
    )
  }
  def =/=(other: GlobalHistory): Bool = !(this === other)

  def update(branches: UInt, cfi_taken: Bool, cfi_is_br: Bool, cfi_idx: UInt,
    cfi_valid: Bool, addr: UInt,
    cfi_is_call: Bool, cfi_is_ret: Bool): GlobalHistory = {
    val cfi_idx_fixed = cfi_idx(log2Ceil(fetchWidth)-1,0)
    val cfi_idx_oh = UIntToOH(cfi_idx_fixed)
    val new_history = Wire(new GlobalHistory)

    val not_taken_branches = branches & Mux(cfi_valid,
                                            MaskLower(cfi_idx_oh) & ~Mux(cfi_is_br && cfi_taken, cfi_idx_oh, 0.U(fetchWidth.W)),
                                            ~(0.U(fetchWidth.W)))

    if (nBanks == 1) {
      // In the single bank case every bank sees the history including the previous bank
      new_history := DontCare
      new_history.current_saw_branch_not_taken := false.B
      val saw_not_taken_branch = not_taken_branches =/= 0.U || current_saw_branch_not_taken
      new_history.old_history := Mux(cfi_is_br && cfi_taken && cfi_valid   , histories(0) << 1 | 1.U,
                                 Mux(saw_not_taken_branch                  , histories(0) << 1,
                                                                             histories(0)))
    } else {
      // In the two bank case every bank ignore the history added by the previous bank
      val base = histories(1)
      val cfi_in_bank_0 = cfi_valid && cfi_taken && cfi_idx_fixed < bankWidth.U
      val ignore_second_bank = cfi_in_bank_0 || mayNotBeDualBanked(addr)

      val first_bank_saw_not_taken = not_taken_branches(bankWidth-1,0) =/= 0.U || current_saw_branch_not_taken
      new_history.current_saw_branch_not_taken := false.B
      when (ignore_second_bank) {
        new_history.old_history := histories(1)
        new_history.new_saw_branch_not_taken := first_bank_saw_not_taken
        new_history.new_saw_branch_taken     := cfi_is_br && cfi_in_bank_0
      } .otherwise {
        new_history.old_history := Mux(cfi_is_br && cfi_in_bank_0                             , histories(1) << 1 | 1.U,
                                   Mux(first_bank_saw_not_taken                               , histories(1) << 1,
                                                                                                histories(1)))

        new_history.new_saw_branch_not_taken := not_taken_branches(fetchWidth-1,bankWidth) =/= 0.U
        new_history.new_saw_branch_taken     := cfi_valid && cfi_taken && cfi_is_br && !cfi_in_bank_0

      }
    }
    new_history.ras_idx := Mux(cfi_valid && cfi_is_call, WrapInc(ras_idx, nRasEntries),
                           Mux(cfi_valid && cfi_is_ret , WrapDec(ras_idx, nRasEntries), ras_idx))
    new_history
  }

}

/**
 * Parameters to manage a L1 Banked ICache
 */
trait HasBoomFrontendParameters extends HasL1ICacheParameters
{
  // How many banks does the ICache use?
  val nBanks = if (cacheParams.fetchBytes <= 8) 1 else 2
  // How many bytes wide is a bank?
  val bankBytes = fetchBytes/nBanks

  val bankWidth = fetchWidth/nBanks

  require(nBanks == 1 || nBanks == 2)



  // How many "chunks"/interleavings make up a cache line?
  val numChunks = cacheParams.blockBytes / bankBytes

  // Which bank is the address pointing to?
  def bank(addr: UInt) = if (nBanks == 2) addr(log2Ceil(bankBytes)) else 0.U
  def isLastBankInBlock(addr: UInt) = {
    (nBanks == 2).B && addr(blockOffBits-1, log2Ceil(bankBytes)) === (numChunks-1).U
  }
  def mayNotBeDualBanked(addr: UInt) = {
    require(nBanks == 2)
    isLastBankInBlock(addr)
  }

  def blockAlign(addr: UInt) = ~(~addr | (cacheParams.blockBytes-1).U)
  def bankAlign(addr: UInt) = ~(~addr | (bankBytes-1).U)

  def fetchIdx(addr: UInt) = addr >> log2Ceil(fetchBytes)

  def nextBank(addr: UInt) = bankAlign(addr) + bankBytes.U
  def nextFetch(addr: UInt) = {
    if (nBanks == 1) {
      bankAlign(addr) + bankBytes.U
    } else {
      require(nBanks == 2)
      bankAlign(addr) + Mux(mayNotBeDualBanked(addr), bankBytes.U, fetchBytes.U)
    }
  }

  def fetchMask(addr: UInt) = {
    val idx = addr.extract(log2Ceil(fetchWidth)+log2Ceil(coreInstBytes)-1, log2Ceil(coreInstBytes))
    if (nBanks == 1) {
      ((1 << fetchWidth)-1).U << idx
    } else {
      val shamt = idx.extract(log2Ceil(fetchWidth)-2, 0)
      val end_mask = Mux(mayNotBeDualBanked(addr), Fill(fetchWidth/2, 1.U), Fill(fetchWidth, 1.U))
      ((1 << fetchWidth)-1).U << shamt & end_mask
    }
  }

  def bankMask(addr: UInt) = {
    val idx = addr.extract(log2Ceil(fetchWidth)+log2Ceil(coreInstBytes)-1, log2Ceil(coreInstBytes))
    if (nBanks == 1) {
      1.U(1.W)
    } else {
      Mux(mayNotBeDualBanked(addr), 1.U(2.W), 3.U(2.W))
    }
  }
}



/**
 * Bundle passed into the FetchBuffer and used to combine multiple
 * relevant signals together.
 */
class FetchBundle(implicit p: Parameters) extends BoomBundle
  with HasBoomFrontendParameters
{
  val pc            = UInt(vaddrBitsExtended.W)
  val next_pc       = UInt(vaddrBitsExtended.W)
  val edge_inst     = Vec(nBanks, Bool()) // True if 1st instruction in this bundle is pc - 2
  val insts         = Vec(fetchWidth, Bits(32.W))
  val exp_insts     = Vec(fetchWidth, Bits(32.W))

  // Information for sfb folding
  // NOTE: This IS NOT equivalent to uop.pc_lob, that gets calculated in the FB
  val sfbs                 = Vec(fetchWidth, Bool())
  val sfb_masks            = Vec(fetchWidth, UInt((2*fetchWidth).W))
  val sfb_dests            = Vec(fetchWidth, UInt((1+log2Ceil(fetchBytes)).W))
  val shadowable_mask      = Vec(fetchWidth, Bool())
  val shadowed_mask        = Vec(fetchWidth, Bool())

  val cfi_idx       = Valid(UInt(log2Ceil(fetchWidth).W))
  val cfi_type      = UInt(CFI_SZ.W)
  val cfi_is_call   = Bool()
  val cfi_is_ret    = Bool()
  val cfi_npc_plus4 = Bool()

  val ras_top       = UInt(vaddrBitsExtended.W)

  val ftq_idx       = UInt(log2Ceil(ftqSz).W)
  val mask          = UInt(fetchWidth.W) // mark which words are valid instructions

  val br_mask       = UInt(fetchWidth.W)

  val ghist         = new GlobalHistory
  val lhist         = Vec(nBanks, UInt(localHistoryLength.W))

  val xcpt_pf_if    = Bool() // I-TLB miss (instruction fetch fault).
  val xcpt_ae_if    = Bool() // Access exception.

  val bp_debug_if_oh= Vec(fetchWidth, Bool())
  val bp_xcpt_if_oh = Vec(fetchWidth, Bool())

  val end_half      = Valid(UInt(16.W))


  val bpd_meta      = Vec(nBanks, UInt())

  // Source of the prediction from this bundle
  val fsrc    = UInt(BSRC_SZ.W)
  // Source of the prediction to this bundle
  val tsrc    = UInt(BSRC_SZ.W)
}



/**
 * IO for the BOOM Frontend to/from the CPU
 */
class BoomFrontendIO(implicit p: Parameters) extends BoomBundle
{
  // Give the backend a packet of instructions.
  val fetchpacket       = Flipped(new DecoupledIO(new FetchBufferResp))

  // 1 for xcpt/jalr/auipc/flush
  val get_pc            = Flipped(Vec(2, new GetPCFromFtqIO()))
  val debug_ftq_idx     = Output(Vec(coreWidth, UInt(log2Ceil(ftqSz).W)))
  val debug_fetch_pc    = Input(Vec(coreWidth, UInt(vaddrBitsExtended.W)))

  // Breakpoint info
  val status            = Output(new MStatus)
  val bp                = Output(Vec(nBreakpoints, new BP))
  val mcontext          = Output(UInt(coreParams.mcontextWidth.W))
  val scontext          = Output(UInt(coreParams.scontextWidth.W))

  val sfence = Valid(new SFenceReq)

  val brupdate          = Output(new BrUpdateInfo)

  // Redirects change the PC
  val redirect_flush   = Output(Bool()) // Flush and hang the frontend?
  val redirect_val     = Output(Bool()) // Redirect the frontend?
  val redirect_pc      = Output(UInt()) // Where do we redirect to?
  val redirect_ftq_idx = Output(UInt()) // Which ftq entry should we reset to?
  val redirect_ghist   = Output(new GlobalHistory) // What are we setting as the global history?

  val commit = Valid(UInt(ftqSz.W))

  val flush_icache = Output(Bool())

  val perf = Input(new FrontendPerfEvents)
}

/**
 * Top level Frontend class
 *
 * @param icacheParams parameters for the icache
 * @param hartid id for the hardware thread of the core
 */
class BoomFrontend(val icacheParams: ICacheParams, staticIdForMetadataUseOnly: Int)(implicit p: Parameters) extends LazyModule
{
  lazy val module = new BoomFrontendModule(this)
  val icache = LazyModule(new boom.v3.ifu.ICache(icacheParams, staticIdForMetadataUseOnly))
  val masterNode = icache.masterNode
  val resetVectorSinkNode = BundleBridgeSink[UInt](Some(() =>
    UInt(masterNode.edges.out.head.bundle.addressBits.W)))
}

/**
 * Bundle wrapping the IO for the Frontend as a whole
 *
 * @param outer top level Frontend class
 */
class BoomFrontendBundle(val outer: BoomFrontend) extends CoreBundle()(outer.p)
{
  val cpu = Flipped(new BoomFrontendIO())
  val ptw = new TLBPTWIO()
}

/**
 * Main Frontend module that connects the icache, TLB, fetch controller,
 * and branch prediction pipeline together.
 *
 * @param outer top level Frontend class
 */
class BoomFrontendModule(outer: BoomFrontend) extends LazyModuleImp(outer)
  with HasBoomCoreParameters
  with HasBoomFrontendParameters
{
  val io = IO(new BoomFrontendBundle(outer))
  val io_reset_vector = outer.resetVectorSinkNode.bundle
  implicit val edge = outer.masterNode.edges.out(0)
  require(fetchWidth*coreInstBytes == outer.icacheParams.fetchBytes)

  val bpd = Module(new BranchPredictor)
  bpd.io.f3_fire := false.B
  val ras = Module(new BoomRAS)

  val icache = outer.icache.module
  icache.io.invalidate := io.cpu.flush_icache
  val tlb = Module(new TLB(true, log2Ceil(fetchBytes), TLBConfig(nTLBSets, nTLBWays)))
  io.ptw <> tlb.io.ptw
  io.cpu.perf.tlbMiss := io.ptw.req.fire
  io.cpu.perf.acquire := icache.io.perf.acquire

  // --------------------------------------------------------
  // **** NextPC Select (F0) ****
  //      Send request to ICache
  // --------------------------------------------------------

  val s0_vpc       = WireInit(0.U(vaddrBitsExtended.W))
  val s0_ghist     = WireInit((0.U).asTypeOf(new GlobalHistory))
  val s0_tsrc      = WireInit(0.U(BSRC_SZ.W))
  val s0_valid     = WireInit(false.B)
  val s0_is_replay = WireInit(false.B)
  val s0_is_sfence = WireInit(false.B)
  val s0_replay_resp = Wire(new TLBResp(log2Ceil(fetchBytes)))
  val s0_replay_bpd_resp = Wire(new BranchPredictionBundle)
  val s0_replay_ppc  = Wire(UInt())
  val s0_s1_use_f3_bpd_resp = WireInit(false.B)




  when (RegNext(reset.asBool) && !reset.asBool) {
    s0_valid   := true.B
    s0_vpc     := io_reset_vector
    s0_ghist   := (0.U).asTypeOf(new GlobalHistory)
    s0_tsrc    := BSRC_C
  }

  icache.io.req.valid     := s0_valid
  icache.io.req.bits.addr := s0_vpc

  bpd.io.f0_req.valid      := s0_valid
  bpd.io.f0_req.bits.pc    := s0_vpc
  bpd.io.f0_req.bits.ghist := s0_ghist

  // --------------------------------------------------------
  // **** ICache Access (F1) ****
  //      Translate VPC
  // --------------------------------------------------------
  val s1_vpc       = RegNext(s0_vpc)
  val s1_valid     = RegNext(s0_valid, false.B)
  val s1_ghist     = RegNext(s0_ghist)
  val s1_is_replay = RegNext(s0_is_replay)
  val s1_is_sfence = RegNext(s0_is_sfence)
  val f1_clear     = WireInit(false.B)
  val s1_tsrc      = RegNext(s0_tsrc)
  tlb.io.req.valid      := (s1_valid && !s1_is_replay && !f1_clear) || s1_is_sfence
  tlb.io.req.bits.cmd   := DontCare
  tlb.io.req.bits.vaddr := s1_vpc
  tlb.io.req.bits.passthrough := false.B
  tlb.io.req.bits.size  := log2Ceil(coreInstBytes * fetchWidth).U
  tlb.io.req.bits.v     := io.ptw.status.v
  tlb.io.req.bits.prv   := io.ptw.status.prv
  tlb.io.sfence         := RegNext(io.cpu.sfence)
  tlb.io.kill           := false.B

  val s1_tlb_miss = !s1_is_replay && tlb.io.resp.miss
  val s1_tlb_resp = Mux(s1_is_replay, RegNext(s0_replay_resp), tlb.io.resp)
  val s1_ppc  = Mux(s1_is_replay, RegNext(s0_replay_ppc), tlb.io.resp.paddr)
  val s1_bpd_resp = bpd.io.resp.f1

  icache.io.s1_paddr := s1_ppc
  icache.io.s1_kill  := tlb.io.resp.miss || f1_clear

  val f1_mask = fetchMask(s1_vpc)
  val f1_redirects = (0 until fetchWidth) map { i =>
    s1_valid && f1_mask(i) && s1_bpd_resp.preds(i).predicted_pc.valid &&
    (s1_bpd_resp.preds(i).is_jal ||
      (s1_bpd_resp.preds(i).is_br && s1_bpd_resp.preds(i).taken))
  }
  val f1_redirect_idx = PriorityEncoder(f1_redirects)
  val f1_do_redirect = f1_redirects.reduce(_||_) && useBPD.B
  val f1_targs = s1_bpd_resp.preds.map(_.predicted_pc.bits)
  val f1_predicted_target = Mux(f1_do_redirect,
                                f1_targs(f1_redirect_idx),
                                nextFetch(s1_vpc))

  val f1_predicted_ghist = s1_ghist.update(
    s1_bpd_resp.preds.map(p => p.is_br && p.predicted_pc.valid).asUInt & f1_mask,
    s1_bpd_resp.preds(f1_redirect_idx).taken && f1_do_redirect,
    s1_bpd_resp.preds(f1_redirect_idx).is_br,
    f1_redirect_idx,
    f1_do_redirect,
    s1_vpc,
    false.B,
    false.B)

  when (s1_valid && !s1_tlb_miss) {
    // Stop fetching on fault
    s0_valid     := !(s1_tlb_resp.ae.inst || s1_tlb_resp.pf.inst)
    s0_tsrc      := BSRC_1
    s0_vpc       := f1_predicted_target
    s0_ghist     := f1_predicted_ghist
    s0_is_replay := false.B
  }

  // --------------------------------------------------------
  // **** ICache Response (F2) ****
  // --------------------------------------------------------

  val s2_valid = RegNext(s1_valid && !f1_clear, false.B)
  val s2_vpc   = RegNext(s1_vpc)
  val s2_ghist = Reg(new GlobalHistory)
  s2_ghist := s1_ghist
  val s2_ppc  = RegNext(s1_ppc)
  val s2_tsrc = RegNext(s1_tsrc) // tsrc provides the predictor component which provided the prediction TO this instruction
  val s2_fsrc = WireInit(BSRC_1) // fsrc provides the predictor component which provided the prediction FROM this instruction
  val f2_clear = WireInit(false.B)
  val s2_tlb_resp = RegNext(s1_tlb_resp)
  val s2_tlb_miss = RegNext(s1_tlb_miss)
  val s2_is_replay = RegNext(s1_is_replay) && s2_valid
  val s2_xcpt = s2_valid && (s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst) && !s2_is_replay
  val f3_ready = Wire(Bool())

  icache.io.s2_kill := s2_xcpt

  val f2_bpd_resp = bpd.io.resp.f2
  val f2_mask = fetchMask(s2_vpc)
  val f2_redirects = (0 until fetchWidth) map { i =>
    s2_valid && f2_mask(i) && f2_bpd_resp.preds(i).predicted_pc.valid &&
    (f2_bpd_resp.preds(i).is_jal ||
      (f2_bpd_resp.preds(i).is_br && f2_bpd_resp.preds(i).taken))
  }
  val f2_redirect_idx = PriorityEncoder(f2_redirects)
  val f2_targs = f2_bpd_resp.preds.map(_.predicted_pc.bits)
  val f2_do_redirect = f2_redirects.reduce(_||_) && useBPD.B
  val f2_predicted_target = Mux(f2_do_redirect,
                                f2_targs(f2_redirect_idx),
                                nextFetch(s2_vpc))
  val f2_predicted_ghist = s2_ghist.update(
    f2_bpd_resp.preds.map(p => p.is_br && p.predicted_pc.valid).asUInt & f2_mask,
    f2_bpd_resp.preds(f2_redirect_idx).taken && f2_do_redirect,
    f2_bpd_resp.preds(f2_redirect_idx).is_br,
    f2_redirect_idx,
    f2_do_redirect,
    s2_vpc,
    false.B,
    false.B)

  val f2_correct_f1_ghist = s1_ghist =/= f2_predicted_ghist && enableGHistStallRepair.B

  when ((s2_valid && !icache.io.resp.valid) ||
        (s2_valid && icache.io.resp.valid && !f3_ready)) {
    s0_valid := (!s2_tlb_resp.ae.inst && !s2_tlb_resp.pf.inst) || s2_is_replay || s2_tlb_miss
    s0_vpc   := s2_vpc
    s0_is_replay := s2_valid && icache.io.resp.valid
    // When this is not a replay (it queried the BPDs, we should use f3 resp in the replaying s1)
    s0_s1_use_f3_bpd_resp := !s2_is_replay
    s0_ghist := s2_ghist
    s0_tsrc  := s2_tsrc
    f1_clear := true.B
  } .elsewhen (s2_valid && f3_ready) {
    when (s1_valid && s1_vpc === f2_predicted_target && !f2_correct_f1_ghist) {
      // We trust our prediction of what the global history for the next branch should be
      s2_ghist := f2_predicted_ghist
    }
    when ((s1_valid && (s1_vpc =/= f2_predicted_target || f2_correct_f1_ghist)) || !s1_valid) {
      f1_clear := true.B

      s0_valid     := !((s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst) && !s2_is_replay)
      s0_vpc       := f2_predicted_target
      s0_is_replay := false.B
      s0_ghist     := f2_predicted_ghist
      s2_fsrc      := BSRC_2
      s0_tsrc      := BSRC_2
    }
  }
  s0_replay_bpd_resp := f2_bpd_resp
  s0_replay_resp := s2_tlb_resp
  s0_replay_ppc  := s2_ppc

  // --------------------------------------------------------
  // **** F3 ****
  // --------------------------------------------------------
  val f3_clear = WireInit(false.B)
  val f3 = withReset(reset.asBool || f3_clear) {
    Module(new Queue(new FrontendResp, 1, pipe=true, flow=false)) }

  // Queue up the bpd resp as well, incase f4 backpressures f3
  // This is "flow" because the response (enq) arrives in f3, not f2
  val f3_bpd_resp = withReset(reset.asBool || f3_clear) {
    Module(new Queue(new BranchPredictionBundle, 1, pipe=true, flow=true)) }




  val f4_ready = Wire(Bool())
  f3_ready := f3.io.enq.ready
  f3.io.enq.valid   := (s2_valid && !f2_clear &&
    (icache.io.resp.valid || ((s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst) && !s2_tlb_miss))
  )
  f3.io.enq.bits.pc := s2_vpc
  f3.io.enq.bits.data  := Mux(s2_xcpt, 0.U, icache.io.resp.bits.data)
  f3.io.enq.bits.ghist := s2_ghist
  f3.io.enq.bits.mask := fetchMask(s2_vpc)
  f3.io.enq.bits.xcpt := s2_tlb_resp
  f3.io.enq.bits.fsrc := s2_fsrc
  f3.io.enq.bits.tsrc := s2_tsrc

  // RAS takes a cycle to read
  val ras_read_idx = RegInit(0.U(log2Ceil(nRasEntries).W))
  ras.io.read_idx := ras_read_idx
  when (f3.io.enq.fire) {
    ras_read_idx := f3.io.enq.bits.ghist.ras_idx
    ras.io.read_idx := f3.io.enq.bits.ghist.ras_idx
  }


  // The BPD resp comes in f3
  f3_bpd_resp.io.enq.valid := f3.io.deq.valid && RegNext(f3.io.enq.ready)
  f3_bpd_resp.io.enq.bits  := bpd.io.resp.f3
  when (f3_bpd_resp.io.enq.fire) {
    bpd.io.f3_fire := true.B
  }

  f3.io.deq.ready := f4_ready
  f3_bpd_resp.io.deq.ready := f4_ready


  val f3_imemresp     = f3.io.deq.bits
  val f3_bank_mask    = bankMask(f3_imemresp.pc)
  val f3_data         = f3_imemresp.data
  val f3_aligned_pc   = bankAlign(f3_imemresp.pc)
  val f3_is_last_bank_in_block = isLastBankInBlock(f3_aligned_pc)
  val f3_is_rvc       = Wire(Vec(fetchWidth, Bool()))
  val f3_redirects    = Wire(Vec(fetchWidth, Bool()))
  val f3_targs        = Wire(Vec(fetchWidth, UInt(vaddrBitsExtended.W)))
  val f3_cfi_types    = Wire(Vec(fetchWidth, UInt(CFI_SZ.W)))
  val f3_shadowed_mask = Wire(Vec(fetchWidth, Bool()))
  val f3_fetch_bundle = Wire(new FetchBundle)
  val f3_mask         = Wire(Vec(fetchWidth, Bool()))
  val f3_br_mask      = Wire(Vec(fetchWidth, Bool()))
  val f3_call_mask    = Wire(Vec(fetchWidth, Bool()))
  val f3_ret_mask     = Wire(Vec(fetchWidth, Bool()))
  val f3_npc_plus4_mask = Wire(Vec(fetchWidth, Bool()))
  val f3_btb_mispredicts = Wire(Vec(fetchWidth, Bool()))
  f3_fetch_bundle.mask := f3_mask.asUInt
  f3_fetch_bundle.br_mask := f3_br_mask.asUInt
  f3_fetch_bundle.pc := f3_imemresp.pc
  f3_fetch_bundle.ftq_idx := 0.U // This gets assigned later
  f3_fetch_bundle.xcpt_pf_if := f3_imemresp.xcpt.pf.inst
  f3_fetch_bundle.xcpt_ae_if := f3_imemresp.xcpt.ae.inst
  f3_fetch_bundle.fsrc := f3_imemresp.fsrc
  f3_fetch_bundle.tsrc := f3_imemresp.tsrc
  f3_fetch_bundle.shadowed_mask := f3_shadowed_mask

  // Tracks trailing 16b of previous fetch packet
  val f3_prev_half    = Reg(UInt(16.W))
  // Tracks if last fetchpacket contained a half-inst
  val f3_prev_is_half = RegInit(false.B)

  require(fetchWidth >= 4) // Logic gets kind of annoying with fetchWidth = 2
  def isRVC(inst: UInt) = (inst(1,0) =/= 3.U)
  var redirect_found = false.B
  var bank_prev_is_half = f3_prev_is_half
  var bank_prev_half    = f3_prev_half
  var last_inst = 0.U(16.W)
  for (b <- 0 until nBanks) {
    val bank_data  = f3_data((b+1)*bankWidth*16-1, b*bankWidth*16)
    val bank_mask  = Wire(Vec(bankWidth, Bool()))
    val bank_insts = Wire(Vec(bankWidth, UInt(32.W)))

    for (w <- 0 until bankWidth) {
      val i = (b * bankWidth) + w

      val valid = Wire(Bool())
      val bpu = Module(new BreakpointUnit(nBreakpoints))
      bpu.io.status   := io.cpu.status
      bpu.io.bp       := io.cpu.bp
      bpu.io.ea       := DontCare
      bpu.io.mcontext := io.cpu.mcontext
      bpu.io.scontext := io.cpu.scontext

      val brsigs = Wire(new BranchDecodeSignals)
      if (w == 0) {
        val inst0 = Cat(bank_data(15,0), f3_prev_half)
        val inst1 = bank_data(31,0)
        val exp_inst0 = ExpandRVC(inst0)
        val exp_inst1 = ExpandRVC(inst1)
        val pc0 = (f3_aligned_pc + (i << log2Ceil(coreInstBytes)).U - 2.U)
        val pc1 = (f3_aligned_pc + (i << log2Ceil(coreInstBytes)).U)

        val bpd_decoder0 = Module(new BranchDecode)
        bpd_decoder0.io.inst := exp_inst0
        bpd_decoder0.io.pc   := pc0
        val bpd_decoder1 = Module(new BranchDecode)
        bpd_decoder1.io.inst := exp_inst1
        bpd_decoder1.io.pc   := pc1

        when (bank_prev_is_half) {
          bank_insts(w)                := inst0
          f3_fetch_bundle.insts(i)     := inst0
          f3_fetch_bundle.exp_insts(i) := exp_inst0
          bpu.io.pc                    := pc0
          brsigs                       := bpd_decoder0.io.out
          f3_fetch_bundle.edge_inst(b) := true.B
          if (b > 0) {
            val inst0b     = Cat(bank_data(15,0), last_inst)
            val exp_inst0b = ExpandRVC(inst0b)
            val bpd_decoder0b = Module(new BranchDecode)
            bpd_decoder0b.io.inst := exp_inst0b
            bpd_decoder0b.io.pc   := pc0

            when (f3_bank_mask(b-1)) {
              bank_insts(w)                := inst0b
              f3_fetch_bundle.insts(i)     := inst0b
              f3_fetch_bundle.exp_insts(i) := exp_inst0b
              brsigs                       := bpd_decoder0b.io.out
            }
          }
        } .otherwise {
          bank_insts(w)                := inst1
          f3_fetch_bundle.insts(i)     := inst1
          f3_fetch_bundle.exp_insts(i) := exp_inst1
          bpu.io.pc                    := pc1
          brsigs                       := bpd_decoder1.io.out
          f3_fetch_bundle.edge_inst(b) := false.B
        }
        valid := true.B
      } else {
        val inst = Wire(UInt(32.W))
        val exp_inst = ExpandRVC(inst)
        val pc = f3_aligned_pc + (i << log2Ceil(coreInstBytes)).U
        val bpd_decoder = Module(new BranchDecode)
        bpd_decoder.io.inst := exp_inst
        bpd_decoder.io.pc   := pc

        bank_insts(w)                := inst
        f3_fetch_bundle.insts(i)     := inst
        f3_fetch_bundle.exp_insts(i) := exp_inst
        bpu.io.pc                    := pc
        brsigs                       := bpd_decoder.io.out
        if (w == 1) {
          // Need special case since 0th instruction may carry over the wrap around
          inst  := bank_data(47,16)
          valid := bank_prev_is_half || !(bank_mask(0) && !isRVC(bank_insts(0)))
        } else if (w == bankWidth - 1) {
          inst  := Cat(0.U(16.W), bank_data(bankWidth*16-1,(bankWidth-1)*16))
          valid := !((bank_mask(w-1) && !isRVC(bank_insts(w-1))) ||
            !isRVC(inst))
        } else {
          inst  := bank_data(w*16+32-1,w*16)
          valid := !(bank_mask(w-1) && !isRVC(bank_insts(w-1)))
        }
      }

      f3_is_rvc(i) := isRVC(bank_insts(w))


      bank_mask(w) := f3.io.deq.valid && f3_imemresp.mask(i) && valid && !redirect_found
      f3_mask  (i) := f3.io.deq.valid && f3_imemresp.mask(i) && valid && !redirect_found
      f3_targs (i) := Mux(brsigs.cfi_type === CFI_JALR,
        f3_bpd_resp.io.deq.bits.preds(i).predicted_pc.bits,
        brsigs.target)

      // Flush BTB entries for JALs if we mispredict the target
      f3_btb_mispredicts(i) := (brsigs.cfi_type === CFI_JAL && valid &&
        f3_bpd_resp.io.deq.bits.preds(i).predicted_pc.valid &&
        (f3_bpd_resp.io.deq.bits.preds(i).predicted_pc.bits =/= brsigs.target)
      )


      f3_npc_plus4_mask(i) := (if (w == 0) {
        !f3_is_rvc(i) && !bank_prev_is_half
      } else {
        !f3_is_rvc(i)
      })
      val offset_from_aligned_pc = (
        (i << 1).U((log2Ceil(icBlockBytes)+1).W) +
        brsigs.sfb_offset.bits -
        Mux(bank_prev_is_half && (w == 0).B, 2.U, 0.U)
      )
      val lower_mask = Wire(UInt((2*fetchWidth).W))
      val upper_mask = Wire(UInt((2*fetchWidth).W))
      lower_mask := UIntToOH(i.U)
      upper_mask := UIntToOH(offset_from_aligned_pc(log2Ceil(fetchBytes)+1,1)) << Mux(f3_is_last_bank_in_block, bankWidth.U, 0.U)

      f3_fetch_bundle.sfbs(i) := (
        f3_mask(i) &&
        brsigs.sfb_offset.valid &&
        (offset_from_aligned_pc <= Mux(f3_is_last_bank_in_block, (fetchBytes+bankBytes).U,(2*fetchBytes).U))
      )
      f3_fetch_bundle.sfb_masks(i)       := ~MaskLower(lower_mask) & ~MaskUpper(upper_mask)
      f3_fetch_bundle.shadowable_mask(i) := (!(f3_fetch_bundle.xcpt_pf_if || f3_fetch_bundle.xcpt_ae_if || bpu.io.debug_if || bpu.io.xcpt_if) &&
                                             f3_bank_mask(b) &&
                                             (brsigs.shadowable || !f3_mask(i)))
      f3_fetch_bundle.sfb_dests(i)       := offset_from_aligned_pc

      // Redirect if
      //  1) its a JAL/JALR (unconditional)
      //  2) the BPD believes this is a branch and says we should take it
      f3_redirects(i)    := f3_mask(i) && (
        brsigs.cfi_type === CFI_JAL || brsigs.cfi_type === CFI_JALR ||
        (brsigs.cfi_type === CFI_BR && f3_bpd_resp.io.deq.bits.preds(i).taken && useBPD.B)
      )

      f3_br_mask(i)   := f3_mask(i) && brsigs.cfi_type === CFI_BR
      f3_cfi_types(i) := brsigs.cfi_type
      f3_call_mask(i) := brsigs.is_call
      f3_ret_mask(i)  := brsigs.is_ret

      f3_fetch_bundle.bp_debug_if_oh(i) := bpu.io.debug_if
      f3_fetch_bundle.bp_xcpt_if_oh (i) := bpu.io.xcpt_if

      redirect_found = redirect_found || f3_redirects(i)
    }
    last_inst = bank_insts(bankWidth-1)(15,0)
    bank_prev_is_half = Mux(f3_bank_mask(b),
      (!(bank_mask(bankWidth-2) && !isRVC(bank_insts(bankWidth-2))) && !isRVC(last_inst)),
      bank_prev_is_half)
    bank_prev_half    = Mux(f3_bank_mask(b),
      last_inst(15,0),
      bank_prev_half)
  }

  f3_fetch_bundle.cfi_type      := f3_cfi_types(f3_fetch_bundle.cfi_idx.bits)
  f3_fetch_bundle.cfi_is_call   := f3_call_mask(f3_fetch_bundle.cfi_idx.bits)
  f3_fetch_bundle.cfi_is_ret    := f3_ret_mask (f3_fetch_bundle.cfi_idx.bits)
  f3_fetch_bundle.cfi_npc_plus4 := f3_npc_plus4_mask(f3_fetch_bundle.cfi_idx.bits)

  f3_fetch_bundle.ghist    := f3.io.deq.bits.ghist
  f3_fetch_bundle.lhist    := f3_bpd_resp.io.deq.bits.lhist
  f3_fetch_bundle.bpd_meta := f3_bpd_resp.io.deq.bits.meta

  f3_fetch_bundle.end_half.valid := bank_prev_is_half
  f3_fetch_bundle.end_half.bits  := bank_prev_half

  when (f3.io.deq.fire) {
    f3_prev_is_half := bank_prev_is_half
    f3_prev_half    := bank_prev_half
    assert(f3_bpd_resp.io.deq.bits.pc === f3_fetch_bundle.pc)
  }

  when (f3_clear) {
    f3_prev_is_half := false.B
  }

  f3_fetch_bundle.cfi_idx.valid := f3_redirects.reduce(_||_)
  f3_fetch_bundle.cfi_idx.bits  := PriorityEncoder(f3_redirects)

  f3_fetch_bundle.ras_top := ras.io.read_addr
  // Redirect earlier stages only if the later stage
  // can consume this packet

  val f3_predicted_target = Mux(f3_redirects.reduce(_||_),
    Mux(f3_fetch_bundle.cfi_is_ret && useBPD.B && useRAS.B,
      ras.io.read_addr,
      f3_targs(PriorityEncoder(f3_redirects))
    ),
    nextFetch(f3_fetch_bundle.pc)
  )

  f3_fetch_bundle.next_pc       := f3_predicted_target
  val f3_predicted_ghist = f3_fetch_bundle.ghist.update(
    f3_fetch_bundle.br_mask,
    f3_fetch_bundle.cfi_idx.valid,
    f3_fetch_bundle.br_mask(f3_fetch_bundle.cfi_idx.bits),
    f3_fetch_bundle.cfi_idx.bits,
    f3_fetch_bundle.cfi_idx.valid,
    f3_fetch_bundle.pc,
    f3_fetch_bundle.cfi_is_call,
    f3_fetch_bundle.cfi_is_ret
  )


  ras.io.write_valid := false.B
  ras.io.write_addr  := f3_aligned_pc + (f3_fetch_bundle.cfi_idx.bits << 1) + Mux(
    f3_fetch_bundle.cfi_npc_plus4, 4.U, 2.U)
  ras.io.write_idx   := WrapInc(f3_fetch_bundle.ghist.ras_idx, nRasEntries)


  val f3_correct_f1_ghist = s1_ghist =/= f3_predicted_ghist && enableGHistStallRepair.B
  val f3_correct_f2_ghist = s2_ghist =/= f3_predicted_ghist && enableGHistStallRepair.B

  when (f3.io.deq.valid && f4_ready) {
    when (f3_fetch_bundle.cfi_is_call && f3_fetch_bundle.cfi_idx.valid) {
      ras.io.write_valid := true.B
    }
    when (f3_redirects.reduce(_||_)) {
      f3_prev_is_half := false.B
    }
    when (s2_valid && s2_vpc === f3_predicted_target && !f3_correct_f2_ghist) {
      f3.io.enq.bits.ghist := f3_predicted_ghist
    } .elsewhen (!s2_valid && s1_valid && s1_vpc === f3_predicted_target && !f3_correct_f1_ghist) {
      s2_ghist := f3_predicted_ghist
    } .elsewhen (( s2_valid &&  (s2_vpc =/= f3_predicted_target || f3_correct_f2_ghist)) ||
          (!s2_valid &&  s1_valid && (s1_vpc =/= f3_predicted_target || f3_correct_f1_ghist)) ||
          (!s2_valid && !s1_valid)) {
      f2_clear := true.B
      f1_clear := true.B

      s0_valid     := !(f3_fetch_bundle.xcpt_pf_if || f3_fetch_bundle.xcpt_ae_if)
      s0_vpc       := f3_predicted_target
      s0_is_replay := false.B
      s0_ghist     := f3_predicted_ghist
      s0_tsrc      := BSRC_3

      f3_fetch_bundle.fsrc := BSRC_3
    }
  }

  // When f3 finds a btb mispredict, queue up a bpd correction update
  val f4_btb_corrections = Module(new Queue(new BranchPredictionUpdate, 2))
  f4_btb_corrections.io.enq.valid := f3.io.deq.fire && f3_btb_mispredicts.reduce(_||_) && enableBTBFastRepair.B
  f4_btb_corrections.io.enq.bits  := DontCare
  f4_btb_corrections.io.enq.bits.is_mispredict_update := false.B
  f4_btb_corrections.io.enq.bits.is_repair_update     := false.B
  f4_btb_corrections.io.enq.bits.btb_mispredicts      := f3_btb_mispredicts.asUInt
  f4_btb_corrections.io.enq.bits.pc                   := f3_fetch_bundle.pc
  f4_btb_corrections.io.enq.bits.ghist                := f3_fetch_bundle.ghist
  f4_btb_corrections.io.enq.bits.lhist                := f3_fetch_bundle.lhist
  f4_btb_corrections.io.enq.bits.meta                 := f3_fetch_bundle.bpd_meta


  // -------------------------------------------------------
  // **** F4 ****
  // -------------------------------------------------------
  val f4_clear = WireInit(false.B)
  val f4 = withReset(reset.asBool || f4_clear) {
    Module(new Queue(new FetchBundle, 1, pipe=true, flow=false))}

  val fb  = Module(new FetchBuffer)
  val ftq = Module(new FetchTargetQueue)

  // When we mispredict, we need to repair

  // Deal with sfbs
  val f4_shadowable_masks = VecInit((0 until fetchWidth) map { i =>
     f4.io.deq.bits.shadowable_mask.asUInt |
    ~f4.io.deq.bits.sfb_masks(i)(fetchWidth-1,0)
  })
  val f3_shadowable_masks = VecInit((0 until fetchWidth) map { i =>
    Mux(f4.io.enq.valid, f4.io.enq.bits.shadowable_mask.asUInt, 0.U) |
    ~f4.io.deq.bits.sfb_masks(i)(2*fetchWidth-1,fetchWidth)
  })
  val f4_sfbs = VecInit((0 until fetchWidth) map { i =>
    enableSFBOpt.B &&
    ((~f4_shadowable_masks(i) === 0.U) &&
     (~f3_shadowable_masks(i) === 0.U) &&
     f4.io.deq.bits.sfbs(i) &&
     !(f4.io.deq.bits.cfi_idx.valid && f4.io.deq.bits.cfi_idx.bits === i.U) &&
      Mux(f4.io.deq.bits.sfb_dests(i) === 0.U,
        !bank_prev_is_half,
      Mux(f4.io.deq.bits.sfb_dests(i) === fetchBytes.U,
        !f4.io.deq.bits.end_half.valid,
        true.B)
      )

     )
  })
  val f4_sfb_valid = f4_sfbs.reduce(_||_) && f4.io.deq.valid
  val f4_sfb_idx   = PriorityEncoder(f4_sfbs)
  val f4_sfb_mask  = f4.io.deq.bits.sfb_masks(f4_sfb_idx)
  // If we have a SFB, wait for next fetch to be available in f3
  val f4_delay     = (
    f4.io.deq.bits.sfbs.reduce(_||_) &&
    !f4.io.deq.bits.cfi_idx.valid &&
    !f4.io.enq.valid &&
    !f4.io.deq.bits.xcpt_pf_if &&
    !f4.io.deq.bits.xcpt_ae_if
  )
  when (f4_sfb_valid) {
    f3_shadowed_mask := f4_sfb_mask(2*fetchWidth-1,fetchWidth).asBools
  } .otherwise {
    f3_shadowed_mask := VecInit(0.U(fetchWidth.W).asBools)
  }

  f4_ready := f4.io.enq.ready
  f4.io.enq.valid := f3.io.deq.valid && !f3_clear
  f4.io.enq.bits  := f3_fetch_bundle
  f4.io.deq.ready := fb.io.enq.ready && ftq.io.enq.ready && !f4_delay

  fb.io.enq.valid := f4.io.deq.valid && ftq.io.enq.ready && !f4_delay
  fb.io.enq.bits  := f4.io.deq.bits
  fb.io.enq.bits.ftq_idx := ftq.io.enq_idx
  fb.io.enq.bits.sfbs    := Mux(f4_sfb_valid, UIntToOH(f4_sfb_idx), 0.U(fetchWidth.W)).asBools
  fb.io.enq.bits.shadowed_mask := (
    Mux(f4_sfb_valid, f4_sfb_mask(fetchWidth-1,0), 0.U(fetchWidth.W)) |
    f4.io.deq.bits.shadowed_mask.asUInt
  ).asBools


  ftq.io.enq.valid          := f4.io.deq.valid && fb.io.enq.ready && !f4_delay
  ftq.io.enq.bits           := f4.io.deq.bits

  val bpd_update_arbiter = Module(new Arbiter(new BranchPredictionUpdate, 2))
  bpd_update_arbiter.io.in(0).valid := ftq.io.bpdupdate.valid
  bpd_update_arbiter.io.in(0).bits  := ftq.io.bpdupdate.bits
  assert(bpd_update_arbiter.io.in(0).ready)
  bpd_update_arbiter.io.in(1) <> f4_btb_corrections.io.deq
  bpd.io.update := bpd_update_arbiter.io.out
  bpd_update_arbiter.io.out.ready := true.B

  when (ftq.io.ras_update && enableRasTopRepair.B) {
    ras.io.write_valid := true.B
    ras.io.write_idx   := ftq.io.ras_update_idx
    ras.io.write_addr  := ftq.io.ras_update_pc
  }


  // -------------------------------------------------------
  // **** To Core (F5) ****
  // -------------------------------------------------------

  io.cpu.fetchpacket <> fb.io.deq
  io.cpu.get_pc <> ftq.io.get_ftq_pc
  ftq.io.deq := io.cpu.commit
  ftq.io.brupdate := io.cpu.brupdate

  ftq.io.redirect.valid   := io.cpu.redirect_val
  ftq.io.redirect.bits    := io.cpu.redirect_ftq_idx
  fb.io.clear := false.B

  when (io.cpu.sfence.valid) {
    fb.io.clear := true.B
    f4_clear    := true.B
    f3_clear    := true.B
    f2_clear    := true.B
    f1_clear    := true.B

    s0_valid     := false.B
    s0_vpc       := io.cpu.sfence.bits.addr
    s0_is_replay := false.B
    s0_is_sfence := true.B

  }.elsewhen (io.cpu.redirect_flush) {
    fb.io.clear := true.B
    f4_clear    := true.B
    f3_clear    := true.B
    f2_clear    := true.B
    f1_clear    := true.B

    f3_prev_is_half := false.B

    s0_valid     := io.cpu.redirect_val
    s0_vpc       := io.cpu.redirect_pc
    s0_ghist     := io.cpu.redirect_ghist
    s0_tsrc      := BSRC_C
    s0_is_replay := false.B

    ftq.io.redirect.valid := io.cpu.redirect_val
    ftq.io.redirect.bits  := io.cpu.redirect_ftq_idx
  }

  ftq.io.debug_ftq_idx := io.cpu.debug_ftq_idx
  io.cpu.debug_fetch_pc := ftq.io.debug_fetch_pc


  override def toString: String =
    (BoomCoreStringPrefix("====Overall Frontend Params====") + "\n"
    + icache.toString + bpd.toString)
}
