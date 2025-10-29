//******************************************************************************
// Copyright (c) 2015 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISC-V Processor Core
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// BOOM has the following (conceptual) stages:
//   if0 - Instruction Fetch 0 (next-pc select)
//   if1 - Instruction Fetch 1 (I$ access)
//   if2 - Instruction Fetch 2 (instruction return)
//   if3 - Instruction Fetch 3 (enqueue to fetch buffer)
//   if4 - Instruction Fetch 4 (redirect from bpd)
//   dec - Decode
//   ren - Rename1
//   dis - Rename2/Dispatch
//   iss - Issue
//   rrd - Register Read
//   exe - Execute
//   mem - Memory
//   sxt - Sign-extend
//   wb  - Writeback
//   com - Commit

package boom.v4.exu

import java.nio.file.{Paths}

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.rocket.{Causes, PRV, CSR, CSRs, TracedInstruction}
import freechips.rocketchip.tile.{HasFPUParameters, TraceBundle, CustomCSR}
import freechips.rocketchip.util.{Str, UIntIsOneOf, CoreMonitorBundle, PlusArg}
import freechips.rocketchip.devices.tilelink.{PLICConsts, CLINTConsts}

import boom.v4.common._
import boom.v4.ifu.{GlobalHistory, HasBoomFrontendParameters}
import boom.v4.util._

/**
 * Top level core object that connects the Frontend to the rest of the pipeline.
 */
