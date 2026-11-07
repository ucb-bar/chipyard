//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISCV Processor Issue Slot Logic
//--------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// Note: stores (and AMOs) are "broken down" into 2 uops, but stored within a single issue-slot.
// TODO XXX make a separate issueSlot for MemoryIssueSlots, and only they break apart stores.
// TODO Disable ldspec for FP queue.

package boom.v4.exu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import boom.v4.common._
import boom.v4.util._

class IssueSlotIO(val numWakeupPorts: Int)(implicit p: Parameters) extends BoomBundle
{
  val valid         = Output(Bool())
  val will_be_valid = Output(Bool()) // TODO code review, do we need this signal so explicitely?
  val request       = Output(Bool())
  val grant         = Input(Bool())
  val iss_uop       = Output(new MicroOp())

  val in_uop        = Input(Valid(new MicroOp())) // if valid, this WILL overwrite an entry!
  val out_uop       = Output(new MicroOp())


  val brupdate      = Input(new BrUpdateInfo())
  val kill          = Input(Bool()) // pipeline flush
  val clear         = Input(Bool()) // entry being moved elsewhere (not mutually exclusive with grant)

  val squash_grant  = Input(Bool())

  val wakeup_ports  = Flipped(Vec(numWakeupPorts, Valid(new Wakeup)))
  val pred_wakeup_port = Flipped(Valid(UInt(log2Ceil(ftqSz).W)))
  val child_rebusys = Input(UInt(aluWidth.W))
}

