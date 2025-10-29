//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Floating Point Datapath Pipeline
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.exu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.rocket
import freechips.rocketchip.tile

import boom.v3.exu.FUConstants._
import boom.v3.common._
import boom.v3.util.{BoomCoreStringPrefix}

/**
 * Top level datapath that wraps the floating point issue window, regfile, and arithmetic units.
 */
class FpPipeline(implicit p: Parameters) extends BoomModule with tile.HasFPUParameters
{
  val fpIssueParams = issueParams.find(_.iqType == IQT_FP.litValue).get
  val dispatchWidth = fpIssueParams.dispatchWidth
  val numLlPorts = memWidth
  val numWakeupPorts = fpIssueParams.issueWidth + numLlPorts
  val fpPregSz = log2Ceil(numFpPhysRegs)

  val io = IO(new Bundle {
    val brupdate         = Input(new BrUpdateInfo())
    val flush_pipeline   = Input(Bool())
    val fcsr_rm          = Input(UInt(width=freechips.rocketchip.tile.FPConstants.RM_SZ.W))
    val status           = Input(new freechips.rocketchip.rocket.MStatus())

    val dis_uops         = Vec(dispatchWidth, Flipped(Decoupled(new MicroOp)))

    // +1 for recoding.
    val ll_wports        = Flipped(Vec(memWidth, Decoupled(new ExeUnitResp(fLen+1))))// from memory unit
    val from_int         = Flipped(Decoupled(new ExeUnitResp(fLen+1)))// from integer RF
    val to_sdq           = Decoupled(new ExeUnitResp(fLen))           // to Load/Store Unit
    val to_int           = Decoupled(new ExeUnitResp(xLen))           // to integer RF

    val wakeups          = Vec(numWakeupPorts, Valid(new ExeUnitResp(fLen+1)))
    val wb_valids        = Input(Vec(numWakeupPorts, Bool()))
    val wb_pdsts         = Input(Vec(numWakeupPorts, UInt(width=fpPregSz.W)))

    val debug_tsc_reg    = Input(UInt(width=xLen.W))
    val debug_wb_wdata   = Output(Vec(numWakeupPorts, UInt((fLen+1).W)))
  })

  //**********************************
  // construct all of the modules

  val exe_units      = new boom.v3.exu.ExecutionUnits(fpu=true)
  val issue_unit     = Module(new IssueUnitCollapsing(
                         issueParams.find(_.iqType == IQT_FP.litValue).get,
                         numWakeupPorts))
  issue_unit.suggestName("fp_issue_unit")
  val fregfile       = Module(new RegisterFileSynthesizable(numFpPhysRegs,
                         exe_units.numFrfReadPorts,
                         exe_units.numFrfWritePorts + memWidth,
                         fLen+1,
                         // No bypassing for any FP units, + memWidth for ll_wb
                         Seq.fill(exe_units.numFrfWritePorts + memWidth){ false }
                         ))
  val fregister_read = Module(new RegisterRead(
                         issue_unit.issueWidth,
                         exe_units.withFilter(_.readsFrf).map(_.supportedFuncUnits).toSeq,
                         exe_units.numFrfReadPorts,
                         exe_units.withFilter(_.readsFrf).map(x => 3).toSeq,
                         0, // No bypass for FP
                         0,
                         fLen+1))

  require (exe_units.count(_.readsFrf) == issue_unit.issueWidth)
  require (exe_units.numFrfWritePorts + numLlPorts == numWakeupPorts)

  //*************************************************************
  // Issue window logic

  val iss_valids = Wire(Vec(exe_units.numFrfReaders, Bool()))
  val iss_uops   = Wire(Vec(exe_units.numFrfReaders, new MicroOp()))

  issue_unit.io.tsc_reg := io.debug_tsc_reg
  issue_unit.io.brupdate := io.brupdate
  issue_unit.io.flush_pipeline := io.flush_pipeline
  // Don't support ld-hit speculation to FP window.
  for (w <- 0 until memWidth) {
    issue_unit.io.spec_ld_wakeup(w).valid := false.B
    issue_unit.io.spec_ld_wakeup(w).bits := 0.U
  }
  issue_unit.io.ld_miss := false.B

