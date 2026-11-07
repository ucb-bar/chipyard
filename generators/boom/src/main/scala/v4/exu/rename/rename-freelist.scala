//******************************************************************************
// Copyright (c) 2015 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Rename FreeList
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v4.exu

import chisel3._
import chisel3.util._
import boom.v4.common._
import boom.v4.util._
import org.chipsalliance.cde.config.Parameters

abstract class AbstractRenameFreeList(
  val allocWidth: Int,
  val deallocWidth: Int,
  val numPregs: Int)
  (implicit p: Parameters) extends BoomModule
{
  val pregSz = log2Ceil(numPregs)
  val n = numPregs

  val io = IO(new Bundle {
    val initial_allocation = Input(UInt(numPregs.W))
    // Physical register requests.
    val reqs          = Input(Vec(allocWidth, Bool()))
    val alloc_pregs   = Output(Vec(allocWidth, Valid(UInt(pregSz.W))))

    // Pregs returned by the ROB.
    val despec      = Input(Vec(deallocWidth, Valid(UInt(pregSz.W))))
    val dealloc     = Input(Vec(deallocWidth, Valid(UInt(pregSz.W))))

    // Branch info for starting new allocation lists.
    val ren_br_tags   = Input(Vec(allocWidth+1, Valid(UInt(brTagSz.W))))

    // Mispredict info for recovering speculatively allocated registers.
    val brupdate        = Input(new BrUpdateInfo)

    val rollback      = Input(Bool())

    val debug_freelist = Output(UInt(numPregs.W))
  })
}


class RenameFreeList(
  allocWidth: Int,
  deallocWidth: Int,
  numPregs: Int,
  isImm: Boolean

)(implicit p: Parameters)
    extends AbstractRenameFreeList(allocWidth, deallocWidth, numPregs)
{
  // The free list register array and its branch allocation lists.
  val free_list = RegInit(UInt(numPregs.W), io.initial_allocation)
  val spec_alloc_list = RegInit(0.U(numPregs.W))
  val br_alloc_lists = Reg(Vec(maxBrCount, UInt(numPregs.W)))

  // Select pregs from the free list.
  val sels = SelectFirstN(free_list, allocWidth)
  val sel_fire  = Wire(Vec(allocWidth, Bool()))

  // Allocations seen by branches in each pipeline slot.
  val allocs = io.alloc_pregs map (a => UIntToOH(a.bits)(n-1,0))
  val alloc_masks = (allocs zip io.reqs).scanRight(0.U(n.W)) { case ((a,r),m) => m | a & Fill(n,r) }

  // Masks that modify the freelist array.
  val sel_mask = (sels zip sel_fire) map { case (s,f) => s & Fill(n,f) } reduce(_|_)

  val br_deallocs = br_alloc_lists(io.brupdate.b2.uop.br_tag) & Fill(n, io.brupdate.b2.mispredict)
  val com_deallocs = RegNext(io.dealloc).map(d => UIntToOH(d.bits)(numPregs-1,0) & Fill(n,d.valid)).reduce(_|_)
  val rollback_deallocs = spec_alloc_list & Fill(n, io.rollback)
  val dealloc_mask = com_deallocs | br_deallocs | rollback_deallocs

  val com_despec = io.despec.map(d => UIntToOH(d.bits)(numPregs-1,0) & Fill(n,d.valid)).reduce(_|_)

  // Update branch snapshots
  for (i <- 0 until maxBrCount) {
    val updated_br_alloc_list = if (isImm) {
      // Immediates clear the busy table when they read, potentially before older branches resolve.
      // Thus the branch alloc lists must be updated as well
      br_alloc_lists(i) & ~br_deallocs & ~com_deallocs | alloc_masks(0)
    } else {
      br_alloc_lists(i) & ~br_deallocs | alloc_masks(0)
    }
    br_alloc_lists(i) := updated_br_alloc_list
  }
  if (enableSuperscalarSnapshots) {
    val br_slots = VecInit(io.ren_br_tags.map(tag => tag.valid)).asUInt
    // Create branch allocation lists.
    for (i <- 0 until maxBrCount) {
      val list_req = VecInit(io.ren_br_tags.map(tag => tag.bits === i.U)).asUInt & br_slots
      val new_list = list_req.orR
      when (new_list) {
        br_alloc_lists(i) := Mux1H(list_req, alloc_masks)
      }
    }
  } else {
    assert(PopCount(io.ren_br_tags.map(_.valid)) <= 1.U)
    val do_br_snapshot = io.ren_br_tags.map(_.valid).reduce(_||_)
    val br_snapshot_tag   = Mux1H(io.ren_br_tags.map(_.valid), io.ren_br_tags.map(_.bits))
    val br_snapshot_list  = Mux1H(io.ren_br_tags.map(_.valid), alloc_masks)
    when (do_br_snapshot) {
      br_alloc_lists(br_snapshot_tag) := br_snapshot_list
    }
  }


  spec_alloc_list := (spec_alloc_list | alloc_masks(0)) & ~dealloc_mask & ~com_despec

  // Update the free list.
  free_list     := (free_list & ~sel_mask) | dealloc_mask

  // Pipeline logic | hookup outputs.
  for (w <- 0 until allocWidth) {
    val can_sel = sels(w).orR
    val r_valid = RegInit(false.B)
    val r_sel   = RegEnable(OHToUInt(sels(w)), sel_fire(w))

    r_valid := r_valid && !io.reqs(w) || can_sel
    sel_fire(w) := (!r_valid || io.reqs(w)) && can_sel

    io.alloc_pregs(w).bits  := r_sel
    io.alloc_pregs(w).valid := r_valid
  }

  io.debug_freelist := free_list | io.alloc_pregs.map(p => UIntToOH(p.bits)(n-1,0) & Fill(n,p.valid)).reduce(_|_)

  if (!isImm)
    assert (!(io.debug_freelist & dealloc_mask).orR, "[freelist] Returning a free physical register.")

}


