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
import freechips.rocketchip.util.{Str}

import boom.v4.common._
import boom.v4.util.{BoolToChar}

case class IssueParams(
  dispatchWidth: Int = 1,
  issueWidth: Int = 1,
  numEntries: Int = 8,
  useFullIssueSel: Boolean = true,
  numSlowEntries: Int = 0,
  useMatrixIssue: Boolean = true,
  iqType: Int
)


abstract class IssueUnit(
  val params: IssueParams,
  val numWakeupPorts: Int
)(implicit p: Parameters)
  extends BoomModule
{
  val numIssueSlots = params.numEntries
  val issueWidth = params.issueWidth
  val iqType = params.iqType
  val dispatchWidth = params.dispatchWidth

  val io = IO(new Bundle {
    val dis_uops         = Vec(dispatchWidth, Flipped(Decoupled(new MicroOp)))

    val iss_uops         = Output(Vec(issueWidth, Valid(new MicroOp())))
    val wakeup_ports     = Flipped(Vec(numWakeupPorts, Valid(new Wakeup)))
    val pred_wakeup_port = Flipped(Valid(UInt(log2Ceil(ftqSz).W)))

    val child_rebusys    = Input(UInt(aluWidth.W))

    // tell the issue unit what each execution pipeline has in terms of functional units
    val fu_types         = Input(Vec(issueWidth, Vec(FC_SZ, Bool())))

    val brupdate         = Input(new BrUpdateInfo())
    val flush_pipeline   = Input(Bool())
    val squash_grant     = Input(Bool())

    val tsc_reg          = Input(UInt(xLen.W))

    // For the speculative non-interference (SNI) implementation
    val rob_head = Input(UInt(robAddrSz.W))
    val rob_pnr_idx = Input(UInt(robAddrSz.W))
  })


  //-------------------------------------------------------------


  def getType: String =
    if (iqType == IQ_ALU) "alu"
    else if (iqType == IQ_MEM) "mem"
    else if (iqType == IQ_FP) " fp"
    else if (iqType == IQ_UNQ) "unique"
    else "unknown"
}

object IssueUnit
{
  def apply(params: IssueParams, numWakeupPorts: Int, useColumnIssueUnit: Boolean, useSingleWideDispatch: Boolean)(implicit p: Parameters): IssueUnit = {
    if (useColumnIssueUnit)
      Module(new IssueUnitBanked(params, numWakeupPorts, useSingleWideDispatch))
    else
      if (params.useMatrixIssue) {
        Module(new IssueUnitAgeMatrix(params, numWakeupPorts))        
      } else {
        Module(new IssueUnitCollapsing(params, numWakeupPorts))        
      }
  }

}
