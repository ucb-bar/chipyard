//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISCV Processor Issue Logic
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.exu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.Str

import FUConstants._
import boom.v3.common._

/**
 * Specific type of issue unit
 *
 * @param params issue queue params
 * @param numWakeupPorts number of wakeup ports for the issue queue
 */
class IssueUnitStatic(
  params: IssueParams,
  numWakeupPorts: Int)
  (implicit p: Parameters)
  extends IssueUnit(params.numEntries, params.issueWidth, numWakeupPorts, params.iqType, params.dispatchWidth)
{
  //-------------------------------------------------------------
  // Issue Table

  val entry_wen_oh = VecInit(Seq.fill(numIssueSlots){ Wire(Bits(dispatchWidth.W)) })
  for (i <- 0 until numIssueSlots) {
    issue_slots(i).in_uop.valid := entry_wen_oh(i).orR
    issue_slots(i).in_uop.bits  := Mux1H(entry_wen_oh(i), dis_uops)
    issue_slots(i).clear        := false.B
  }

  //-------------------------------------------------------------
  // Dispatch/Entry Logic
  // find a slot to enter a new dispatched instruction

  val entry_wen_oh_array = Array.fill(numIssueSlots,dispatchWidth){false.B}
  var allocated = VecInit(Seq.fill(dispatchWidth){false.B}) // did an instruction find an issue width?

  for (i <- 0 until numIssueSlots) {
    var next_allocated = Wire(Vec(dispatchWidth, Bool()))
    var can_allocate = !(issue_slots(i).valid)

    for (w <- 0 until dispatchWidth) {
      entry_wen_oh_array(i)(w) = can_allocate && !(allocated(w))

      next_allocated(w) := can_allocate | allocated(w)
      can_allocate = can_allocate && allocated(w)
    }

    allocated = next_allocated
  }

  // if we can find an issue slot, do we actually need it?
  // also, translate from Scala data structures to Chisel Vecs
  for (i <- 0 until numIssueSlots) {
    val temp_uop_val = Wire(Vec(dispatchWidth, Bool()))

    for (w <- 0 until dispatchWidth) {
      // TODO add ctrl bit for "allocates iss_slot"
      temp_uop_val(w) := io.dis_uops(w).valid &&
                         !dis_uops(w).exception &&
                         !dis_uops(w).is_fence &&
                         !dis_uops(w).is_fencei &&
                         entry_wen_oh_array(i)(w)
    }
    entry_wen_oh(i) := temp_uop_val.asUInt
  }

  for (w <- 0 until dispatchWidth) {
    io.dis_uops(w).ready := allocated(w)
  }

  //-------------------------------------------------------------
  // Issue Select Logic

  for (w <- 0 until issueWidth) {
    io.iss_valids(w) := false.B
    io.iss_uops(w)   := NullMicroOp
    // unsure if this is overkill
    io.iss_uops(w).prs1 := 0.U
    io.iss_uops(w).prs2 := 0.U
    io.iss_uops(w).prs3 := 0.U
    io.iss_uops(w).lrs1_rtype := RT_X
    io.iss_uops(w).lrs2_rtype := RT_X
  }

  // TODO can we use flatten to get an array of bools on issue_slot(*).request?
  val lo_request_not_satisfied = Array.fill(numIssueSlots){Bool()}
  val hi_request_not_satisfied = Array.fill(numIssueSlots){Bool()}

  for (i <- 0 until numIssueSlots) {
    lo_request_not_satisfied(i) = issue_slots(i).request
    hi_request_not_satisfied(i) = issue_slots(i).request_hp
    issue_slots(i).grant := false.B // default
  }

  for (w <- 0 until issueWidth) {
    var port_issued = false.B

    // first look for high priority requests
    for (i <- 0 until numIssueSlots) {
      val can_allocate = (issue_slots(i).uop.fu_code & io.fu_types(w)) =/= 0.U

      when (hi_request_not_satisfied(i) && can_allocate && !port_issued) {
        issue_slots(i).grant := true.B
        io.iss_valids(w)     := true.B
        io.iss_uops(w)       := issue_slots(i).uop
      }

      val port_already_in_use     = port_issued
      port_issued                 = (hi_request_not_satisfied(i) && can_allocate) | port_issued
      // deassert lo_request if hi_request is 1.
      lo_request_not_satisfied(i) = (lo_request_not_satisfied(i) && !hi_request_not_satisfied(i))
      // if request is 0, stay 0. only stay 1 if request is true and can't allocate
      hi_request_not_satisfied(i) = (hi_request_not_satisfied(i) && (!can_allocate || port_already_in_use))
    }

    // now look for low priority requests
    for (i <- 0 until numIssueSlots) {
      val can_allocate = (issue_slots(i).uop.fu_code & io.fu_types(w)) =/= 0.U

      when (lo_request_not_satisfied(i) && can_allocate && !port_issued) {
        issue_slots(i).grant := true.B
        io.iss_valids(w)     := true.B
        io.iss_uops(w)       := issue_slots(i).uop
      }

      val port_already_in_use     = port_issued
      port_issued                 = (lo_request_not_satisfied(i) && can_allocate) | port_issued
      // if request is 0, stay 0. only stay 1 if request is true and can't allocate or port already in use
      lo_request_not_satisfied(i) = (lo_request_not_satisfied(i) && (!can_allocate || port_already_in_use))
    }
  }
}
