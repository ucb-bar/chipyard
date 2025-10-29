package shuttle.ifu

import chisel3._
import chisel3.util._

import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.rocket.{MStatus, BP, BreakpointUnit}
import freechips.rocketchip.util._
import org.chipsalliance.cde.config.{Parameters}

import shuttle.common._
import shuttle.util._

class ShuttleFetchBuffer(implicit p: Parameters) extends CoreModule
{
  // This is an approximate heuristic to ensure that
  // this buffer rarely backpresures the frontend
  val numEntries = (retireWidth * 3) + 1
  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(new ShuttleFetchBundle))
    val deq = Vec(retireWidth, Decoupled(new ShuttleUOP))
    val peek = Vec(retireWidth, Valid(new ShuttleUOP))

    val clear = Input(Bool())
  })
  val ram = Reg(Vec(numEntries, Valid(new ShuttleUOP)))
  val enq_ptr = RegInit(1.U(numEntries.W))
  val deq_ptr = RegInit(1.U(numEntries.W))
  require(numEntries >= fetchWidth)
  io.enq.ready := PopCount(ram.map(_.valid)) +& PopCount(io.enq.bits.mask) <= numEntries.U

  // Input microops.
  val in_uops = Wire(Vec(fetchWidth, Valid(new ShuttleUOP)))

  // Step 1: Convert FetchPacket into a vector of MicroOps.
  val lower = Wire(UInt(fetchWidth.W))
  lower := MaskLower(io.enq.bits.mask >> 1)
  val maybe_cfi_mask = io.enq.bits.mask & ~lower
  for (i <- 0 until fetchWidth) {
    val rvc = io.enq.bits.insts(i)(1,0) =/= 3.U
    val cond_br = Seq(BNE, BGE, BGEU, BEQ, BLT, BLTU).map(_ === io.enq.bits.exp_insts(i)).orR
    in_uops(i).valid               := io.enq.valid && io.enq.bits.mask(i)
    in_uops(i).bits                := DontCare
    in_uops(i).bits.pc             := io.enq.bits.pcs(i)
    in_uops(i).bits.ctrl           := DontCare
    in_uops(i).bits.fp_ctrl        := DontCare
    in_uops(i).bits.inst           := io.enq.bits.exp_insts(i)
    in_uops(i).bits.raw_inst       := io.enq.bits.insts(i)
    in_uops(i).bits.rvc            := io.enq.bits.insts(i)(1,0) =/= 3.U
    in_uops(i).bits.sfb_br         := cond_br && ImmGen(IMM_SB, io.enq.bits.exp_insts(i)) === Mux(rvc, 4.S, 6.S) && !io.enq.bits.next_pc.valid
    in_uops(i).bits.btb_resp       := io.enq.bits.btb_resp
    in_uops(i).bits.next_pc.valid  := io.enq.bits.next_pc.valid && maybe_cfi_mask(i)
    in_uops(i).bits.next_pc.bits   := io.enq.bits.next_pc.bits
    in_uops(i).bits.needs_replay   := false.B
    in_uops(i).bits.mem_size       := io.enq.bits.exp_insts(i)(13,12)
    in_uops(i).bits.ras_head       := io.enq.bits.ras_head

    in_uops(i).bits.xcpt := io.enq.bits.xcpt_pf_if || io.enq.bits.xcpt_ae_if
    in_uops(i).bits.edge_inst := (i == 0).B && io.enq.bits.edge_inst
    in_uops(i).bits.xcpt_cause := Mux(io.enq.bits.xcpt_pf_if,
      Causes.fetch_page_fault.U, Causes.fetch_access.U)

  }

  def rotateLeft(in: UInt): UInt = {
    val w = in.getWidth
    ((in << 1) | in(w-1))(w-1,0)
  }

  def rotateLeft(in: UInt, n: UInt): UInt = {
    val w = in.getWidth
    val full = Wire(UInt((w*2).W))
    full := in << n
    (full(w-1,0) | (full >> w))(w-1,0)
  }
  val write_mask = Wire(Vec(numEntries, Vec(fetchWidth, Bool())))
  write_mask.foreach(_.foreach(_ := false.B))

  var enq_oh = enq_ptr
  for (i <- 0 until fetchWidth) {
    for (j <- 0 until numEntries) {
      write_mask(j)(i) := enq_oh(j) && in_uops(i).valid
    }
    enq_oh = Mux(in_uops(i).valid, rotateLeft(enq_oh), enq_oh)
  }
  when (io.enq.fire) {
    enq_ptr := rotateLeft(enq_ptr, PopCount(io.enq.bits.mask))
    for (i <- 0 until numEntries) {
      when (!ram(i).valid) {
        ram(i) := Mux1H(write_mask(i), in_uops)
      }
    }
  }

  var deq_oh = deq_ptr
  var deq_mask = 0.U(numEntries.W)
  for (i <- 0 until retireWidth) {
    val out_uop = Mux1H(deq_oh, ram)
    io.deq(i).valid := out_uop.valid
    io.deq(i).bits := out_uop.bits
    deq_mask = Mux(io.deq(i).fire, deq_mask | deq_oh, deq_mask)
    deq_oh = rotateLeft(deq_oh)
  }
  for (i <- 0 until retireWidth) {
    val out_uop = Mux1H(deq_oh, ram)
    io.peek(i).valid := out_uop.valid
    io.peek(i).bits := out_uop.bits
    deq_oh = rotateLeft(deq_oh)
  }
  for (i <- 0 until numEntries) {
    when (deq_mask(i)) {
      ram(i).valid := false.B
    }
  }
  deq_ptr := rotateLeft(deq_ptr, PopCount(io.deq.map(_.fire)))
  for (i <- 1 until retireWidth) {
    when (io.deq(i).ready) {
      assert(io.deq.take(i).map(_.ready).reduce(_&&_))
    }
  }
  when (io.clear) {
    enq_ptr := 1.U
    deq_ptr := 1.U
    ram.foreach(_.valid := false.B)
  }
}