class IssueSlot(val numWakeupPorts: Int, val isMem: Boolean, val isFp: Boolean)(implicit p: Parameters)
  extends BoomModule
{
  val io = IO(new IssueSlotIO(numWakeupPorts))


  val slot_valid = RegInit(false.B)
  val slot_uop = Reg(new MicroOp())

  val next_valid = WireInit(slot_valid)
  val next_uop   = WireInit(UpdateBrMask(io.brupdate, slot_uop))

  val killed = IsKilledByBranch(io.brupdate, io.kill, slot_uop)

  io.valid         := slot_valid
  io.out_uop       := next_uop
  io.will_be_valid := next_valid && !killed

  when (io.kill) {
    slot_valid := false.B
  } .elsewhen (io.in_uop.valid) {
    slot_valid := true.B
  } .elsewhen (io.clear) {
    slot_valid := false.B
  } .otherwise {
    slot_valid := next_valid && !killed
  }

  when (io.in_uop.valid) {
    slot_uop := io.in_uop.bits
    assert (!slot_valid || io.clear || io.kill)
  } .otherwise {
    slot_uop := next_uop
  }


  // Wakeups
  next_uop.iw_p1_bypass_hint := false.B
  next_uop.iw_p2_bypass_hint := false.B
  next_uop.iw_p3_bypass_hint := false.B
  next_uop.iw_p1_speculative_child := 0.U
  next_uop.iw_p2_speculative_child := 0.U

  val rebusied_prs1 = WireInit(false.B)
  val rebusied_prs2 = WireInit(false.B)
  val rebusied = rebusied_prs1 || rebusied_prs2

  val prs1_matches = io.wakeup_ports.map { w => w.bits.uop.pdst === slot_uop.prs1 }
  val prs2_matches = io.wakeup_ports.map { w => w.bits.uop.pdst === slot_uop.prs2 }
  val prs3_matches = io.wakeup_ports.map { w => w.bits.uop.pdst === slot_uop.prs3 }
  val prs1_wakeups = (io.wakeup_ports zip prs1_matches).map { case (w,m) => w.valid && m }
  val prs2_wakeups = (io.wakeup_ports zip prs2_matches).map { case (w,m) => w.valid && m }
  val prs3_wakeups = (io.wakeup_ports zip prs3_matches).map { case (w,m) => w.valid && m }
  val prs1_rebusys = (io.wakeup_ports zip prs1_matches).map { case (w,m) => w.bits.rebusy && m }
  val prs2_rebusys = (io.wakeup_ports zip prs2_matches).map { case (w,m) => w.bits.rebusy && m }
  val bypassables  = io.wakeup_ports.map { w => w.bits.bypassable }
  val speculative_masks = io.wakeup_ports.map { w => w.bits.speculative_mask }

  when (prs1_wakeups.reduce(_||_)) {
    next_uop.prs1_busy := false.B
    next_uop.iw_p1_speculative_child := Mux1H(prs1_wakeups, speculative_masks)
    next_uop.iw_p1_bypass_hint := Mux1H(prs1_wakeups, bypassables)
  }
  when ((prs1_rebusys.reduce(_||_) || ((io.child_rebusys & slot_uop.iw_p1_speculative_child) =/= 0.U)) &&
    slot_uop.lrs1_rtype === RT_FIX) {
    next_uop.prs1_busy := true.B
    rebusied_prs1 := true.B
  }
  when (prs2_wakeups.reduce(_||_)) {
    next_uop.prs2_busy := false.B
    next_uop.iw_p2_speculative_child := Mux1H(prs2_wakeups, speculative_masks)
    next_uop.iw_p2_bypass_hint := Mux1H(prs2_wakeups, bypassables)
  }
  when ((prs2_rebusys.reduce(_||_) || ((io.child_rebusys & slot_uop.iw_p2_speculative_child) =/= 0.U)) &&
    slot_uop.lrs2_rtype === RT_FIX) {
    next_uop.prs2_busy := true.B
    rebusied_prs2 := true.B
  }

  when (prs3_wakeups.reduce(_||_)) {
    next_uop.prs3_busy := false.B
    next_uop.iw_p3_bypass_hint := Mux1H(prs3_wakeups, bypassables)
  }
  when (io.pred_wakeup_port.valid && io.pred_wakeup_port.bits === slot_uop.ppred) {
    next_uop.ppred_busy := false.B
  }

  val iss_ready  = !slot_uop.prs1_busy && !slot_uop.prs2_busy && !(slot_uop.ppred_busy && enableSFBOpt.B) && !(slot_uop.prs3_busy && isFp.B)
  val agen_ready = (slot_uop.fu_code(FC_AGEN) && !slot_uop.prs1_busy && !(slot_uop.ppred_busy && enableSFBOpt.B) && isMem.B)
  val dgen_ready = (slot_uop.fu_code(FC_DGEN) && !slot_uop.prs2_busy && !(slot_uop.ppred_busy && enableSFBOpt.B) && isMem.B)

  io.request := slot_valid && !slot_uop.iw_issued && (
    iss_ready || agen_ready || dgen_ready
  )

  io.iss_uop  := slot_uop

  // Update state for current micro-op based on grant


  next_uop.iw_issued := false.B
  next_uop.iw_issued_partial_agen := false.B
  next_uop.iw_issued_partial_dgen := false.B
  when (io.grant && !io.squash_grant) {
    next_uop.iw_issued := true.B
  }

  if (isMem) {
    when (slot_uop.fu_code(FC_AGEN) && slot_uop.fu_code(FC_DGEN)) {
      when (agen_ready) {
        // Issue the AGEN, next slot entry is a DGEN
        when (io.grant && !io.squash_grant) {
          next_uop.iw_issued_partial_agen := true.B
        }
        io.iss_uop.fu_code(FC_AGEN) := true.B
        io.iss_uop.fu_code(FC_DGEN) := false.B
      } .otherwise {
        // Issue the DGEN, next slot entry is the AGEN
        when (io.grant && !io.squash_grant) {
          next_uop.iw_issued_partial_dgen := true.B
        }
        io.iss_uop.fu_code(FC_AGEN) := false.B
        io.iss_uop.fu_code(FC_DGEN) := true.B
        io.iss_uop.imm_sel    := IS_N
        io.iss_uop.prs1       := slot_uop.prs2
        io.iss_uop.lrs1_rtype := slot_uop.lrs2_rtype
        io.iss_uop.iw_p1_bypass_hint := slot_uop.iw_p2_bypass_hint
      }
    } .elsewhen (slot_uop.fu_code(FC_DGEN)) {
      io.iss_uop.imm_sel    := IS_N
      io.iss_uop.prs1       := slot_uop.prs2
      io.iss_uop.lrs1_rtype := slot_uop.lrs2_rtype
      io.iss_uop.iw_p1_bypass_hint := slot_uop.iw_p2_bypass_hint
    }
    io.iss_uop.lrs2_rtype := RT_X
    io.iss_uop.prs2       := io.iss_uop.prs1 // helps with DCE
  }

  when (slot_valid && slot_uop.iw_issued) {
    next_valid := rebusied
    if (isMem) {
      when (slot_uop.iw_issued_partial_agen) {
        next_valid := true.B
        when (!rebusied_prs1) {
          next_uop.fu_code(FC_AGEN) := false.B
          next_uop.fu_code(FC_DGEN) := true.B
        }
      } .elsewhen (slot_uop.iw_issued_partial_dgen) {
        next_valid := true.B
        when (!rebusied_prs2) {
          next_uop.fu_code(FC_AGEN) := true.B
          next_uop.fu_code(FC_DGEN) := false.B
        }
      }

    }
  }




}
