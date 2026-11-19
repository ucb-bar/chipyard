//------------------------------------------------------------------------------
// [boom-ustt] STT visibility point: the oldest unresolved branch in the ROB
//------------------------------------------------------------------------------
//
// One pending bit per ROB entry, set when a conditional branch or jalr enters
// the ROB and cleared when its branch tag resolves or the entry stops being
// valid (kill, flush, rollback, commit). The VP is the oldest valid entry with
// its bit set, from registered state only. Contract:
// project-docs/spec-interface.md section 4.
//
// Invariant: a set bit means the entry held an unresolved branch in the
// previous cycle. A bit outlives its entry's invalidation by at most one cycle,
// so consumers and assertions use valid && pending, which is exact.

package boom.v3.exu

import chisel3._
import chisel3.util._

import boom.v3.util.AgePriorityEncoder

/**
 * Entries use the ROB's flat index, row << log2Ceil(coreWidth) | bank. With a
 * non-power-of-two width the unused bank encodings are never valid.
 */
class STTVisibilityPoint(
  val numRobRows: Int,
  val coreWidth: Int,
  val maxBrCount: Int) extends Module
{
  val bankSz     = log2Ceil(coreWidth)
  val numEntries = numRobRows << bankSz
  val robAddrSz  = log2Ceil(numRobRows) + bankSz
  val brTagSz    = log2Ceil(maxBrCount)

  val io = IO(new Bundle {
    val enq_valids   = Input(Vec(coreWidth, Bool()))
    val enq_is_br    = Input(Vec(coreWidth, Bool())) // allocates a branch tag
    val enq_row      = Input(UInt(log2Ceil(numRobRows).W))

    // Current ROB state: entry valid bits and each entry's own branch tag.
    val entry_valid  = Input(Vec(numEntries, Bool()))
    val entry_br_tag = Input(Vec(numEntries, UInt(brTagSz.W)))
    val resolve_mask = Input(UInt(maxBrCount.W))
    val head         = Input(UInt(robAddrSz.W))

    val vp           = Output(Valid(UInt(robAddrSz.W)))
  })

  val pending = RegInit(VecInit(Seq.fill(numEntries)(false.B)))

  for (i <- 0 until numEntries) {
    when (!io.entry_valid(i) || io.resolve_mask(io.entry_br_tag(i))) {
      pending(i) := false.B
    }
  }

  // After the resolve updates, so a reused branch tag cannot clear a new branch.
  for (w <- 0 until coreWidth) {
    val idx = if (coreWidth == 1) io.enq_row else Cat(io.enq_row, w.U(bankSz.W))
    when (io.enq_valids(w)) {
      pending(idx) := io.enq_is_br(w)
    }
  }

  val live = (0 until numEntries).map(i => io.entry_valid(i) && pending(i))
  io.vp.valid := live.reduce(_||_)
  io.vp.bits  := Mux(io.vp.valid, AgePriorityEncoder(live, io.head), 0.U)
}
