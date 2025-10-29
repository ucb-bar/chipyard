//******************************************************************************
// Copyright (c) 2015 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Rename BusyTable
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v4.exu

import chisel3._
import chisel3.util._
import boom.v4.common._
import boom.v4.util._
import org.chipsalliance.cde.config.Parameters

class BusyResp extends Bundle
{
  val prs1_busy = Bool()
  val prs2_busy = Bool()
  val prs3_busy = Bool()
}

class RenameBusyTable(
  val plWidth: Int,
  val numPregs: Int,
  val numWbPorts: Int,
  val float: Boolean)
  (implicit p: Parameters) extends BoomModule
{
  val pregSz = log2Ceil(numPregs)

  val io = IO(new BoomBundle()(p) {
    val ren_uops = Input(Vec(plWidth, new MicroOp))
    val busy_resps = Output(Vec(plWidth, new BusyResp))
    val rebusy_reqs = Input(Vec(plWidth, Bool()))

    val wakeups = Input(Vec(numWbPorts, Valid(new Wakeup)))
    val child_rebusys = Input(UInt(aluWidth.W))

    val debug = new Bundle { val busytable = Output(Bits(numPregs.W)) }
  })
  val wakeups = io.wakeups.map { w =>
    val wu = Wire(Valid(new Wakeup))
    wu.valid := RegNext(w.valid) && ((RegNext(w.bits.speculative_mask) & io.child_rebusys) === 0.U)
    wu.bits  := RegNext(w.bits)
    wu
  }

  val busy_table = RegInit(0.U(numPregs.W))
  // Unbusy written back registers.
  val busy_table_wb = busy_table & ~(wakeups.map { w =>
    UIntToOH(w.bits.uop.pdst) & Fill(numPregs, w.valid && !w.bits.rebusy)
  } .reduce(_|_))
  // Rebusy newly allocated registers.
  val busy_table_next = busy_table_wb | (
    (io.ren_uops zip io.rebusy_reqs)
      .map {case (uop, req) => UIntToOH(uop.pdst) & Fill(numPregs, req)}.reduce(_|_)
  ) | (wakeups.map { w =>
    UIntToOH(w.bits.uop.pdst) & Fill(numPregs, w.valid && w.bits.rebusy)
  } .reduce(_|_))

  busy_table := busy_table_next

  // Read the busy table.
  for (i <- 0 until plWidth) {
    val prs1_match = wakeups.map { w => w.valid && w.bits.uop.pdst === io.ren_uops(i).prs1 }
    val prs2_match = wakeups.map { w => w.valid && w.bits.uop.pdst === io.ren_uops(i).prs2 }
    val prs3_match = wakeups.map { w => w.valid && w.bits.uop.pdst === io.ren_uops(i).prs3 }

    io.busy_resps(i).prs1_busy := busy_table(io.ren_uops(i).prs1)
    io.busy_resps(i).prs2_busy := busy_table(io.ren_uops(i).prs2)
    io.busy_resps(i).prs3_busy := busy_table(io.ren_uops(i).prs3)

    when (prs1_match.reduce(_||_)) {
      io.busy_resps(i).prs1_busy := Mux1H(prs1_match, wakeups.map { w => w.valid && w.bits.rebusy })
    }
    when (prs2_match.reduce(_||_)) {
      io.busy_resps(i).prs2_busy := Mux1H(prs2_match, wakeups.map { w => w.valid && w.bits.rebusy })
    }
    when (prs3_match.reduce(_||_)) {
      io.busy_resps(i).prs3_busy := Mux1H(prs3_match, wakeups.map { w => w.valid && w.bits.rebusy })
    }

    if (!float) io.busy_resps(i).prs3_busy := false.B

  }

  io.debug.busytable := busy_table
}
