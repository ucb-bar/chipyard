//------------------------------------------------------------------------------
// [boom-ustt] STT taint tables and dispatch-time YRoT computation
//------------------------------------------------------------------------------
//
// One tag per physical register (integer and FP), written on accepted dispatch
// lanes, with a same-group bypass from older lanes. Every cycle each stored tag
// is dropped once it is safe against the VP, so no tag outlives its root's ROB
// slot. Contract: project-docs/spec-interface.md sections 3 and 5.

package boom.v3.exu

import chisel3._
import chisel3.util._

import boom.v3.common._

/**
 * The dispatch-lane fields the taint logic reads: final physical operands and
 * the lane's own ROB index.
 */
class STTTaintLane(val lregSz: Int, val pregSz: Int, val robAddrSz: Int) extends Bundle
{
  val fire       = Bool()
  val rob_idx    = UInt(robAddrSz.W)
  val is_root    = Bool() // load or AMO: its result is a new root

  val lrs1       = UInt(lregSz.W)
  val lrs2       = UInt(lregSz.W)
  val lrs1_rtype = UInt(2.W)
  val lrs2_rtype = UInt(2.W)
  val frs3_en    = Bool()
  val prs1       = UInt(pregSz.W)
  val prs2       = UInt(pregSz.W)
  val prs3       = UInt(pregSz.W)

  val ldst_val   = Bool()
  val dst_rtype  = UInt(2.W)
  val pdst       = UInt(pregSz.W)
}

/**
 * @param plWidth dispatch width
 * @param numIntPregs integer physical registers
 * @param numFpPregs FP physical registers, 0 without an FPU
 */
class STTTaintTable(
  val plWidth: Int,
  val numIntPregs: Int,
  val numFpPregs: Int,
  val lregSz: Int,
  val pregSz: Int,
  val robAddrSz: Int) extends Module
{
  val io = IO(new Bundle {
    val lanes = Input(Vec(plWidth, new STTTaintLane(lregSz, pregSz, robAddrSz)))
    val vp    = Input(Valid(UInt(robAddrSz.W)))
    val head  = Input(UInt(robAddrSz.W))
    // Full flush: committed values are untainted.
    val flush = Input(Bool())

    // Aggregate source tag of each lane; meaningful when that lane is valid.
    val yrot  = Output(Vec(plWidth, Valid(UInt(robAddrSz.W))))
  })

  def tagType = Valid(UInt(robAddrSz.W))
  val untainted = 0.U.asTypeOf(tagType)

  val int_table = RegInit(VecInit(Seq.fill(numIntPregs)(untainted)))
  val fp_table  = if (numFpPregs > 0) Some(RegInit(VecInit(Seq.fill(numFpPregs)(untainted)))) else None

  def index(preg: UInt, n: Int): UInt = preg(log2Ceil(n) - 1, 0)

  def readInt(preg: UInt): Valid[UInt] = STTClearSafe(int_table(index(preg, numIntPregs)), io.vp, io.head)
  def readFp(preg: UInt): Valid[UInt] = fp_table match {
    case Some(t) => STTClearSafe(t(index(preg, numFpPregs)), io.vp, io.head)
    case None    => STTUntainted(robAddrSz)
  }

  val writes_int = Wire(Vec(plWidth, Bool()))
  val writes_fp  = Wire(Vec(plWidth, Bool()))
  val dst_tags   = Wire(Vec(plWidth, tagType))

  for (w <- 0 until plWidth) {
    val lane = io.lanes(w)

    // An older accepted lane in the same group overrides the table read.
    def source(is_int: Bool, is_fp: Bool, preg: UInt): Valid[UInt] = {
      val table_tag = Mux(is_int, readInt(preg), Mux(is_fp, readFp(preg), untainted))
      if (w == 0) {
        table_tag
      } else {
        val hits = (0 until w).map(j => io.lanes(j).pdst === preg &&
          (is_int && writes_int(j) || is_fp && writes_fp(j)))
        Mux(hits.reduce(_||_), PriorityMux(hits.reverse, dst_tags.take(w).reverse), table_tag)
      }
    }

    val rs1 = source(lane.lrs1_rtype === RT_FIX && lane.lrs1 =/= 0.U, lane.lrs1_rtype === RT_FLT, lane.prs1)
    val rs2 = source(lane.lrs2_rtype === RT_FIX && lane.lrs2 =/= 0.U, lane.lrs2_rtype === RT_FLT, lane.prs2)
    val rs3 = source(false.B, lane.frs3_en, lane.prs3)

    val yrot = STTYoungest(STTYoungest(rs1, rs2, io.head), rs3, io.head)
    io.yrot(w) := yrot

    val root = Wire(tagType)
    root.valid := true.B
    root.bits  := lane.rob_idx

    writes_int(w) := lane.fire && lane.ldst_val && lane.dst_rtype === RT_FIX
    writes_fp(w)  := lane.fire && lane.ldst_val && lane.dst_rtype === RT_FLT && (numFpPregs > 0).B
    dst_tags(w)   := Mux(lane.is_root, root, yrot)
  }

  // Clear safe tags every cycle, then apply this cycle's writes. Writes win over
  // a flush: a squashed writer's register is freed and rewritten on reallocation.
  def update(table: Vec[Valid[UInt]], writes: Seq[Bool]): Unit = {
    for (i <- 0 until table.size) {
      val next = WireInit(Mux(io.flush, untainted, STTClearSafe(table(i), io.vp, io.head)))
      for (w <- 0 until plWidth) {
        when (writes(w) && index(io.lanes(w).pdst, table.size) === i.U) {
          next := dst_tags(w)
        }
      }
      table(i) := next
    }
  }

  update(int_table, writes_int)
  fp_table.foreach(t => update(t, writes_fp))
}