  require (exe_units.numTotalBypassPorts == 0)

  //-------------------------------------------------------------
  // **** Dispatch Stage ****
  //-------------------------------------------------------------

  // Input (Dispatch)
  for (w <- 0 until dispatchWidth) {
    issue_unit.io.dis_uops(w) <> io.dis_uops(w)
  }

  //-------------------------------------------------------------
  // **** Issue Stage ****
  //-------------------------------------------------------------

  // Output (Issue)
  for (i <- 0 until issue_unit.issueWidth) {
    iss_valids(i) := issue_unit.io.iss_valids(i)
    iss_uops(i) := issue_unit.io.iss_uops(i)

    var fu_types = exe_units(i).io.fu_types
    if (exe_units(i).supportedFuncUnits.fdiv) {
      val fdiv_issued = iss_valids(i) && iss_uops(i).fu_code_is(FU_FDV)
      fu_types = fu_types & RegNext(~Mux(fdiv_issued, FU_FDV, 0.U))
    }
    issue_unit.io.fu_types(i) := fu_types

    require (exe_units(i).readsFrf)
  }

  // Wakeup
  for ((writeback, issue_wakeup) <- io.wakeups zip issue_unit.io.wakeup_ports) {
    issue_wakeup.valid := writeback.valid
    issue_wakeup.bits.pdst  := writeback.bits.uop.pdst
    issue_wakeup.bits.poisoned := false.B
  }
  issue_unit.io.pred_wakeup_port.valid := false.B
  issue_unit.io.pred_wakeup_port.bits := DontCare

  //-------------------------------------------------------------
  // **** Register Read Stage ****
  //-------------------------------------------------------------

  // Register Read <- Issue (rrd <- iss)
  fregister_read.io.rf_read_ports <> fregfile.io.read_ports
  fregister_read.io.prf_read_ports map { port => port.data := false.B }

  fregister_read.io.iss_valids <> iss_valids
  fregister_read.io.iss_uops := iss_uops

  fregister_read.io.brupdate := io.brupdate
  fregister_read.io.kill := io.flush_pipeline

  //-------------------------------------------------------------
  // **** Execute Stage ****
  //-------------------------------------------------------------

  exe_units.map(_.io.brupdate := io.brupdate)

  for ((ex,w) <- exe_units.withFilter(_.readsFrf).map(x=>x).zipWithIndex) {
    ex.io.req <> fregister_read.io.exe_reqs(w)
    require (!ex.bypassable)
  }
  require (exe_units.numTotalBypassPorts == 0)

  //-------------------------------------------------------------
  // **** Writeback Stage ****
  //-------------------------------------------------------------

  val ll_wbarb = Module(new Arbiter(new ExeUnitResp(fLen+1), 2))


  // Hookup load writeback -- and recode FP values.
  ll_wbarb.io.in(0) <> io.ll_wports(0)
  ll_wbarb.io.in(0).bits.data := recode(io.ll_wports(0).bits.data,
                                        io.ll_wports(0).bits.uop.mem_size =/= 2.U)

  val ifpu_resp = io.from_int
  ll_wbarb.io.in(1) <> ifpu_resp


  // Cut up critical path by delaying the write by a cycle.
  // Wakeup signal is sent on cycle S0, write is now delayed until end of S1,
  // but Issue happens on S1 and RegRead doesn't happen until S2 so we're safe.
  fregfile.io.write_ports(0) := RegNext(WritePort(ll_wbarb.io.out, fpregSz, fLen+1, RT_FLT))

  assert (ll_wbarb.io.in(0).ready) // never backpressure the memory unit.
  when (ifpu_resp.valid) { assert (ifpu_resp.bits.uop.rf_wen && ifpu_resp.bits.uop.dst_rtype === RT_FLT) }