class BankedRenameFreeList(
  plWidth: Int,
  numPregs: Int
)(implicit p: Parameters)
    extends AbstractRenameFreeList(plWidth, plWidth, numPregs)
{
  def bankIdx(prs: UInt): UInt = prs(log2Ceil(plWidth)-1,0)

  require(isPow2(plWidth))
  require(plWidth > 1)
  require(enableColumnALUWrites)
  require(numPregs % plWidth == 0)
  require(!enableSuperscalarSnapshots)

  val sel = RegInit(1.U(plWidth.W))
  sel := RotateL1(sel)

  val freelists = (0 until plWidth).map { w =>
    val freelist = Module(new RenameFreeList(1, plWidth, numPregs / plWidth, false))


    val initial = io.initial_allocation.asBools.zipWithIndex.filter(_._2 % plWidth == w).map(_._1)
    freelist.io.initial_allocation := VecInit(initial).asUInt
    freelist.io.reqs(0) := false.B

    for (i <- 0 until plWidth) {
      freelist.io.despec(i).valid := io.despec(i).valid && bankIdx(io.despec(i).bits) === w.U
      freelist.io.despec(i).bits  := io.despec(i).bits >> log2Ceil(plWidth)

      freelist.io.dealloc(i).valid := io.dealloc(i).valid && bankIdx(io.dealloc(i).bits) === w.U
      freelist.io.dealloc(i).bits  := io.dealloc(i).bits >> log2Ceil(plWidth)
    }
    freelist.io.ren_br_tags(0).valid := false.B
    freelist.io.ren_br_tags(0).bits  := DontCare
    freelist.io.ren_br_tags(1).valid := false.B
    freelist.io.ren_br_tags(1).bits  := DontCare

    freelist.io.brupdate := io.brupdate
    freelist.io.rollback := io.rollback

    freelist
  }

  var older_than_branch = true.B
  for (w <- 0 until plWidth) {
    io.alloc_pregs(w).valid := false.B
    io.alloc_pregs(w).bits  := DontCare



    for (i <- 0 until plWidth) {
      when (sel((i + w) % plWidth)) {
        freelists(i).io.reqs(0) := io.reqs(w)
        io.alloc_pregs(w).valid := freelists(i).io.alloc_pregs(0).valid
        io.alloc_pregs(w).bits  := Cat(freelists(i).io.alloc_pregs(0).bits, i.U(log2Ceil(plWidth).W))

        freelists(i).io.ren_br_tags(0).valid := io.ren_br_tags.map(_.valid).reduce(_||_) && !older_than_branch
        freelists(i).io.ren_br_tags(0).bits  := Mux1H(io.ren_br_tags.map(_.valid), io.ren_br_tags.map(_.bits))

        freelists(i).io.ren_br_tags(1).valid := io.ren_br_tags.map(_.valid).reduce(_||_) &&  older_than_branch
        freelists(i).io.ren_br_tags(1).bits  := Mux1H(io.ren_br_tags.map(_.valid), io.ren_br_tags.map(_.bits))

      }
    }
    older_than_branch = older_than_branch && !io.ren_br_tags(w+1).valid
  }

  io.debug_freelist := VecInit((0 until numPregs).map { w =>
    freelists(w % plWidth).io.debug_freelist(w >> log2Ceil(plWidth))
  }).asUInt
}
