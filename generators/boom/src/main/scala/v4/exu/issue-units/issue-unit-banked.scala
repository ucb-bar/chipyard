//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISCV Processor Issue Logic
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v4.exu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.Str

import boom.v4.common._

class IssueUnitBanked(
  params: IssueParams,
  numWakeupPorts: Int,
  singleWideDispatch: Boolean
)(implicit p: Parameters)
  extends IssueUnit(params, numWakeupPorts)
{
  val innerParams = params.copy(issueWidth = 1, dispatchWidth = if (singleWideDispatch) 1 else params.dispatchWidth)

  val issue_units = (0 until params.issueWidth).map { w =>
    val u = if (innerParams.useMatrixIssue) {
      Module(new IssueUnitAgeMatrix(innerParams, numWakeupPorts)).suggestName(s"col_${w}")      
    } else {
      Module(new IssueUnitCollapsing(innerParams, numWakeupPorts)).suggestName(s"col_${w}")      
    }
    u.io.wakeup_ports := io.wakeup_ports
    u.io.pred_wakeup_port := io.pred_wakeup_port
    u.io.child_rebusys := io.child_rebusys
    u.io.brupdate := io.brupdate
    u.io.flush_pipeline := io.flush_pipeline
    u.io.squash_grant := io.squash_grant
    u.io.tsc_reg := io.tsc_reg
    // for SNI
    u.io.rob_head := io.rob_head
    u.io.rob_pnr_idx := io.rob_pnr_idx

    u.io.fu_types(0) := io.fu_types(w)
    u
  }



  if (singleWideDispatch) {
    require (params.dispatchWidth == params.issueWidth)
    require (enableColumnALUWrites)
    require (params.iqType == IQ_ALU)
    require (isPow2(params.issueWidth))
    val col_sels = (0 until params.dispatchWidth).map { w => io.dis_uops(w).bits.dis_col_sel }


    val col_sels_t = (0 until params.issueWidth).map { i => VecInit(col_sels.map(_(i))) }
    for (w <- 0 until params.dispatchWidth) {
      io.dis_uops(w).ready := (VecInit(issue_units.map(_.io.dis_uops(0).ready)).asUInt & col_sels(w)) =/= 0.U
    }
    for (i <- 0 until params.issueWidth) {
      val u_sel = (col_sels_t(i) zip io.dis_uops).map({case (s,u) => s && u.valid})

      issue_units(i).io.dis_uops(0).valid := u_sel.reduce(_||_)
      issue_units(i).io.dis_uops(0).bits  := Mux1H(u_sel, io.dis_uops).bits
      assert(PopCount(u_sel) <= 1.U)
    }
  } else {
    val col_sels = (0 until params.dispatchWidth).map { w =>
      if (enableColumnALUWrites) {
        require (params.iqType == IQ_ALU)
        require (isPow2(params.issueWidth))
        UIntToOH(io.dis_uops(w).bits.pdst(log2Ceil(params.issueWidth)-1,0))
      } else {
        val sel = RegInit((1 << (w % params.issueWidth)).U(params.issueWidth.W))
        sel := (sel << 1) | sel(params.issueWidth-1)
        sel
      }
    }

    for (w <- 0 until params.dispatchWidth) {
      io.dis_uops(w).ready := (VecInit(issue_units.map(_.io.dis_uops(w).ready)).asUInt & col_sels(w)) =/= 0.U
      for (i <- 0 until params.issueWidth) {
        issue_units(i).io.dis_uops(w).valid := col_sels(w)(i) && io.dis_uops(w).valid
        issue_units(i).io.dis_uops(w).bits  := io.dis_uops(w).bits
      }
    }
  }


  for (i <- 0 until params.issueWidth) {
    io.iss_uops(i) := issue_units(i).io.iss_uops(0)
  }
}
