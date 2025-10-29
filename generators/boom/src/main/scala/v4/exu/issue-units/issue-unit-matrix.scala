package boom.v4.exu

import chisel3._
import chisel3.util._
import chisel3.experimental.hierarchy.{Definition, Instance, instantiable, public}

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.Str

import boom.v4.common._
import boom.v4.util._

class IssueUnitAgeMatrix(
  params: IssueParams,
  numWakeupPorts: Int
)(implicit p: Parameters)
    extends IssueUnit(params, numWakeupPorts) {
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

  // memorize which indices are going to be available for dispatch next cycle.

  val slots = (0 until numIssueSlots) map { w => Module(new IssueSlot(numWakeupPorts, iqType == IQ_MEM, iqType == IQ_FP)) }
  val issue_slots = VecInit(slots.map(_.io))



  for (i <- 0 until numIssueSlots) {
    issue_slots(i).wakeup_ports     := io.wakeup_ports
    issue_slots(i).pred_wakeup_port := io.pred_wakeup_port
    issue_slots(i).child_rebusys    := io.child_rebusys
    issue_slots(i).squash_grant     := io.squash_grant
    issue_slots(i).brupdate         := io.brupdate
    issue_slots(i).kill             := io.flush_pipeline

    // in the matrix-based implementation, we will never clear the issue slots
    issue_slots(i).clear            := false.B
  }
  for (w <- 0 until issueWidth) {
    io.iss_uops(w).valid := false.B
  }
  assert (PopCount(issue_slots.map(s => s.grant)) <= issueWidth.U, "[issue] window giving out too many grants.")



  // Dispatch Logic
  // This is the relatively naive (combinatorial) implementation of dispatch logic, where it searches for empty entries and dispatch into it
  val slots_empty = (0 until numIssueSlots).map(i => !issue_slots(i).will_be_valid && !issue_slots(i).valid)
  // dis_valids no exception
  val dis_readys = WireDefault(VecInit.fill(dispatchWidth)(false.B))
  // Chisel quirk here I guess... 
  val dis_indices = WireDefault(VecInit.fill(dispatchWidth)(0.U(log2Ceil(numIssueSlots).W)))  
  val dis_valids = (0 until dispatchWidth).map(i => io.dis_uops(i).valid && !dis_uops(i).exception && !dis_uops(i).is_fence && !dis_uops(i).is_fencei)

  var dis_scan = 0.U
  for (i <- 0 until numIssueSlots) {
    when (dis_scan < dispatchWidth.U && slots_empty(i)) {
      dis_readys(dis_scan) := true.B
      dis_indices(dis_scan) := i.U
    }
    dis_scan = Mux(dis_scan < dispatchWidth.U && slots_empty(i), dis_scan + 1.U, dis_scan)
  }

  for (w <- 0 until numIssueSlots) {
    issue_slots(w).in_uop.valid := false.B
    issue_slots(w).in_uop.bits  := DontCare
    for (i <- 0 until dispatchWidth) {
      io.dis_uops(i).ready := dis_readys(i)
      // when (dis_valids(i) && dis_readys(i) && (dis_indices(i) === w.U)) {
      //   issue_slots(w).in_uop.valid := true.B
      //   issue_slots(w).in_uop.bits  := dis_uops(i)
      // }
      when (dis_valids(i) && dis_readys(i)) {
        issue_slots(dis_indices(i)).in_uop.valid := true.B
        issue_slots(dis_indices(i)).in_uop.bits  := dis_uops(i)
      }      
    }
  }



  

  // age matrix for tracking
  //  val slots_age_matrix = RegInit( VecInit(Seq.fill(numIssueSlots)(Vec(numIssueSlots, false.B))))
  val slots_age_matrix = RegInit(VecInit.tabulate(numIssueSlots, numIssueSlots) { (_, _) => false.B})
  
  val slots_issue = VecInit((0 until dispatchWidth).map(i => io.dis_uops(i).valid))

  val slots_valids = WireDefault(VecInit(slots.map(_.io.valid)))
  val slots_valid_alloc = WireDefault(VecInit(slots.map(s => !s.io.valid & s.io.will_be_valid)))
  val slots_valid_dealloc = WireDefault(VecInit(slots.map(s => s.io.valid & !s.io.will_be_valid)))

  // age matrix logic
  for (i <- 0 until numIssueSlots) {
    // allocation of issue slot
    when (slots_valid_alloc(i)) {
      slots_age_matrix(i) := slots_valids
      // when deallocation
    }.elsewhen (slots_valid_dealloc(i)) {
      // fill the age matrix with 1 so that it's the youngest
      slots_age_matrix(i) := VecInit.fill(numIssueSlots)(true.B)
      // and zero out this entry in all other age tags
      // an alt design is to leave this as is and mask it with the valids 
      for (j <- 0 until numIssueSlots) {
        slots_age_matrix(j)(i) := false.B
      }
    }
  }

  // ready to issue calculation
  val iss_ready = Wire(Vec(issueWidth, Vec(numIssueSlots, Bool())))

  for (i <- 0 until issueWidth) {
    for (j <- 0 until numIssueSlots) {
      // a single issue is connected to a single exe_unit
      // i.e. will not trigger multiple issue port matching to the same fu_code_match
       val fu_code_match = (issue_slots(j).iss_uop.fu_code zip io.fu_types(i)).map {
        case (r,c) => r && c
      }.reduce(_||_)
     
 //     val fu_code_match = (io.fu_types(i).asUInt & issue_slots(j).iss_uop.fu_code.asUInt).orR
      iss_ready(i)(j) := fu_code_match & issue_slots(j).request
    }
  }

  
  // issue logic
  // Compute the wiring of the mux before, and connect them predicated on the age calculation signal
  val iss_signals = Wire(Vec(issueWidth, Valid(UInt(log2Ceil(numIssueSlots).W))))
  val iss_uops = Wire(Vec(issueWidth, Valid(new MicroOp)))

  if (!params.useFullIssueSel) {
  
    //    This is the case where a very PD scalable algorithm is used
    //  val issue_age_mask = VecInit.tabulate(issueWidth)(_ => slots_age_matrix)
    val issue_oldest_oh = Wire(Vec(issueWidth, Vec(numIssueSlots, Bool())))
    for (i <- 0 until issueWidth) {
      for (j <- 0 until numIssueSlots) {
      // issue_age_mask(i)(j) := slots_age_matrix(i)(j) & iss_ready(i)
      // to make something 1, something has to be valid, and there can be no older valid items in the queue
        issue_oldest_oh(i)(j) := !((slots_age_matrix(i).asUInt & iss_ready(i).asUInt).orR) && iss_ready(i)(j)
      }
    // if there is an issue ready, then the issue oldest one hot must have at least 1
      assert(!iss_ready(i).asUInt.orR | issue_oldest_oh(i).asUInt.orR)
      assert(PopCount(issue_oldest_oh(i)) <= dispatchWidth.U)
    }


    val oldest_iss = Wire(Vec(issueWidth, Valid(UInt(log2Ceil(numIssueSlots).W))))

// age based approach
// Microseconds for one run through Dhrystone: 288
// Dhrystones per Second:                      3465
// mcycle = 144425
// minstret = 186031
    for (i <- 0 until issueWidth) {
      val (old_iss_valid, old_iss_index) = ParallelFindOne(issue_oldest_oh(i).asUInt, i % 2 == 0)
      oldest_iss(i).valid := old_iss_valid
      oldest_iss(i).bits  := old_iss_index
    // maybe find multiple?
    val compute_valid_mask = WireDefault(iss_ready(i))
      for (j <- 0 until i) {
        when (iss_signals(j).valid) {
          compute_valid_mask(iss_signals(j).bits) := false.B
          when (oldest_iss(i).bits === iss_signals(j).bits) {
            oldest_iss(i).valid := false.B
          }
        }
      }
      when (old_iss_valid) {
        compute_valid_mask(old_iss_index) := false.B
      } // when found the oldest issue, only find valid from non-valid entries. Maybe not carry this across issue ports?
        val (val_iss_valid, val_iss_index) = ParallelFindOne(compute_valid_mask.asUInt, i%2 == 1)
      iss_signals(i).valid := oldest_iss(i).valid | val_iss_valid
      iss_signals(i).bits := Mux(oldest_iss(i).valid, oldest_iss(i).bits, val_iss_index)
      for (j <- 0 until i) {
        assert(!(iss_signals(i).valid & iss_signals(j).valid) | (iss_signals(i).bits =/= iss_signals(j).bits)) // no two entries are the same
      }

    }

  } else {

    val issued_index = Wire(Vec(issueWidth, Valid(UInt(log2Ceil(numIssueSlots).W))))
  // The most ideal, almost non-synthesizable case
  // This case only takes 129683 cycles, while the original collapsing takes 129933
  // Each issue slot tries to find its oldest instruction that can be issued
    for (i <- 0 until issueWidth) {
      issued_index(i).valid := false.B
      issued_index(i).bits  := DontCare
      for (j <- 0 until numIssueSlots) {
        val entry_valid = iss_ready(i)(j)
        val oldest      = !((slots_age_matrix(j).asUInt & iss_ready(i).asUInt).orR)
      // an entry j has to be valid and j is older than current issued index
        when (entry_valid && oldest) {
          issued_index(i).valid := true.B
          issued_index(i).bits  := j.U
        }
      }
      when (issued_index(i).valid) {
        for (j <- (i+1) until issueWidth) {
          iss_ready(j)(issued_index(i).bits) := false.B // don't issue this entry in the future
        }
      }
    }

    iss_signals := issued_index
  }

  // calculate
  // special case every issue select logic
  // First one is to find the oldest one
  // Second is to find the oldest with a different ordering
  // Third is to find a random valid one that's different from first two

  // two assumptions here
  // a single issue slot cannot be issued by two issue ports
  // a single issue port cannot be driven by two issue slots
  
  for (w <- 0 until numIssueSlots) {
    issue_slots(w).grant := false.B
    for (i<- 0 until issueWidth) {
      when (iss_signals(i).valid && iss_signals(i).bits === w.U) {
        issue_slots(w).grant := true.B
      }
    }
  }
  
  for (i <- 0 until issueWidth) {
    iss_uops(i).valid := false.B
    iss_uops(i).bits := DontCare
    val iu_index : UInt = iss_signals(i).bits
    when (iss_signals(i).valid) {
      iss_uops(i).valid := true.B
      iss_uops(i).bits := issue_slots(iu_index).iss_uop
    }
  }  
  io.iss_uops := iss_uops
  
  for (i <- 0 until numIssueSlots) {
    when (io.squash_grant) {
      io.iss_uops.map {u => u.valid := false.B}
    }
  }

}
