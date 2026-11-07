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
import boom.v4.util._

class IssueUnitCollapsing(
  params: IssueParams,
  numWakeupPorts: Int)
  (implicit p: Parameters)
  extends IssueUnit(params, numWakeupPorts)
{

  //-------------------------------------------------------------
  // Set up the dispatch uops
  // special case "storing" 2 uops within one issue slot.

  val dis_uops = Array.fill(dispatchWidth) {Wire(new MicroOp())}
  for (w <- 0 until dispatchWidth) {
    dis_uops(w) := io.dis_uops(w).bits
    dis_uops(w).iw_issued := false.B
    dis_uops(w).iw_issued_partial_agen := false.B
    dis_uops(w).iw_issued_partial_dgen := false.B
    dis_uops(w).iw_p1_bypass_hint := false.B
    dis_uops(w).iw_p2_bypass_hint := false.B
    dis_uops(w).iw_p3_bypass_hint := false.B

    // Handle wakeups on dispatch
    val prs1_matches = io.wakeup_ports.map { wu => wu.bits.uop.pdst === io.dis_uops(w).bits.prs1 }
    val prs2_matches = io.wakeup_ports.map { wu => wu.bits.uop.pdst === io.dis_uops(w).bits.prs2 }
    val prs3_matches = io.wakeup_ports.map { wu => wu.bits.uop.pdst === io.dis_uops(w).bits.prs3 }
    val prs1_wakeups = (io.wakeup_ports zip prs1_matches).map { case (wu,m) => wu.valid && m }
    val prs2_wakeups = (io.wakeup_ports zip prs2_matches).map { case (wu,m) => wu.valid && m }
    val prs3_wakeups = (io.wakeup_ports zip prs3_matches).map { case (wu,m) => wu.valid && m }
    val prs1_rebusys = (io.wakeup_ports zip prs1_matches).map { case (wu,m) => wu.bits.rebusy && m }
    val prs2_rebusys = (io.wakeup_ports zip prs2_matches).map { case (wu,m) => wu.bits.rebusy && m }
    val bypassables  = io.wakeup_ports.map { wu => wu.bits.bypassable }
    val speculative_masks = io.wakeup_ports.map { wu => wu.bits.speculative_mask }



    when (prs1_wakeups.reduce(_||_)) {
      dis_uops(w).prs1_busy := false.B
      dis_uops(w).iw_p1_speculative_child := Mux1H(prs1_wakeups, speculative_masks)
      dis_uops(w).iw_p1_bypass_hint := Mux1H(prs1_wakeups, bypassables)
    }
    when (prs1_rebusys.reduce(_||_) || ((io.child_rebusys & io.dis_uops(w).bits.iw_p1_speculative_child) =/= 0.U)) {
      dis_uops(w).prs1_busy := io.dis_uops(w).bits.lrs1_rtype === RT_FIX
    }
    when (prs2_wakeups.reduce(_||_)) {
      dis_uops(w).prs2_busy := false.B
      dis_uops(w).iw_p2_speculative_child := Mux1H(prs2_wakeups, speculative_masks)
      dis_uops(w).iw_p2_bypass_hint := Mux1H(prs2_wakeups, bypassables)
    }

    when (prs2_rebusys.reduce(_||_) || ((io.child_rebusys & io.dis_uops(w).bits.iw_p2_speculative_child) =/= 0.U)) {
      dis_uops(w).prs2_busy := io.dis_uops(w).bits.lrs2_rtype === RT_FIX
    }


    when (prs3_wakeups.reduce(_||_)) {
      dis_uops(w).prs3_busy := false.B
      dis_uops(w).iw_p3_bypass_hint := Mux1H(prs3_wakeups, bypassables)
    }
    when (io.pred_wakeup_port.valid && io.pred_wakeup_port.bits === io.dis_uops(w).bits.ppred) {
      dis_uops(w).ppred_busy := false.B
    }


    if (iqType == IQ_UNQ) {
      when (io.dis_uops(w).bits.fu_code(FC_I2F)) {
        dis_uops(w).prs2 := Cat(io.dis_uops(w).bits.fp_rm, io.dis_uops(w).bits.fp_typ)
      }
      when (io.dis_uops(w).bits.is_sfence) {
        dis_uops(w).pimm := io.dis_uops(w).bits.mem_size
      }
    }

    if (iqType == IQ_MEM) {
      // For store addr gen for FP, rs2 is the FP register, and we don't wait for that here
      when (io.dis_uops(w).bits.uses_stq && io.dis_uops(w).bits.lrs2_rtype === RT_FLT) {
        dis_uops(w).lrs2_rtype := RT_X
        dis_uops(w).prs2_busy  := false.B
      }
      dis_uops(w).prs3_busy := false.B
    } else if (iqType == IQ_FP) {
      // FP "StoreAddrGen" is really storeDataGen, and rs1 is the integer address register
      when (io.dis_uops(w).bits.uses_stq) {
        dis_uops(w).lrs1_rtype := RT_X
        dis_uops(w).prs1_busy  := false.B
      }
    }

    if (iqType != IQ_ALU) {
      assert(!(io.dis_uops(w).bits.ppred_busy && io.dis_uops(w).valid))
      dis_uops(w).ppred_busy := false.B
    }

  }



  //-------------------------------------------------------------
  // Issue Table

  val slots = (0 until numIssueSlots) map { w => Module(new IssueSlot(numWakeupPorts, iqType == IQ_MEM, iqType == IQ_FP)) }
  val issue_slots = VecInit(slots.map(_.io))

  for (i <- 0 until numIssueSlots) {
    issue_slots(i).wakeup_ports     := io.wakeup_ports
    issue_slots(i).pred_wakeup_port := io.pred_wakeup_port
    issue_slots(i).child_rebusys    := io.child_rebusys
    issue_slots(i).squash_grant     := io.squash_grant
    issue_slots(i).brupdate         := io.brupdate
    issue_slots(i).kill             := io.flush_pipeline
  }


  for (w <- 0 until issueWidth) {
    io.iss_uops(w).valid := false.B
  }


  //-------------------------------------------------------------

  assert (PopCount(issue_slots.map(s => s.grant)) <= issueWidth.U, "[issue] window giving out too many grants.")



  //-------------------------------------------------------------
  // Figure out how much to shift entries by

  // Slow slots only shift 1 per cycle, these reduce critical path
  val nSlowSlots = params.numSlowEntries
  // Fast slots can shift up to dispatchWidth per cycle, so they can handle full dispatch throughput
  val nFastSlots = numIssueSlots - nSlowSlots

  require (nFastSlots >= dispatchWidth)
  require (nFastSlots <= numIssueSlots)


  val vacants = issue_slots.map(s => !(s.valid)) ++ io.dis_uops.map(_.valid).map(!_.asBool)
  val shamts_oh = Wire(Vec(numIssueSlots+dispatchWidth, UInt(width=dispatchWidth.W)))
  // track how many to shift up this entry by by counting previous vacant spots
  def SaturatingCounterOH(count_oh:UInt, inc: Bool, max: Int): UInt = {
     val next = Wire(UInt(width=max.W))
     next := count_oh
     when (count_oh === 0.U && inc) {
       next := 1.U
     } .elsewhen (!count_oh(max-1) && inc) {
       next := (count_oh << 1.U)
     }
     next
  }
  shamts_oh(0) := 0.U
  for (i <- 1 until numIssueSlots + dispatchWidth) {
    val shift = if (i < nSlowSlots) (dispatchWidth min 1 + (i * (dispatchWidth-1)/nSlowSlots).toInt) else dispatchWidth
    if (dispatchWidth == 1 || shift == 1) {
      shamts_oh(i) := vacants.take(i).reduce(_||_)
    } else {
      shamts_oh(i) := SaturatingCounterOH(shamts_oh(i-1), vacants(i-1), shift)
    }
  }

 //-------------------------------------------------------------

  // which entries' uops will still be next cycle? (not being issued and vacated)
  val will_be_valid = (0 until numIssueSlots).map(i => issue_slots(i).will_be_valid) ++
                      (0 until dispatchWidth).map(i => io.dis_uops(i).valid &&
                                                        !dis_uops(i).exception &&
                                                        !dis_uops(i).is_fence &&
                                                        !dis_uops(i).is_fencei)

  val uops = issue_slots.map(s=>s.out_uop) ++ dis_uops.map(s=>s)
  for (i <- 0 until numIssueSlots) {
    issue_slots(i).in_uop.valid := false.B
    issue_slots(i).in_uop.bits  := uops(i+1)
    for (j <- 1 to dispatchWidth by 1) {
      when (shamts_oh(i+j) === (1 << (j-1)).U) {
        issue_slots(i).in_uop.valid := will_be_valid(i+j)
        issue_slots(i).in_uop.bits  := uops(i+j)
      }
    }
    issue_slots(i).clear        := shamts_oh(i) =/= 0.U
  }

  //-------------------------------------------------------------
  // Dispatch/Entry Logic
  // did we find a spot to slide the new dispatched uops into?

  // Only look at the fast slots to determine readiness to dispatch.
  // Slow slot do not compact fast enough to make this calculation valid
  val is_available = Reg(Vec(nFastSlots, Bool()))
  is_available := VecInit((nSlowSlots until numIssueSlots).map(i =>
    (!issue_slots(i).will_be_valid || issue_slots(i).clear) && !(issue_slots(i).in_uop.valid)))
  for (w <- 0 until dispatchWidth) {
    io.dis_uops(w).ready := RegNext(PopCount(is_available) > w.U(log2Ceil(nFastSlots).W) + PopCount(io.dis_uops.map(_.fire)))
    // io.dis_uops(w).ready := RegNext(PopCount(will_be_available) > w.U)
    assert (!io.dis_uops(w).ready || (shamts_oh(w+numIssueSlots) >> w) =/= 0.U)
  }

  //-------------------------------------------------------------
  // Issue Select Logic
  val requests = issue_slots.map(s => s.request)
  val port_issued = Array.fill(issueWidth){Bool()}
  for (w <- 0 until issueWidth) {
    port_issued(w) = false.B
  }

  val iss_select_mask = Array.ofDim[Boolean](issueWidth, numIssueSlots)
  if (params.useFullIssueSel) {
    for (w <- 0 until issueWidth) {
      for (i <- 0 until numIssueSlots) {
        iss_select_mask(w)(i) = true
      }
    }
  } else {
    for (w <- 0 until issueWidth) {
      for (i <- 0 until numIssueSlots) {
        iss_select_mask(w)(i) = (w % 2) == (i % 2)
      }
      iss_select_mask(w)(0) = true
    }
  }

  val iss_uops = Wire(Vec(issueWidth, Valid(new MicroOp)))
  for (w <- 0 until issueWidth) {
    iss_uops(w).valid := false.B
    iss_uops(w).bits  := DontCare
  }

  for (i <- 0 until numIssueSlots) {
    issue_slots(i).grant := false.B
    var uop_issued = false.B

    // when enabling a conservative implementation of speculative non-interference, delay issue of instructions that have not passed PnR
    val issue_slot_past_pnr = IsOlder(issue_slots(i).iss_uop.rob_idx, io.rob_pnr_idx, io.rob_head) | (issue_slots(i).iss_uop.rob_idx === io.rob_pnr_idx)
    val issue_past_pnr = MuxCase(true.B, Array(
      issue_slots(i).iss_uop.is_br -> issue_slot_past_pnr,
      issue_slots(i).iss_uop.is_jal -> issue_slot_past_pnr,
      issue_slots(i).iss_uop.is_jalr -> issue_slot_past_pnr,
      issue_slots(i).iss_uop.fu_code(FC_MUL) -> issue_slot_past_pnr,
      issue_slots(i).iss_uop.fu_code(FC_DIV) -> issue_slot_past_pnr,
      issue_slots(i).iss_uop.fu_code(FC_AGEN) -> (issue_slots(i).iss_uop.rob_idx === io.rob_head)
    ))
    val can_issue_sni = issue_past_pnr | !enableConservativeSNI.B

    for (w <- 0 until issueWidth) {
      val fu_code_match = (issue_slots(i).iss_uop.fu_code zip io.fu_types(w)).map {
        case (r,c) => r && c
      } .reduce(_||_)

      val can_allocate = fu_code_match && iss_select_mask(w)(i).B && can_issue_sni

      when (requests(i) && !uop_issued && can_allocate && !port_issued(w)) {
        issue_slots(i).grant := true.B
        iss_uops(w).valid := true.B
        iss_uops(w).bits  := issue_slots(i).iss_uop
      }
      val was_port_issued_yet = port_issued(w)
      port_issued(w) = (requests(i) && !uop_issued && can_allocate) | port_issued(w)
      uop_issued = (requests(i) && can_allocate && !was_port_issued_yet) | uop_issued
    }
  }
  io.iss_uops := iss_uops
  when (io.squash_grant) {
    io.iss_uops.map { u => u.valid := false.B }
  }
}