class BoomCore(roccCSRs: Seq[Seq[CustomCSR]])(implicit p: Parameters) extends BoomModule
  with HasBoomFrontendParameters // TODO: Don't add this trait
  with HasFPUParameters
{
  val nTotalRoCCCSRs = roccCSRs.flatten.size
  val io = IO(new freechips.rocketchip.tile.CoreBundle
  {
    val hartid = Input(UInt(hartIdLen.W))
    val interrupts = Input(new freechips.rocketchip.rocket.CoreInterrupts(false))
    val ifu = new boom.v4.ifu.BoomFrontendIO
    val ptw = Flipped(new freechips.rocketchip.rocket.DatapathPTWIO())
    val rocc = Flipped(new freechips.rocketchip.tile.RoCCCoreIO(nTotalRoCCCSRs))
    val lsu = Flipped(new boom.v4.lsu.LSUCoreIO)
    val ptw_tlb = new freechips.rocketchip.rocket.TLBPTWIO()
    val trace = Output(new TraceBundle)
    val fcsr_rm = UInt(freechips.rocketchip.tile.FPConstants.RM_SZ.W)
  })
  io.ptw_tlb := DontCare
  io.ptw := DontCare
  io.ifu := DontCare

  //**********************************
  // construct all of the modules

  val mem_exe_units: Seq[MemExeUnit] = (0 until memWidth) map { w =>
    Module(new MemExeUnit(
      hasAGen = w >= (memWidth - lsuWidth),
      hasDGen = true
    )).suggestName(s"mem_exe_unit_${w}")
  }
  val agen_exe_units: Seq[MemExeUnit] = mem_exe_units.filter(_.hasAGen)
  val unq_exe_units: Seq[UniqueExeUnit] = Seq(
    Module(new UniqueExeUnit(
      hasCSR = true,
      hasRocc = usingRoCC,
      hasMul = true,
      hasDiv = true,
      hasIfpu = true
    )).suggestName(s"unique_exe_unit_0")
  )
  val unq_exe_unit = unq_exe_units(0)
  val csr_resp = unq_exe_unit.io_csr_resp.get
  val alu_exe_units: Seq[ALUExeUnit] = (0 until aluWidth) map { w =>
    Module(new ALUExeUnit(
      id             = w
    )).suggestName(s"alu_exe_unit_${w}")
  }
  val all_exe_units = (alu_exe_units ++ mem_exe_units ++ unq_exe_units)

  // Meanwhile, the FP pipeline holds the FP issue window, FP regfile, and FP arithmetic units.
  val fp_pipeline = Module(new FpPipeline)

  // ********************************************************
  // Clear fp_pipeline before use
  fp_pipeline.io.ll_wports := DontCare


  val numIrfWritePorts        = aluWidth + lsuWidth + 1
  val numIrfLogicalReadPorts  = all_exe_units.map(_.nReaders).reduce(_+_)

  val numIntWakeups           = coreWidth + lsuWidth + 1
  val numFpWakeupPorts        = fp_pipeline.io.wakeups.length

  val numImmReaders     = aluWidth + memWidth + 1 // "Wakeup" immediates when they are read

  val decode_units      = (0 until decodeWidth) map { w => Module(new DecodeUnit).suggestName(s"decode_${w}") }
  val dec_brmask_logic  = Module(new BranchMaskGenerationLogic(coreWidth))
  val rename_stage      = Module(new RenameStage(coreWidth, numIntPhysRegs, numIntWakeups, false))
  val fp_rename_stage   = Module(new RenameStage(coreWidth, numFpPhysRegs, numFpWakeupPorts, true))
  val pred_rename_stage = Module(new PredRenameStage(coreWidth, 1))
  val imm_rename_stage  = Module(new ImmRenameStage(coreWidth, numImmReaders)) // wakeup ports used when insts read imm
  val rename_stages     = Seq(rename_stage, pred_rename_stage, imm_rename_stage) ++ (if (usingFPU) Seq(fp_rename_stage) else Nil)

  val mem_iss_unit     = IssueUnit(memIssueParam, numIntWakeups, false, false)
  val unq_iss_unit     = IssueUnit(unqIssueParam, numIntWakeups, false, false)
  val alu_iss_unit     = IssueUnit(aluIssueParam, numIntWakeups, enableColumnALUIssue, enableALUSingleWideDispatch)
  val dispatcher       = Module(new BasicDispatcher)
  val iregfileBankedWriteArray = Seq.fill(lsuWidth + 1) { None } ++ ((0 until aluWidth).map { w => if (enableColumnALUWrites) Some(w) else None })
  val iregfile         = Module(new BankedRF(
    UInt(xLen.W),
    numIrfBanks,
    numIrfLogicalReadPorts,
    numIntPhysRegs,
    numIrfLogicalReadPorts,
    numIrfReadPorts,
    numIrfWritePorts,
    iregfileBankedWriteArray,
    "Integer"
  ))
  val pregfile         = Module(new FullyPortedRF(
    Bool(),
    ftqSz,
    aluWidth,
    1,
    "Predicate"
  ))
  val immregfile       = Module(new FullyPortedRF(
    UInt(LONGEST_IMM_SZ.W),
    numImmPhysRegs,
    numImmReaders,
    coreWidth,
    "Immediate"
  ))
  val bregfile         = Module(new FullyPortedRF(
    new BrInfoBundle,
    maxBrCount,
    aluWidth,
    coreWidth,
    "Branch"
  ))
  val rob              = Module(new Rob(
    numIrfWritePorts + numFpWakeupPorts,
    trace
  ))
  // Used to wakeup registers in rename and issue. ROB needs to listen to something else.
  val int_wakeups  = Wire(Vec(numIntWakeups, Valid(new Wakeup)))
  val pred_wakeups = Wire(Vec(aluWidth     , Valid(new Wakeup)))

  // The arb stage guarantees only 1 pred wakeup per cycle
  assert(PopCount(pred_wakeups.map(_.valid)) <= 1.U)
  val pred_wakeup  = Wire(Valid(new Wakeup))
  pred_wakeup.valid         := pred_wakeups.map(_.valid).reduce(_||_)
  pred_wakeup.bits          := DontCare
  pred_wakeup.bits.uop.pdst := Mux1H(pred_wakeups.map(_.valid), pred_wakeups.map(_.bits.uop.pdst))

  val int_bypasses  = Wire(Vec(coreWidth + lsuWidth, Valid(new ExeUnitResp(xLen))))

  //***********************************
  // Pipeline State Registers and Wires

  // Decode/Rename1 Stage
  val dec_valids = Wire(Vec(coreWidth, Bool()))  // are the decoded instruction valid? It may be held up though.
  val dec_uops   = Wire(Vec(coreWidth, new MicroOp()))
  val dec_fire   = Wire(Vec(coreWidth, Bool()))  // can the instruction fire beyond decode?
                                                    // (can still be stopped in ren or dis)
  val dec_ready  = Wire(Bool())
  val dec_xcpts  = Wire(Vec(coreWidth, Bool()))
  val ren_stalls = Wire(Vec(coreWidth, Bool()))

  // Rename2/Dispatch stage
  val dis_valids = Wire(Vec(coreWidth, Bool()))
  val dis_uops   = Wire(Vec(coreWidth, new MicroOp))
  val dis_fire   = Wire(Vec(coreWidth, Bool()))
  val dis_ready  = Wire(Bool())

  // Issue Stage/Register Read
  val mem_iss_uops = mem_iss_unit.io.iss_uops
  val alu_iss_uops = alu_iss_unit.io.iss_uops
  val unq_iss_uops = unq_iss_unit.io.iss_uops

  // --------------------------------------
  // Dealing with branch resolutions

  // The individual branch resolutions from each ALU
  val brinfos = Reg(Vec(coreWidth, Valid(new BrResolutionInfo)))

  // "Merged" branch update info from all ALUs
  // brmask contains masks for rapidly clearing mispredicted instructions
  // brindices contains indices to reset pointers for allocated structures
  //           brindices is delayed a cycle
  val brupdate  = Wire(new BrUpdateInfo)
  val b1    = Wire(new BrUpdateMasks)
  val b2    = Reg(new BrResolutionInfo)

  brupdate.b1 := b1
  brupdate.b2 := b2

  for ((b, a) <- brinfos zip alu_exe_units) {
    b.bits := UpdateBrMask(brupdate, a.io_brinfo.bits)
    b.valid := a.io_brinfo.valid && !rob.io.flush.valid && !IsKilledByBranch(brupdate, RegNext(rob.io.flush.valid), a.io_brinfo.bits)
  }
  b1.resolve_mask := brinfos.map(x => x.valid << x.bits.uop.br_tag).reduce(_|_)
  b1.mispredict_mask := brinfos.map(x => (x.valid && x.bits.mispredict) << x.bits.uop.br_tag).reduce(_|_)

  // Find the oldest mispredict and use it to update indices
  val live_brinfos      = brinfos.map(br => br.valid && br.bits.mispredict && !IsKilledByBranch(brupdate, RegNext(rob.io.flush.valid), br.bits.uop))
  val mispredict_val    = live_brinfos.reduce(_||_)

  b2.mispredict  := mispredict_val
  b2.cfi_type    := Mux1H(live_brinfos, brinfos.map(_.bits.cfi_type))
  b2.taken       := Mux1H(live_brinfos, brinfos.map(_.bits.taken))
  b2.pc_sel      := Mux1H(live_brinfos, brinfos.map(_.bits.pc_sel))
  b2.uop         := UpdateBrMask(brupdate, PriorityMux(live_brinfos, brinfos.map(_.bits.uop)))
  b2.jalr_target := Mux1H(live_brinfos, brinfos.map(_.bits.jalr_target))
  b2.target_offset := Mux1H(live_brinfos, brinfos.map(_.bits.target_offset))

  val oldest_mispredict_ftq_idx = Mux1H(live_brinfos, brinfos.map(_.bits.uop.ftq_idx))


  assert (!((brupdate.b1.mispredict_mask =/= 0.U || brupdate.b2.mispredict)
    && rob.io.rollback), "Can't have a mispredict during rollback.")

  io.ifu.brupdate := brupdate

  for (eu <- all_exe_units) {
    eu.io_brupdate := brupdate
  }

  fp_pipeline.io.brupdate := brupdate

  // Load/Store Unit & ExeUnits
  // val mem_resps = io.lsu.iresp
  var agen_idx = 0
  var dgen_idx = 0
  for (eu <- mem_exe_units) {
    if (eu.hasAGen) {
      io.lsu.agen(agen_idx) := eu.io_agen.get
      agen_idx += 1
    }
    if (eu.hasDGen) {
      io.lsu.dgen(dgen_idx) := eu.io_dgen.get
      dgen_idx += 1
    }
  }
  io.lsu.dgen(dgen_idx) := fp_pipeline.io.dgen



  //-------------------------------------------------------------
  // Uarch Hardware Performance Events (HPEs)

  val perfEvents = new freechips.rocketchip.rocket.EventSets(Seq(
    new freechips.rocketchip.rocket.EventSet((mask, hits) => (mask & hits).orR, Seq(
      ("exception", () => rob.io.com_xcpt.valid),
      ("nop",       () => false.B),
      ("nop",       () => false.B),
      ("nop",       () => false.B))),

    new freechips.rocketchip.rocket.EventSet((mask, hits) => (mask & hits).orR, Seq(
//      ("I$ blocked",                        () => icache_blocked),
      ("nop",                               () => false.B),
      ("branch misprediction",              () => b2.mispredict),
      ("control-flow target misprediction", () => b2.mispredict &&
                                                  b2.cfi_type === CFI_JALR),
      ("flush",                             () => rob.io.flush.valid),
// ("branch resolved",                   () => b2.valid)
    )),

    new freechips.rocketchip.rocket.EventSet((mask, hits) => (mask & hits).orR, Seq(
      ("I$ miss",     () => io.ifu.perf.acquire),
      ("D$ miss",     () => io.lsu.perf.acquire),
      ("D$ release",  () => io.lsu.perf.release),
      ("ITLB miss",   () => io.ifu.perf.tlbMiss),
      ("DTLB miss",   () => io.lsu.perf.tlbMiss),
      ("L2 TLB miss", () => io.ptw.perf.l2miss)))))
  val csr = Module(new freechips.rocketchip.rocket.CSRFile(perfEvents, boomParams.customCSRs.decls, roccCSRs.flatten))
  csr.io.inst foreach { c => c := DontCare }
  csr.io.rocc_interrupt := io.rocc.interrupt
  csr.io.gva := DontCare
  csr.io.htval := DontCare
  csr.io.mhtinst_read_pseudo := false.B

  val custom_csrs = Wire(new BoomCustomCSRs)
  custom_csrs.csrs.foreach { c => c.stall := false.B; c.set := false.B; c.sdata := DontCare }
  (custom_csrs.csrs zip csr.io.customCSRs).map { case (lhs, rhs) => lhs <> rhs }
  io.ifu.enable_bpd := custom_csrs.enableBPD

  //val icache_blocked = !(io.ifu.fetchpacket.valid || RegNext(io.ifu.fetchpacket.valid))
  val icache_blocked = false.B
  csr.io.counters foreach { c => c.inc := RegNext(perfEvents.evaluate(c.eventSel)) }

  //****************************************
  // Time Stamp Counter & Retired Instruction Counter
  // (only used for printf and vcd dumps - the actual counters are in the CSRFile)
  val debug_tsc_reg = RegInit(0.U(xLen.W))
  val debug_irt_reg = RegInit(0.U(xLen.W))
  val debug_brs     = RegInit(VecInit(Seq.fill(5) { 0.U(xLen.W) }))
  val debug_jals    = RegInit(VecInit(Seq.fill(5) { 0.U(xLen.W) }))
  val debug_jalrs   = RegInit(VecInit(Seq.fill(5) { 0.U(xLen.W) }))

  for (j <- 0 until 5) {
    debug_brs(j) := debug_brs(j) + PopCount(VecInit((0 until coreWidth) map {i =>
      rob.io.commit.arch_valids(i) &&
      (rob.io.commit.uops(i).debug_fsrc === j.U) &&
      rob.io.commit.uops(i).is_br
    }))
    debug_jals(j) := debug_jals(j) + PopCount(VecInit((0 until coreWidth) map {i =>
      rob.io.commit.arch_valids(i) &&
      (rob.io.commit.uops(i).debug_fsrc === j.U) &&
      rob.io.commit.uops(i).is_jal
    }))
    debug_jalrs(j) := debug_jalrs(j) + PopCount(VecInit((0 until coreWidth) map {i =>
      rob.io.commit.arch_valids(i) &&
      (rob.io.commit.uops(i).debug_fsrc === j.U) &&
      rob.io.commit.uops(i).is_jalr
    }))
  }

  dontTouch(debug_brs)
  dontTouch(debug_jals)
  dontTouch(debug_jalrs)

  debug_tsc_reg := debug_tsc_reg + 1.U
  debug_irt_reg := debug_irt_reg + PopCount(rob.io.commit.arch_valids.asUInt)
  dontTouch(debug_tsc_reg)
  dontTouch(debug_irt_reg)

  //****************************************
  // Print-out information about the machine

  val issStr = "(Age-based Priority)"



  val fpPipelineStr = fp_pipeline.toString

  override def toString: String =
    (BoomCoreStringPrefix("====Overall Core Params====") + "\n\n"
    + mem_exe_units.map(_.toString).mkString("") + "\n"
    + unq_exe_units.map(_.toString).mkString("") + "\n\n"
    + alu_exe_units.map(_.toString).mkString("") + "\n\n"
    + fpPipelineStr + "\n\n"
    + rob.toString + "\n\n"
    + BoomCoreStringPrefix(
        "===Other Core Params===",
        "Fetch Width           : " + fetchWidth,
        "Decode Width          : " + coreWidth,
        "Issue Width           : " + issueParams.map(_.issueWidth).sum,
        "ROB Size              : " + numRobEntries,
        "Issue Window Size     : " + issueParams.map(_.numEntries) + issStr,
        "Load/Store Unit Size  : " + numLdqEntries + "/" + numStqEntries,
        "Num Int Phys Registers: " + numIntPhysRegs,
        "Num FP  Phys Registers: " + numFpPhysRegs,
        "Load-to-use delay     : " + (if (enableFastLoadUse) 4 else 5),
        "Max Branch Count      : " + maxBrCount)
    + "\n\n" + iregfile.toString + "\n\n"
    + BoomCoreStringPrefix(
        "Num Wakeup Ports      : " + numIntWakeups,
        "Num Bypass Ports      : " + int_bypasses.length) + "\n"
    + BoomCoreStringPrefix(
        "DCache Ways           : " + dcacheParams.nWays,
        "DCache Sets           : " + dcacheParams.nSets,
        "DCache nMSHRs         : " + dcacheParams.nMSHRs,
        "ICache Ways           : " + icacheParams.nWays,
        "ICache Sets           : " + icacheParams.nSets,
        "D-TLB Ways            : " + dcacheParams.nTLBWays,
        "I-TLB Ways            : " + icacheParams.nTLBWays,
        "Paddr Bits            : " + paddrBits,
        "Vaddr Bits            : " + vaddrBits) + "\n"
    + BoomCoreStringPrefix(
        "Using FPU Unit?       : " + usingFPU.toString,
        "Using FDivSqrt?       : " + usingFDivSqrt.toString,
        "Using VM?             : " + usingVM.toString) + "\n")

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Fetch Stage/Frontend ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------
  io.ifu.redirect_val         := false.B
  io.ifu.redirect_flush       := false.B

  // Breakpoint info
  io.ifu.status  := csr.io.status
  io.ifu.bp      := csr.io.bp
  io.ifu.mcontext := csr.io.mcontext
  io.ifu.scontext := csr.io.scontext

  io.ifu.flush_icache := (0 until coreWidth).map { i =>
    (rob.io.commit.arch_valids(i) && rob.io.commit.uops(i).is_fencei) ||
    (RegNext(dec_valids(i) && dec_uops(i).is_jalr && csr.io.status.debug))
  }.reduce(_||_)

  // TODO FIX THIS HACK
  // The below code works because of two quirks with the flush mechanism
  //  1 ) All flush_on_commit instructions are also is_unique,
  //      In the future, this constraint will be relaxed.
  //  2 ) We send out flush signals one cycle after the commit signal. We need to
  //      mux between one/two cycle delay for the following cases:
  //       ERETs are reported to the CSR two cycles before we send the flush
  //       Exceptions are reported to the CSR on the cycle we send the flush
  // This discrepency should be resolved elsewhere.
  when (RegNext(rob.io.flush.valid)) {
    io.ifu.redirect_val   := true.B
    io.ifu.redirect_flush := true.B
    val flush_typ = RegNext(rob.io.flush.bits.flush_typ)
    // Clear the global history when we flush the ROB (exceptions, AMOs, unique instructions, etc.)
    val new_ghist = WireInit((0.U).asTypeOf(new GlobalHistory))
    new_ghist.current_saw_branch_not_taken := true.B
    new_ghist.ras_idx := io.ifu.rrd_ftq_resps(0).entry.ras_idx
    io.ifu.redirect_ghist := new_ghist
    when (FlushTypes.useCsrEvec(flush_typ)) {
      io.ifu.redirect_pc  := Mux(flush_typ === FlushTypes.eret,
                                 ShiftRegister(csr.io.evec, 3),
                                 csr.io.evec)
    } .otherwise {
      val flush_pc = (AlignPCToBoundary(io.ifu.rrd_ftq_resps(0).pc, icBlockBytes)
                      + RegNext(rob.io.flush.bits.pc_lob)
                      - Mux(RegNext(rob.io.flush.bits.edge_inst), 2.U, 0.U))
      val flush_pc_next = flush_pc + Mux(RegNext(rob.io.flush.bits.is_rvc), 2.U, 4.U)
      io.ifu.redirect_pc := Mux(FlushTypes.useSamePC(flush_typ),
                                flush_pc, flush_pc_next)

    }
    io.ifu.redirect_ftq_idx := RegNext(rob.io.flush.bits.ftq_idx)
  } .elsewhen (brupdate.b2.mispredict && !RegNext(rob.io.flush.valid)) {
    val block_pc = AlignPCToBoundary(io.ifu.rrd_ftq_resps(0).pc, icBlockBytes)
    val uop_maybe_pc = block_pc | brupdate.b2.uop.pc_lob
    val npc = uop_maybe_pc + Mux(brupdate.b2.uop.is_rvc || brupdate.b2.uop.edge_inst, 2.U, 4.U)
    val jal_br_target = Wire(UInt(vaddrBitsExtended.W))
    jal_br_target := (uop_maybe_pc.asSInt + brupdate.b2.target_offset +
      (Fill(vaddrBitsExtended-1, brupdate.b2.uop.edge_inst) << 1).asSInt).asUInt
    val bj_addr = Mux(brupdate.b2.cfi_type === CFI_JALR, brupdate.b2.jalr_target, jal_br_target)
    val mispredict_target = Mux(brupdate.b2.pc_sel === PC_PLUS4, npc, bj_addr)
    io.ifu.redirect_val     := true.B
    io.ifu.redirect_pc      := mispredict_target
    io.ifu.redirect_flush   := true.B
    io.ifu.redirect_ftq_idx := brupdate.b2.uop.ftq_idx
    val use_same_ghist = (brupdate.b2.cfi_type === CFI_BR &&
                          !brupdate.b2.taken &&
                          bankAlign(block_pc) === bankAlign(npc))
    val ftq_entry = io.ifu.rrd_ftq_resps(0).entry
    val cfi_idx = (brupdate.b2.uop.pc_lob ^
      Mux(ftq_entry.start_bank === 1.U, 1.U << log2Ceil(bankBytes), 0.U))(log2Ceil(fetchWidth), 1)
    val ftq_ghist = io.ifu.rrd_ftq_resps(0).ghist
    val next_ghist = ftq_ghist.update(
      ftq_entry.br_mask.asUInt,
      brupdate.b2.taken,
      brupdate.b2.cfi_type === CFI_BR,
      cfi_idx,
      true.B,
      io.ifu.rrd_ftq_resps(0).pc,
      ftq_entry.cfi_is_call && ftq_entry.cfi_idx.bits === cfi_idx,
      ftq_entry.cfi_is_ret  && ftq_entry.cfi_idx.bits === cfi_idx)


    io.ifu.redirect_ghist   := Mux(
      use_same_ghist,
      ftq_ghist,
      next_ghist)
    io.ifu.redirect_ghist.current_saw_branch_not_taken := use_same_ghist
  } .elsewhen (rob.io.flush_frontend || brupdate.b1.mispredict_mask =/= 0.U) {
    io.ifu.redirect_flush   := true.B
  }

  // Tell the FTQ it can deallocate entries by passing youngest ftq_idx.
  val youngest_com_idx = (coreWidth-1).U - PriorityEncoder(rob.io.commit.valids.reverse)
  io.ifu.commit.valid := rob.io.commit.valids.reduce(_|_) || rob.io.com_xcpt.valid
  io.ifu.commit.bits  := Mux(rob.io.com_xcpt.valid,
                             rob.io.com_xcpt.bits.ftq_idx,
                             rob.io.commit.uops(youngest_com_idx).ftq_idx)

  assert(!(rob.io.commit.valids.reduce(_|_) && rob.io.com_xcpt.valid),
    "ROB can't commit and except in same cycle!")




  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Decode Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // track mask of finished instructions in the bundle
  // use this to mask out insts coming from FetchBuffer that have been finished
  // for example, back pressure may cause us to only issue some instructions from FetchBuffer
  // but on the next cycle, we only want to retry a subset
  val dec_finished_mask = RegInit(0.U(coreWidth.W))

  //-------------------------------------------------------------
  // Pull out instructions and send to the Decoders

  io.ifu.fetchpacket.ready := dec_ready
  val dec_fbundle = io.ifu.fetchpacket.bits

  //-------------------------------------------------------------
  // Decoders

  for (w <- 0 until coreWidth) {
    dec_valids(w)                      := io.ifu.fetchpacket.valid && dec_fbundle.uops(w).valid &&
                                          !dec_finished_mask(w)
    decode_units(w).io.enq.uop         := dec_fbundle.uops(w).bits
    decode_units(w).io.status          := csr.io.status
    decode_units(w).io.csr_decode      <> csr.io.decode(w)
    decode_units(w).io.interrupt       := RegNext(csr.io.interrupt)
    decode_units(w).io.interrupt_cause := RegNext(csr.io.interrupt_cause)
    decode_units(w).io.fcsr_rm         := csr.io.fcsr_rm

    dec_uops(w) := decode_units(w).io.deq.uop
  }

  //-------------------------------------------------------------
  // FTQ GetPC Port Arbitration
  // 3 ports
  // port0 goes to flush,mispredict,xcpt
  // port1/2 goes to jmp unit

  val xcpt_pc_req = Wire(Decoupled(UInt(log2Ceil(ftqSz).W)))
  val mispredict_pc_req = Wire(Decoupled(UInt(log2Ceil(ftqSz).W)))
  val flush_pc_req = Wire(Decoupled(UInt(log2Ceil(ftqSz).W)))

  val ftq_arb = Module(new Arbiter(UInt(log2Ceil(ftqSz).W), 3))

  // Order by the oldest. Flushes come from the oldest instructions in pipe
  // Decoding exceptions come from youngest
  ftq_arb.io.in(0) <> flush_pc_req
  ftq_arb.io.in(1) <> mispredict_pc_req
  ftq_arb.io.in(2) <> xcpt_pc_req

  io.ifu.arb_ftq_reqs(0) := ftq_arb.io.out.bits
  ftq_arb.io.out.ready  := true.B

  val ftq_port_issued = Array.fill(2) { false.B }
  val ftq_port_addrs  = Array.fill(2) { 0.U(log2Ceil(ftqSz).W) }
  for (i <- 0 until aluWidth) {
    for (w <- 0 until 2) {
      val req = alu_exe_units(i).io_arb_ftq_reqs(w)
      var read_issued = false.B
      val data_sel = WireInit(0.U(2.W))
      for (j <- 0 until 2) {
        val issue_read = WireInit(false.B)
        val use_port = WireInit(false.B)
        when (!read_issued && !ftq_port_issued(j) && req.valid) {
          issue_read := true.B
          use_port := true.B
          data_sel := UIntToOH(j.U)
        }
        val was_port_issued_yet = ftq_port_issued(j)
        ftq_port_issued(j) = use_port || ftq_port_issued(j)
        ftq_port_addrs(j) = ftq_port_addrs(j) | Mux(was_port_issued_yet || !use_port, 0.U, req.bits)
        read_issued = issue_read || read_issued
      }
      req.ready := read_issued
      alu_exe_units(i).io_rrd_ftq_resps(w) := Mux(RegNext(data_sel(0)),
        io.ifu.rrd_ftq_resps(1), io.ifu.rrd_ftq_resps(2))

    }
  }
  for (j <- 0 until 2) {
    io.ifu.arb_ftq_reqs(j+1)  := ftq_port_addrs(j)
  }



  // Frontend Exception Requests
  val xcpt_idx = PriorityEncoder(dec_xcpts)
  xcpt_pc_req.valid    := dec_xcpts.reduce(_||_)
  xcpt_pc_req.bits     := dec_uops(xcpt_idx).ftq_idx
  rob.io.xcpt_fetch_pc := io.ifu.rrd_ftq_resps(0).pc

  flush_pc_req.valid   := rob.io.flush.valid
  flush_pc_req.bits    := rob.io.flush.bits.ftq_idx

  // Mispredict requests (to get the correct target)
  mispredict_pc_req.valid := mispredict_val
  mispredict_pc_req.bits  := oldest_mispredict_ftq_idx


  //-------------------------------------------------------------
  // Decode/Rename1 pipeline logic

  dec_xcpts := dec_uops zip dec_valids map {case (u,v) => u.exception && v}
  // Frontend exceptions need to shoot straight past dec/dis without stall, to match
  // the timing of the FTQ resp which provides badaddr.
  // Wait for pipeline to empty before letting an exception past dec
  val dec_prior_slot_valid = dec_valids.scanLeft(false.B) ((s,v) => s || v)
  val dec_xcpt_stall = (0 until coreWidth).map(w => dec_xcpts(w) &&
    (!rob.io.empty || !io.lsu.fencei_rdy || dec_prior_slot_valid(w) || dis_valids.reduce(_||_) || !xcpt_pc_req.ready)
  )
  // stall fetch/dcode because we ran out of branch tags
  val branch_mask_full = Wire(Vec(coreWidth, Bool()))

  val dec_hazards = (0 until coreWidth).map(w =>
                      dec_valids(w) &&
                      (  !dis_ready
                      || rob.io.rollback
                      || dec_xcpt_stall(w)
                      || branch_mask_full(w)
                      || brupdate.b1.mispredict_mask =/= 0.U
                      || brupdate.b2.mispredict
                      || io.ifu.redirect_flush))

  val dec_stalls = dec_hazards.scanLeft(false.B) ((s,h) => s || h).takeRight(coreWidth)
  dec_fire := (0 until coreWidth).map(w => dec_valids(w) && !dec_stalls(w))

  // all decoders are empty and ready for new instructions
  dec_ready := dec_fire.last

  when (dec_ready || io.ifu.redirect_flush) {
    dec_finished_mask := 0.U
  } .otherwise {
    dec_finished_mask := dec_fire.asUInt | dec_finished_mask
  }

  //-------------------------------------------------------------
  // Branch Mask Logic

  dec_brmask_logic.io.brupdate := brupdate
  dec_brmask_logic.io.flush_pipeline := RegNext(rob.io.flush.valid)

  for (w <- 0 until coreWidth) {
    dec_brmask_logic.io.is_branch(w) := !dec_finished_mask(w) && dec_uops(w).allocate_brtag
    dec_brmask_logic.io.will_fire(w) :=  dec_fire(w) &&
                                         dec_uops(w).allocate_brtag // ren, dis can back pressure us
    dec_uops(w).br_tag  := dec_brmask_logic.io.br_tag(w)
    dec_uops(w).br_mask := dec_brmask_logic.io.br_mask(w)
  }

  branch_mask_full := dec_brmask_logic.io.is_full

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Register Rename Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // Inputs
  for (rename <- rename_stages) {
    rename.io.kill := io.ifu.redirect_flush
    rename.io.brupdate := brupdate

    rename.io.debug_rob_empty := rob.io.empty

    rename.io.dec_fire := dec_fire
    rename.io.dec_uops := dec_uops

    rename.io.dis_fire := dis_fire
    rename.io.dis_ready := dis_ready

    rename.io.com_valids := rob.io.commit.valids
    rename.io.com_uops := rob.io.commit.uops
    rename.io.rollback := rob.io.rollback
  }



  // Outputs
  dis_uops := rename_stage.io.ren2_uops
  dis_valids := rename_stage.io.ren2_mask
  ren_stalls := rename_stage.io.ren_stalls


  /**
   * TODO This is a bit nasty, but it's currently necessary to
   * split the INT/FP rename pipelines into separate instantiations.
   * Won't have to do this anymore with a properly decoupled FP pipeline.
   */
  for (w <- 0 until coreWidth) {
    val i_uop     = rename_stage.io.ren2_uops(w)
    val f_uop     = fp_rename_stage.io.ren2_uops(w)
    val p_uop     = pred_rename_stage.io.ren2_uops(w)
    val imm_uop   = imm_rename_stage.io.ren2_uops(w)
    val f_stall   = fp_rename_stage.io.ren_stalls(w)
    val p_stall   = pred_rename_stage.io.ren_stalls(w)
    val imm_stall = imm_rename_stage.io.ren_stalls(w)

    // lrs1 can "pass through" to prs1. Used solely to index the csr file.
    dis_uops(w).prs1 := Mux(dis_uops(w).lrs1_rtype === RT_FLT, f_uop.prs1,
                        Mux(dis_uops(w).lrs1_rtype === RT_FIX, i_uop.prs1, dis_uops(w).lrs1))
    dis_uops(w).prs2 := Mux(dis_uops(w).lrs2_rtype === RT_FLT, f_uop.prs2, i_uop.prs2)
    dis_uops(w).prs3 := f_uop.prs3
    dis_uops(w).ppred := p_uop.ppred
    dis_uops(w).pdst := Mux(dis_uops(w).dst_rtype  === RT_FLT, f_uop.pdst,
                        Mux(dis_uops(w).dst_rtype  === RT_FIX, i_uop.pdst,
                        Mux(dis_uops(w).is_sfb_br            , p_uop.pdst,
                                                               random.LFSR(maxPregSz) // Random set the PDST so the ALU banked IQ is balanced
                        )))
    dis_uops(w).imm_sel := imm_uop.imm_sel
    dis_uops(w).pimm    := imm_uop.pimm

    dis_uops(w).stale_pdst := Mux(dis_uops(w).dst_rtype === RT_FLT, f_uop.stale_pdst, i_uop.stale_pdst)

    dis_uops(w).prs1_busy := i_uop.prs1_busy && (dis_uops(w).lrs1_rtype === RT_FIX) ||
                             f_uop.prs1_busy && (dis_uops(w).lrs1_rtype === RT_FLT)
    dis_uops(w).prs2_busy := i_uop.prs2_busy && (dis_uops(w).lrs2_rtype === RT_FIX) ||
                             f_uop.prs2_busy && (dis_uops(w).lrs2_rtype === RT_FLT)
    dis_uops(w).prs3_busy := f_uop.prs3_busy && dis_uops(w).frs3_en
    dis_uops(w).ppred_busy := p_uop.ppred_busy && dis_uops(w).is_sfb_shadow

    ren_stalls(w) := rename_stage.io.ren_stalls(w) || f_stall || p_stall || imm_stall
  }

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Dispatch Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  //-------------------------------------------------------------
  // Rename2/Dispatch pipeline logic

  val dis_prior_slot_valid = dis_valids.scanLeft(false.B) ((s,v) => s || v)
  val dis_prior_slot_unique = (dis_uops zip dis_valids).scanLeft(false.B) {case (s,(u,v)) => s || v && u.is_unique}
  val wait_for_empty_pipeline = (0 until coreWidth).map(w => (dis_uops(w).is_unique || !custom_csrs.enableOOO) &&
                                  (!rob.io.empty || !io.lsu.fencei_rdy || dis_prior_slot_valid(w)))
  val rocc_shim_busy = if (usingRoCC) !unq_exe_unit.io_rocc_core.get.rxq_empty else false.B
  val wait_for_rocc = (0 until coreWidth).map(w =>
                        (dis_uops(w).is_fence || dis_uops(w).is_fencei) && (io.rocc.busy || rocc_shim_busy))
  val rxq_full = if (usingRoCC) unq_exe_unit.io_rocc_core.get.rxq_full else false.B
  val block_rocc = (dis_uops zip dis_valids).map{case (u,v) => v && u.is_rocc}.scanLeft(rxq_full)(_||_)
  val dis_rocc_alloc_stall = (dis_uops.map(_.is_rocc) zip block_rocc) map {case (p,r) =>
                               if (usingRoCC) p && r else false.B}

  // Only 1 branch-tag allocating insruction allowed to proceed per cycle unless enableSuperscalarSnapshots
  // This reduces checkpointing complexity
  val block_brtag = (dis_uops zip dis_valids).map{case (u,v) => v && u.allocate_brtag}.scanLeft(false.B)(_||_)
  val brtag_stall = (dis_uops.map(_.allocate_brtag) zip block_brtag) map {case (p,r) =>
    if (enableSuperscalarSnapshots) false.B else (p && r) }
  val dis_hazards = (0 until coreWidth).map(w =>
                      dis_valids(w) &&
                      (  !rob.io.ready
                      || brtag_stall(w)
                      || ren_stalls(w)
                      || io.lsu.ldq_full(w) && dis_uops(w).uses_ldq
                      || io.lsu.stq_full(w) && dis_uops(w).uses_stq
                      || !dispatcher.io.ren_uops(w).ready
                      || wait_for_empty_pipeline(w)
                      || wait_for_rocc(w)
                      || dis_prior_slot_unique(w)
                      || dis_rocc_alloc_stall(w)
                      || brupdate.b1.mispredict_mask =/= 0.U
                      || brupdate.b2.mispredict
                      || io.ifu.redirect_flush))


  io.lsu.fence_dmem := (dis_valids zip wait_for_empty_pipeline).map {case (v,w) => v && w} .reduce(_||_)

  val dis_stalls = dis_hazards.scanLeft(false.B) ((s,h) => s || h).takeRight(coreWidth)
  dis_fire := dis_valids zip dis_stalls map {case (v,s) => v && !s}
  dis_ready := !dis_stalls.last

  //-------------------------------------------------------------
  // LDQ/STQ Allocation Logic

  for (w <- 0 until coreWidth) {
    // Dispatching instructions request load/store queue entries when they can proceed.
    dis_uops(w).ldq_idx := io.lsu.dis_ldq_idx(w)
    dis_uops(w).stq_idx := io.lsu.dis_stq_idx(w)
  }

  //-------------------------------------------------------------
  // Rob Allocation Logic

  rob.io.enq_valids := dis_fire
  rob.io.enq_uops   := dis_uops
  rob.io.enq_partial_stall := dis_stalls.last // TODO come up with better ROB compacting scheme.
  rob.io.debug_tsc := debug_tsc_reg
  rob.io.csr_stall := csr.io.csr_stall

  // Minor hack: ecall and breaks need to increment the FTQ deq ptr earlier than commit, since
  // they write their PC into the CSR the cycle before they commit.
  // Since these are also unique, increment the FTQ ptr when they are dispatched
  when (RegNext(dis_fire.reduce(_||_) && dis_uops(PriorityEncoder(dis_fire)).is_sys_pc2epc)) {
    io.ifu.commit.valid := true.B
    io.ifu.commit.bits  := RegNext(dis_uops(PriorityEncoder(dis_valids)).ftq_idx)
  }

  for (w <- 0 until coreWidth) {
    // note: this assumes uops haven't been shifted - there's a 1:1 match between PC's LSBs and "w" here
    // (thus the LSB of the rob_idx gives part of the PC)
    if (coreWidth == 1) {
      dis_uops(w).rob_idx := rob.io.rob_tail_idx
    } else {
      dis_uops(w).rob_idx := Cat(rob.io.rob_tail_idx >> log2Ceil(coreWidth).U,
                               w.U(log2Ceil(coreWidth).W))
    }
  }

  //-------------------------------------------------------------
  // RoCC allocation logic
  if (usingRoCC) {
    val rocc_unit = unq_exe_unit
    for (w <- 0 until coreWidth) {
      // We guarantee only decoding 1 RoCC instruction per cycle
      dis_uops(w).rxq_idx := rocc_unit.io_rocc_core.get.rxq_idx(w)
    }
  }

  //-------------------------------------------------------------
  // Dispatch to issue queues

  // Get uops from rename2
  for (w <- 0 until coreWidth) {
    dispatcher.io.ren_uops(w).valid := dis_fire(w)
    dispatcher.io.ren_uops(w).bits  := dis_uops(w)
  }

  var iu_idx = 0
  // Send dispatched uops to correct issue queues
  // Backpressure through dispatcher if necessary
  for (i <- 0 until issueParams.size) {
    if (issueParams(i).iqType == IQ_FP) {
      fp_pipeline.io.dis_uops <> dispatcher.io.dis_uops(i)
    } else if (issueParams(i).iqType == IQ_MEM) {
      mem_iss_unit.io.dis_uops <> dispatcher.io.dis_uops(i)
    } else if (issueParams(i).iqType == IQ_ALU) {
      alu_iss_unit.io.dis_uops <> dispatcher.io.dis_uops(i)
    } else if (issueParams(i).iqType == IQ_UNQ) {
      unq_iss_unit.io.dis_uops <> dispatcher.io.dis_uops(i)
    } else {
      require(false)
    }
  }

  //-------------------------------------------------------------
  // Write immediates, branches into immediate file
  for (w <- 0 until coreWidth) {
    val uop = RegNext(dis_uops(w))

    immregfile.io.write_ports(w).valid     := RegNext(dis_fire(w)) && !uop.imm_sel.isOneOf(IS_N, IS_SH)
    immregfile.io.write_ports(w).bits.addr := uop.pimm
    immregfile.io.write_ports(w).bits.data := uop.imm_packed

    bregfile.io.write_ports(w).valid               := RegNext(dis_fire(w)) && uop.allocate_brtag
    bregfile.io.write_ports(w).bits.addr           := uop.br_tag
    bregfile.io.write_ports(w).bits.data.ldq_idx   := uop.ldq_idx
    bregfile.io.write_ports(w).bits.data.stq_idx   := uop.stq_idx
    bregfile.io.write_ports(w).bits.data.rxq_idx   := uop.rxq_idx
  }


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Issue Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  var wu_idx = 0
  var wb_idx = 0
  var bypass_idx = 0
  for (i <- 0 until lsuWidth) {
    int_wakeups(wu_idx) := io.lsu.iwakeups(i)
    rob.io.wb_resps(wb_idx) := RegNext(UpdateBrMask(brupdate, RegNext(rob.io.flush.valid), io.lsu.iresp(i)))
    iregfile.io.write_ports(wb_idx).valid := RegNext(io.lsu.iresp(i).valid)
    iregfile.io.write_ports(wb_idx).bits.addr := RegNext(io.lsu.iresp(i).bits.uop.pdst)
    iregfile.io.write_ports(wb_idx).bits.data := RegNext(io.lsu.iresp(i).bits.data)

    int_bypasses(bypass_idx).valid := RegNext(io.lsu.iresp(i).valid)
    int_bypasses(bypass_idx).bits  := RegNext(io.lsu.iresp(i).bits)
    wu_idx += 1
    wb_idx += 1
    bypass_idx += 1
  }
  val ll_arb = Module(new Arbiter(new ExeUnitResp(xLen),
    1 + // Mul
    1 + // F2I
    (if (unq_exe_unit.hasDiv) 1 else 0) +
    (if (unq_exe_unit.hasCSR) 1 else 0) +
    (if (unq_exe_unit.hasRocc) 1 else 0)))

  var arb_idx = 0
  ll_arb.io.in(arb_idx).valid := unq_exe_unit.io_mul_resp.get.valid
  ll_arb.io.in(arb_idx).bits  := unq_exe_unit.io_mul_resp.get.bits
  arb_idx += 1
  ll_arb.io.in(arb_idx) <> fp_pipeline.io.to_int
  arb_idx += 1
  if (unq_exe_unit.hasDiv) {
    ll_arb.io.in(arb_idx) <> unq_exe_unit.io_div_resp.get
    arb_idx += 1
  }
  if (unq_exe_unit.hasCSR) {
    ll_arb.io.in(arb_idx).valid     := csr_resp.valid
    ll_arb.io.in(arb_idx).bits.uop  := csr_resp.bits.uop
    val rdata = WireInit(csr.io.rw.rdata)
    if (enableBPDHPMs) {
      var hpmcounter = CSRs.hpmcounter16
      var mhpmcounter = CSRs.mhpmcounter16
      for (i <- 0 until 5) {
        when (csr.io.rw.addr === hpmcounter.U || csr.io.rw.addr === mhpmcounter.U) {
          rdata := debug_brs(i)
        }
        hpmcounter = hpmcounter + 1
        mhpmcounter = mhpmcounter + 1
        when (csr.io.rw.addr === hpmcounter.U || csr.io.rw.addr === mhpmcounter.U) {
          rdata := debug_jals(i)
        }
        hpmcounter = hpmcounter + 1
        mhpmcounter = mhpmcounter + 1
        when (csr.io.rw.addr === hpmcounter.U || csr.io.rw.addr === mhpmcounter.U) {
          rdata := debug_jalrs(i)
        }
        hpmcounter = hpmcounter + 1
        mhpmcounter = mhpmcounter + 1
      }
    }
    ll_arb.io.in(arb_idx).bits.data := rdata
    ll_arb.io.in(arb_idx).bits.predicated   := false.B
    ll_arb.io.in(arb_idx).bits.fflags.valid := false.B
    ll_arb.io.in(arb_idx).bits.fflags.bits  := false.B
    assert(!(ll_arb.io.in(arb_idx).valid && !ll_arb.io.in(arb_idx).ready))
    arb_idx += 1
  }
  if (unq_exe_unit.hasRocc) {
    ll_arb.io.in(arb_idx) <> unq_exe_unit.io_rocc_resp.get
    arb_idx += 1
  }
  ll_arb.io.out.ready := true.B
  int_wakeups(wu_idx).valid := ll_arb.io.out.valid && ll_arb.io.out.bits.uop.dst_rtype === RT_FIX
  int_wakeups(wu_idx).bits.uop  := ll_arb.io.out.bits.uop
  int_wakeups(wu_idx).bits.speculative_mask := 0.U
  int_wakeups(wu_idx).bits.rebusy := false.B
  int_wakeups(wu_idx).bits.bypassable := false.B
  wu_idx += 1

  rob.io.wb_resps(wb_idx).valid  := RegNext(ll_arb.io.out.valid && !IsKilledByBranch(brupdate, RegNext(rob.io.flush.valid), ll_arb.io.out.bits))
  rob.io.wb_resps(wb_idx).bits   := RegNext(ll_arb.io.out.bits)

  iregfile.io.write_ports(wb_idx).valid     := ll_arb.io.out.valid && ll_arb.io.out.bits.uop.dst_rtype === RT_FIX
  iregfile.io.write_ports(wb_idx).bits.addr := ll_arb.io.out.bits.uop.pdst
  iregfile.io.write_ports(wb_idx).bits.data := ll_arb.io.out.bits.data
  wb_idx += 1


  // loop through each issue-port (exe_units are statically connected to an issue-port)
  for (i <- 0 until aluWidth) {
    val unit = alu_exe_units(i)
    val fast_wakeup = unit.io_fast_wakeup

    int_bypasses(bypass_idx).valid := unit.io_alu_resp.valid && unit.io_alu_resp.bits.uop.dst_rtype === RT_FIX
    int_bypasses(bypass_idx).bits  := unit.io_alu_resp.bits
    bypass_idx += 1

    int_wakeups(wu_idx) := fast_wakeup
    wu_idx += 1

    rob.io.wb_resps(wb_idx).valid  := RegNext(unit.io_alu_resp.valid && !IsKilledByBranch(brupdate, RegNext(rob.io.flush.valid), unit.io_alu_resp.bits))
    rob.io.wb_resps(wb_idx).bits   := RegNext(unit.io_alu_resp.bits)

    iregfile.io.write_ports(wb_idx).valid     := unit.io_alu_resp.valid && unit.io_alu_resp.bits.uop.dst_rtype === RT_FIX
    iregfile.io.write_ports(wb_idx).bits.addr := unit.io_alu_resp.bits.uop.pdst
    iregfile.io.write_ports(wb_idx).bits.data := unit.io_alu_resp.bits.data
    wb_idx += 1

    pred_wakeups(i) := unit.io_fast_pred_wakeup


  }
  require (wu_idx == numIntWakeups)
  require (wb_idx == numIrfWritePorts)

  // arb stage guarantees 1 preg writer per cycle
  val pregfile_write_valids = alu_exe_units.map(u => u.io_alu_resp.valid && u.io_alu_resp.bits.uop.is_sfb_br)
  assert(PopCount(pregfile_write_valids) <= 1.U)
  pregfile.io.write_ports(0).valid := pregfile_write_valids.reduce(_||_)
  pregfile.io.write_ports(0).bits.addr := Mux1H(pregfile_write_valids, alu_exe_units.map(_.io_alu_resp.bits.uop.pdst))
  pregfile.io.write_ports(0).bits.data := Mux1H(pregfile_write_valids, alu_exe_units.map(_.io_alu_resp.bits.data))

  // Connect the predicate wakeup port
  alu_iss_unit.io.pred_wakeup_port.valid := pred_wakeup.valid
  alu_iss_unit.io.pred_wakeup_port.bits  := pred_wakeup.bits.uop.pdst
  mem_iss_unit.io.pred_wakeup_port.valid := false.B
  mem_iss_unit.io.pred_wakeup_port.bits  := DontCare
  unq_iss_unit.io.pred_wakeup_port.valid := false.B
  unq_iss_unit.io.pred_wakeup_port.bits  := DontCare



  // ----------------------------------------------------------------
  // Connect the wakeup ports to the busy tables in the rename stages

  for ((renport, intport) <- rename_stage.io.wakeups zip int_wakeups) {
    renport <> intport
  }
  if (usingFPU) {
    fp_rename_stage.io.wakeups := fp_pipeline.io.wakeups
  }

  pred_rename_stage.io.wakeups(0) := pred_wakeup
  imm_rename_stage.io.wakeups := all_exe_units.map(_.io_rrd_immrf_wakeup)

  rename_stage.io.child_rebusys := alu_exe_units.map(_.io_child_rebusy).reduce(_|_)
  imm_rename_stage.io.child_rebusys := 0.U
  pred_rename_stage.io.child_rebusys := 0.U
  fp_rename_stage.io.child_rebusys := 0.U

  mem_iss_unit.io.fu_types := mem_exe_units.map(_.io_ready_fu_types)
  alu_iss_unit.io.fu_types := alu_exe_units.map(_.io_ready_fu_types)
  unq_iss_unit.io.fu_types := unq_exe_units.map(_.io_ready_fu_types)

  for (iss_unit <- Seq(mem_iss_unit, alu_iss_unit, unq_iss_unit)) {
    iss_unit.io.tsc_reg  := debug_tsc_reg
    iss_unit.io.brupdate := brupdate
    iss_unit.io.flush_pipeline := RegNext(rob.io.flush.valid)

    // Rebusy children of misspeculated wakeups
    iss_unit.io.child_rebusys := alu_exe_units.map(_.io_child_rebusy).reduce(_|_)

    iss_unit.io.wakeup_ports := int_wakeups

    iss_unit.io.rob_pnr_idx := rob.io.rob_pnr_idx
    iss_unit.io.rob_head    := rob.io.rob_head_idx
  }

  mem_iss_unit.io.squash_grant := (
    mem_exe_units.map(_.io_squash_iss).reduce(_||_) ||
    alu_exe_units.map(_.io_squash_iss).reduce(_||_) ||
    io.lsu.iwakeups.map(_.bits.rebusy).reduce(_||_)
  )
  unq_iss_unit.io.squash_grant := (
    unq_exe_units.map(_.io_squash_iss).reduce(_||_) ||
    alu_exe_units.map(_.io_squash_iss).reduce(_||_) ||
    io.lsu.iwakeups.map(_.bits.rebusy).reduce(_||_)
  )
  alu_iss_unit.io.squash_grant := (
    alu_exe_units.map(_.io_squash_iss).reduce(_||_) ||
    io.lsu.iwakeups.map(_.bits.rebusy).reduce(_||_)
  )

  mem_iss_unit.io.iss_uops zip mem_exe_units map { case (i, u) => u.io_iss_uop := i }
  alu_iss_unit.io.iss_uops zip alu_exe_units map { case (i, u) => u.io_iss_uop := i }
  unq_iss_unit.io.iss_uops zip unq_exe_units map { case (i, u) => u.io_iss_uop := i }

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Register Read Arbitrate Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------
  arb_idx = 0
  for ((unit, w) <- all_exe_units.zipWithIndex) {
    for (i <- 0 until unit.nReaders) {
      iregfile.io.arb_read_reqs(arb_idx) <> unit.io_arb_irf_reqs(i)
      arb_idx += 1
    }
    immregfile.io.arb_read_reqs(w) <> unit.io_arb_immrf_req
    unit.io_arb_rebusys := io.lsu.iwakeups
  }
  require(arb_idx == numIrfLogicalReadPorts)
  for ((unit, w) <- (alu_exe_units).zipWithIndex) {
    pregfile.io.arb_read_reqs(w) <> unit.io_arb_prf_req
    bregfile.io.arb_read_reqs(w) <> unit.io_arb_brf_req
  }


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Register Read Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // Register Read <- Issue (rrd <- iss)
  var rd_idx = 0
  for ((unit, w) <- all_exe_units.zipWithIndex) {
    for (i <- 0 until unit.nReaders) {
      unit.io_rrd_irf_resps(i) := iregfile.io.rrd_read_resps(rd_idx)
      rd_idx += 1
    }
    unit.io_rrd_immrf_resp := immregfile.io.rrd_read_resps(w)
    unit.io_rrd_irf_bypasses := int_bypasses
  }
  require (rd_idx == numIrfLogicalReadPorts)
  for ((unit, w) <- alu_exe_units.zipWithIndex) {
    unit.io_rrd_prf_resp := pregfile.io.rrd_read_resps(w)
    unit.io_rrd_brf_resp := bregfile.io.rrd_read_resps(w)
  }

  //-------------------------------------------------------------
  // Privileged Co-processor 0 Register File
  // Note: Normally this would be bad in that I'm writing state before
  // committing, so to get this to work I stall the entire pipeline for
  // CSR instructions so I never speculate these instructions.

  io.lsu.sfence := unq_exe_unit.io_sfence.get
  io.ifu.sfence := unq_exe_unit.io_sfence.get

  // for critical path reasons, we aren't zero'ing this out if resp is not valid
  csr.io.rw.addr        := csr_resp.bits.addr
  csr.io.rw.cmd         := CSR.maskCmd(csr_resp.valid, csr_resp.bits.uop.csr_cmd)
  csr.io.rw.wdata       := csr_resp.bits.data

  rob.io.csr_replay.valid := csr_resp.valid && csr.io.rw_stall
  rob.io.csr_replay.bits.uop := csr_resp.bits.uop
  rob.io.csr_replay.bits.cause := MINI_EXCEPTION_CSR_REPLAY
  rob.io.csr_replay.bits.badvaddr := DontCare

  // Extra I/O
  // Delay retire/exception 1 cycle
  csr.io.retire    := RegNext(PopCount(rob.io.commit.arch_valids.asUInt))
  csr.io.exception := RegNext(rob.io.com_xcpt.valid)
  // csr.io.pc used for setting EPC during exception or CSR.io.trace.

  csr.io.pc        := (boom.v4.util.AlignPCToBoundary(io.ifu.com_pc, icBlockBytes)
                     + RegNext(rob.io.com_xcpt.bits.pc_lob)
                     - Mux(RegNext(rob.io.com_xcpt.bits.edge_inst), 2.U, 0.U))
  // Cause not valid for for CALL or BREAKPOINTs (CSRFile will override it).
  csr.io.cause     := RegNext(rob.io.com_xcpt.bits.cause)
  csr.io.ungated_clock := clock

  val tval_valid = csr.io.exception &&
    csr.io.cause.isOneOf(
      //Causes.illegal_instruction.U, we currently only write 0x0 for illegal instructions
      Causes.breakpoint.U,
      Causes.misaligned_load.U,
      Causes.misaligned_store.U,
      Causes.load_access.U,
      Causes.store_access.U,
      Causes.fetch_access.U,
      Causes.load_page_fault.U,
      Causes.store_page_fault.U,
      Causes.fetch_page_fault.U)

  csr.io.tval := Mux(tval_valid,
    RegNext(encodeVirtualAddress(rob.io.com_xcpt.bits.badvaddr, rob.io.com_xcpt.bits.badvaddr)), 0.U)

  // TODO move this function to some central location (since this is used elsewhere).
  def encodeVirtualAddress(a0: UInt, ea: UInt) =
    if (vaddrBitsExtended == vaddrBits) {
      ea
    } else {
      // Efficient means to compress 64-bit VA into vaddrBits+1 bits.
      // (VA is bad if VA(vaddrBits) != VA(vaddrBits-1)).
      val a = a0.asSInt >> vaddrBits
      val msb = Mux(a === 0.S || a === -1.S, ea(vaddrBits), !ea(vaddrBits-1))
      Cat(msb, ea(vaddrBits-1,0))
    }

  // reading requires serializing the entire pipeline
  csr.io.fcsr_flags.valid := rob.io.commit.fflags.valid
  csr.io.fcsr_flags.bits  := rob.io.commit.fflags.bits
  csr.io.set_fs_dirty.get := rob.io.commit.fflags.valid

  all_exe_units.map(i => i.io_fcsr_rm := csr.io.fcsr_rm)
  io.fcsr_rm := csr.io.fcsr_rm

  fp_pipeline.io.fcsr_rm := csr.io.fcsr_rm

  csr.io.hartid := io.hartid
  csr.io.interrupts := io.interrupts

  // we do not support the H-extension
  csr.io.htval := DontCare
  csr.io.gva := DontCare

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Execute Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Load/Store Unit ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // enqueue basic load/store info in Decode
  for (w <- 0 until coreWidth) {
    io.lsu.dis_uops(w).valid := dis_fire(w)
    io.lsu.dis_uops(w).bits  := dis_uops(w)
  }

  // tell LSU about committing loads and stores to clear entries
  io.lsu.commit                  := rob.io.commit

  // tell LSU that it should fire a load that waits for the rob to clear
  io.lsu.commit_load_at_rob_head := rob.io.com_load_is_at_rob_head

  //com_xcpt.valid comes too early, will fight against a branch that resolves same cycle as an exception
  io.lsu.exception := RegNext(rob.io.flush.valid)

  // Handle Branch Mispeculations
  io.lsu.brupdate := brupdate
  io.lsu.rob_head_idx := rob.io.rob_head_idx
  io.lsu.rob_pnr_idx  := rob.io.rob_pnr_idx

  io.lsu.tsc_reg := debug_tsc_reg

  // Connect IFPU
  fp_pipeline.io.from_int  <> unq_exe_unit.io_ifpu_resp.get

  // Connect FLDs
  fp_pipeline.io.ll_wports <> io.lsu.fresp

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Commit Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  var cnt = numIrfWritePorts
  for (wb <- fp_pipeline.io.wb) {
    rob.io.wb_resps(cnt) := wb
    rob.io.wb_resps(cnt).bits.data := ieee(wb.bits.data)
    cnt += 1
  }

  require (cnt == rob.numWakeupPorts)

  // branch resolution
  rob.io.brupdate <> brupdate

  io.lsu.status := csr.io.status
  io.lsu.bp     := csr.io.bp
  io.lsu.mcontext := csr.io.mcontext
  io.lsu.scontext := csr.io.scontext


  all_exe_units.map(u => u.io_status := csr.io.status)
  fp_pipeline.io.status := csr.io.status

  // LSU <> ROB
  rob.io.lsu_clr_bsy    := io.lsu.clr_bsy
  rob.io.lsu_clr_unsafe := io.lsu.clr_unsafe
  rob.io.lxcpt          <> io.lsu.lxcpt

  assert (!(csr.io.singleStep), "[core] single-step is unsupported.")


  //-------------------------------------------------------------
  // **** Flush Pipeline ****
  //-------------------------------------------------------------
  // flush on exceptions, miniexeptions, and after some special instructions

  fp_pipeline.io.flush_pipeline := RegNext(rob.io.flush.valid)

  for (eu <- all_exe_units)
    eu.io_kill := RegNext(rob.io.flush.valid)


  assert (!(rob.io.com_xcpt.valid && !rob.io.flush.valid),
    "[core] exception occurred, but pipeline flush signal not set!")

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Outputs to the External World ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // detect pipeline freezes and throw error
  val idle_cycles = freechips.rocketchip.util.WideCounter(32)
  when (rob.io.commit.valids.asUInt.orR ||
        csr.io.csr_stall ||
        io.rocc.busy ||
        reset.asBool) {
    idle_cycles := 0.U
  }
  assert (!(idle_cycles.value(PlusArg("boom_timeout", 13, width=5))), "Pipeline has hung.")

  fp_pipeline.io.debug_tsc_reg := debug_tsc_reg


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Handle Cycle-by-Cycle Printouts ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------


  if (COMMIT_LOG_PRINTF) {
    var new_commit_cnt = 0.U

    for (w <- 0 until coreWidth) {
      val priv = ShiftRegister(csr.io.status.prv, 2) // erets change the privilege. Get the old one

      // To allow for diffs against spike :/
      def printf_inst(uop: MicroOp) = {
        when (uop.is_rvc) {
          printf("(0x%x)", uop.debug_inst(15,0))
        } .otherwise {
          printf("(0x%x)", uop.debug_inst)
        }
      }

      when (rob.io.commit.arch_valids(w)) {
        printf("%d 0x%x ",
          priv,
          Sext(rob.io.commit.uops(w).debug_pc(vaddrBits-1,0), xLen))
        printf_inst(rob.io.commit.uops(w))
        when (rob.io.commit.uops(w).dst_rtype === RT_FIX && rob.io.commit.uops(w).ldst =/= 0.U) {
          printf(" x%d 0x%x\n",
            rob.io.commit.uops(w).ldst,
            rob.io.commit.debug_wdata(w))
        } .elsewhen (rob.io.commit.uops(w).dst_rtype === RT_FLT) {
          printf(" f%d 0x%x\n",
            rob.io.commit.uops(w).ldst,
            rob.io.commit.debug_wdata(w))
        } .otherwise {
          printf("\n")
        }
      }
    }
  } else if (BRANCH_PRINTF) {
    val debug_ghist = RegInit(0.U(globalHistoryLength.W))
    when (rob.io.flush.valid && FlushTypes.useCsrEvec(rob.io.flush.bits.flush_typ)) {
      debug_ghist := 0.U
    }

    var new_ghist = debug_ghist

    for (w <- 0 until coreWidth) {
      when (rob.io.commit.arch_valids(w) &&
        (rob.io.commit.uops(w).is_br || rob.io.commit.uops(w).is_jal || rob.io.commit.uops(w).is_jalr)) {
        // for (i <- 0 until globalHistoryLength) {
        //   printf("%x", new_ghist(globalHistoryLength-i-1))
        // }
        // printf("\n")
        printf("%x %x %x %x %x %x\n",
          rob.io.commit.uops(w).debug_fsrc, rob.io.commit.uops(w).taken,
          rob.io.commit.uops(w).is_br, rob.io.commit.uops(w).is_jal,
          rob.io.commit.uops(w).is_jalr, Sext(rob.io.commit.uops(w).debug_pc(vaddrBits-1,0), xLen))

      }
      new_ghist = Mux(rob.io.commit.arch_valids(w) && rob.io.commit.uops(w).is_br,
        Mux(rob.io.commit.uops(w).taken, new_ghist << 1 | 1.U(1.W), new_ghist << 1),
        new_ghist)
    }
    debug_ghist := new_ghist
  }

  // TODO: Does anyone want this debugging functionality?
  val coreMonitorBundle = Wire(new CoreMonitorBundle(xLen, fLen))
  coreMonitorBundle := DontCare
  coreMonitorBundle.clock  := clock
  coreMonitorBundle.reset  := reset


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // Page Table Walker

  io.ptw.ptbr       := csr.io.ptbr
  io.ptw.status     := csr.io.status
  io.ptw.pmp        := csr.io.pmp
  io.ptw.sfence     := io.ifu.sfence

  //-------------------------------------------------------------
  //-------------------------------------------------------------

  io.rocc := DontCare
  io.rocc.exception := csr.io.exception && csr.io.status.xs.orR
  io.rocc.csrs <> csr.io.roccCSRs
  if (usingRoCC) {
    val rocc_unit = unq_exe_unit
    rocc_unit.io_rocc_core.get.rocc         <> io.rocc
    rocc_unit.io_rocc_core.get.dis_uops     := dis_uops
    rocc_unit.io_rocc_core.get.rob_head_idx := rob.io.rob_head_idx
    rocc_unit.io_rocc_core.get.rob_pnr_idx  := rob.io.rob_pnr_idx

    for (w <- 0 until coreWidth) {
      rocc_unit.io_rocc_core.get.dis_rocc_vals(w) := (
        dis_fire(w) &&
        dis_uops(w).is_rocc &&
        !dis_uops(w).exception
      )
    }
  }

  io.trace := DontCare
  io.trace.time := csr.io.time
  io.trace.insns map (t => t.valid := false.B)
  io.trace.custom.get.asInstanceOf[BoomTraceBundle].rob_empty := rob.io.empty

  if (trace) {
    for (w <- 0 until coreWidth) {
      // Delay the trace so we have a cycle to pull PCs out of the FTQ
      io.trace.insns(w).valid      := RegNext(rob.io.commit.arch_valids(w))

      // Recalculate the PC
      io.ifu.debug_ftq_idx(w) := rob.io.commit.uops(w).ftq_idx
      val iaddr = (AlignPCToBoundary(io.ifu.debug_fetch_pc(w), icBlockBytes)
                   + RegNext(rob.io.commit.uops(w).pc_lob)
                   - Mux(RegNext(rob.io.commit.uops(w).edge_inst), 2.U, 0.U))(vaddrBits-1,0)
      io.trace.insns(w).iaddr      := Sext(iaddr, xLen)

      def getInst(uop: MicroOp, inst: UInt): UInt = {
        Mux(uop.is_rvc, Cat(0.U(16.W), inst(15,0)), inst)
      }

      def getWdata(uop: MicroOp, wdata: UInt): UInt = {
        Mux((uop.dst_rtype === RT_FIX && uop.ldst =/= 0.U) || (uop.dst_rtype === RT_FLT), wdata, 0.U(xLen.W))
      }

      // use debug_insts instead of uop.debug_inst to use the rob's debug_inst_mem
      // note: rob.debug_insts comes 1 cycle later
      io.trace.insns(w).insn       := getInst(RegNext(rob.io.commit.uops(w)), rob.io.commit.debug_insts(w))
      io.trace.insns(w).wdata.map { _ := RegNext(getWdata(rob.io.commit.uops(w), rob.io.commit.debug_wdata(w))) }

      // Comment out this assert because it blows up FPGA synth-asserts
      // This tests correctedness of the debug_inst mem
      // when (RegNext(rob.io.commit.valids(w))) {
      //   assert(rob.io.commit.debug_insts(w) === RegNext(rob.io.commit.uops(w).debug_inst))
      // }
      // This tests correctedness of recovering pcs through ftq debug ports
      // when (RegNext(rob.io.commit.valids(w))) {
      //   assert(Sext(io.trace.insns(w).iaddr, xLen) ===
      //     RegNext(Sext(rob.io.commit.uops(w).debug_pc(vaddrBits-1,0), xLen)))
      // }

      // These csr signals do not exactly match up with the ROB commit signals.
      io.trace.insns(w).priv       := RegNext(Cat(RegNext(RegNext(csr.io.status.debug)), csr.io.status.prv))
      // Can determine if it is an interrupt or not based on the MSB of the cause
      io.trace.insns(w).exception  := RegNext(rob.io.com_xcpt.valid && !rob.io.com_xcpt.bits.cause(xLen - 1)) && (w == 0).B
      io.trace.insns(w).interrupt  := RegNext(rob.io.com_xcpt.valid && rob.io.com_xcpt.bits.cause(xLen - 1)) && (w == 0).B
      io.trace.insns(w).cause      := RegNext(rob.io.com_xcpt.bits.cause)
      io.trace.insns(w).tval       := RegNext(csr.io.tval)
    }
    dontTouch(io.trace)
  } else {
    io.ifu.debug_ftq_idx := DontCare
  }
}