  var w_cnt = 1
  for (i <- 1 until memWidth) {
    fregfile.io.write_ports(w_cnt) := RegNext(WritePort(io.ll_wports(i), fpregSz, fLen+1, RT_FLT))
    fregfile.io.write_ports(w_cnt).bits.data := RegNext(recode(io.ll_wports(i).bits.data,
                                                               io.ll_wports(i).bits.uop.mem_size =/= 2.U))
    w_cnt += 1
  }
  for (eu <- exe_units) {
    if (eu.writesFrf) {
      fregfile.io.write_ports(w_cnt).valid     := eu.io.fresp.valid && eu.io.fresp.bits.uop.rf_wen
      fregfile.io.write_ports(w_cnt).bits.addr := eu.io.fresp.bits.uop.pdst
      fregfile.io.write_ports(w_cnt).bits.data := eu.io.fresp.bits.data
      eu.io.fresp.ready                        := true.B
      when (eu.io.fresp.valid) {
        assert(eu.io.fresp.ready, "No backpressuring the FPU")
        assert(eu.io.fresp.bits.uop.rf_wen, "rf_wen must be high here")
        assert(eu.io.fresp.bits.uop.dst_rtype === RT_FLT, "wb type must be FLT for fpu")
      }
      w_cnt += 1
    }
  }
  require (w_cnt == fregfile.io.write_ports.length)

  val fpiu_unit = exe_units.fpiu_unit
  val fpiu_is_sdq = fpiu_unit.io.ll_iresp.bits.uop.uopc === uopSTA
  io.to_int.valid := fpiu_unit.io.ll_iresp.fire && !fpiu_is_sdq
  io.to_sdq.valid := fpiu_unit.io.ll_iresp.fire &&  fpiu_is_sdq
  io.to_int.bits  := fpiu_unit.io.ll_iresp.bits
  io.to_sdq.bits  := fpiu_unit.io.ll_iresp.bits
  fpiu_unit.io.ll_iresp.ready := io.to_sdq.ready && io.to_int.ready

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // **** Commit Stage ****
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  io.wakeups(0).valid := ll_wbarb.io.out.valid
  io.wakeups(0).bits := ll_wbarb.io.out.bits
  ll_wbarb.io.out.ready := true.B

  w_cnt = 1
  for (i <- 1 until memWidth) {
    io.wakeups(w_cnt) := io.ll_wports(i)
    io.wakeups(w_cnt).bits.data := recode(io.ll_wports(i).bits.data,
      io.ll_wports(i).bits.uop.mem_size =/= 2.U)
    w_cnt += 1
  }
  for (eu <- exe_units) {
    if (eu.writesFrf) {
      val exe_resp = eu.io.fresp
      val wb_uop = eu.io.fresp.bits.uop
      val wport = io.wakeups(w_cnt)
      wport.valid := exe_resp.valid && wb_uop.dst_rtype === RT_FLT
      wport.bits := exe_resp.bits

      w_cnt += 1

      assert(!(exe_resp.valid && wb_uop.uses_ldq))
      assert(!(exe_resp.valid && wb_uop.uses_stq))
      assert(!(exe_resp.valid && wb_uop.is_amo))
    }
  }

  for ((wdata, wakeup) <- io.debug_wb_wdata zip io.wakeups) {
    wdata := ieee(wakeup.bits.data)
  }

  exe_units.map(_.io.fcsr_rm := io.fcsr_rm)
  exe_units.map(_.io.status := io.status)

  //-------------------------------------------------------------
  // **** Flush Pipeline ****
  //-------------------------------------------------------------
  // flush on exceptions, miniexeptions, and after some special instructions

  for (w <- 0 until exe_units.length) {
    exe_units(w).io.req.bits.kill := io.flush_pipeline
  }

  override def toString: String =
    (BoomCoreStringPrefix("===FP Pipeline===") + "\n"
    + fregfile.toString
    + BoomCoreStringPrefix(
      "Num Wakeup Ports      : " + numWakeupPorts,
      "Num Bypass Ports      : " + exe_units.numTotalBypassPorts))
}
