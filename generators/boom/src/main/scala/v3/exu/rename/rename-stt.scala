//------------------------------------------------------------------------------
// [boom-ustt] STT taint tables and dispatch-time YRoT computation
//------------------------------------------------------------------------------
//
// One tag per physical register (integer and FP), written on accepted dispatch
// lanes. Every cycle each stored tag is dropped once it is safe against the VP,
// so no tag outlives its root's ROB slot. Contract:
// project-docs/spec-interface.md sections 3 and 5.
//
// Two ways to compute a lane's tag from its dispatch group:
//  - bypass (baseline STT): each source takes the destination tag of the
//    latest older lane that writes it, so tags chain lane to lane.
//  - Age Matrix (uSTT, #5): all table-read source tags are compared pairwise
//    in parallel with the dependency check, and each lane selects the youngest
//    tag among the sources it reaches. Port of reference/uSTT/rtl/src/stt_age.v
//    and stt_control.v.

package boom.v3.exu

import chisel3._
import chisel3.util._

import boom.v3.common._
import boom.v3.util.IsOlder

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
 * @param ageMatrix compute same-group tags with the uSTT Age Matrix instead of the bypass chain
 */
class STTTaintTable(
  val plWidth: Int,
  val numIntPregs: Int,
  val numFpPregs: Int,
  val lregSz: Int,
  val pregSz: Int,
  val robAddrSz: Int,
  val ageMatrix: Boolean = false) extends Module
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
    writes_int(w) := lane.fire && lane.ldst_val && lane.dst_rtype === RT_FIX
    writes_fp(w)  := lane.fire && lane.ldst_val && lane.dst_rtype === RT_FLT && (numFpPregs > 0).B
  }

  // A source operand: its table read, and which older lane (if any) writes it.
  class Slot(val lane: Int, is_int: Bool, is_fp: Bool, preg: UInt) {
    val table_tag = WireInit(Mux(is_int, readInt(preg), Mux(is_fp, readFp(preg), untainted)))
    val hits = (0 until lane).map(j => WireInit(io.lanes(j).pdst === preg &&
      (is_int && writes_int(j) || is_fp && writes_fp(j))))
    // The latest older writer; physical destinations are unique, so at most one hits.
    val producer = if (lane == 0) Seq[Bool]() else PriorityEncoderOH(hits.reverse).reverse.map(WireInit(_))
    val bypassed = WireInit(hits.foldLeft(false.B)(_||_))
  }

  val slots = (0 until plWidth).map { w =>
    val lane = io.lanes(w)
    Seq(new Slot(w, lane.lrs1_rtype === RT_FIX && lane.lrs1 =/= 0.U, lane.lrs1_rtype === RT_FLT, lane.prs1),
        new Slot(w, lane.lrs2_rtype === RT_FIX && lane.lrs2 =/= 0.U, lane.lrs2_rtype === RT_FLT, lane.prs2),
        new Slot(w, false.B, lane.frs3_en, lane.prs3))
  }

  val yrots = (if (ageMatrix) ageMatrixYrots() else bypassYrots()).map(WireInit(_))
  // Keep each lane's tags as shared signals. Inlined, the bypass chain copies every
  // older lane's expression into each younger lane and grows exponentially with width.
  yrots.foreach(dontTouch(_))
  dontTouch(dst_tags)

  for (w <- 0 until plWidth) {
    val root = Wire(tagType)
    root.valid := true.B
    root.bits  := io.lanes(w).rob_idx

    io.yrot(w)  := yrots(w)
    dst_tags(w) := Mux(io.lanes(w).is_root, root, yrots(w))
  }

  /** Baseline: each source reads its producer's destination tag, chaining through the group. */
  def bypassYrots(): Seq[Valid[UInt]] = slots.map { lane_slots =>
    val tags = lane_slots.map { s =>
      if (s.lane == 0) s.table_tag
      else Mux(s.bypassed, PriorityMux(s.hits.reverse, dst_tags.take(s.lane).reverse), s.table_tag)
    }
    tags.reduce((a, b) => STTYoungest(a, b, io.head))
  }

  /**
   * Age Matrix: the result is the youngest leaf of the lane's dependency tree
   * within the group. A leaf is either the table tag of an unbypassed source, or
   * a same-group root (load or AMO), which ends a path. Same-group roots are
   * younger than every table tag, so when a lane reaches one, the latest such
   * lane wins without an age comparison.
   */
  def ageMatrixYrots(): Seq[Valid[UInt]] = {
    val all_slots = slots.flatten
    val n = all_slots.size

    // edge(w)(j): lane w reads lane j's destination directly.
    val edge = (0 until plWidth).map(w => (0 until w).map(j =>
      WireInit(slots(w).map(s => s.producer(j)).reduce(_||_))))

    // reach(w)(j): a dependency path from lane w back to lane j whose
    // intermediate lanes are not roots. Lane w reaches itself.
    val reach = Array.tabulate(plWidth, plWidth)((_, _) => false.B)
    for (w <- 0 until plWidth) {
      reach(w)(w) = true.B
      for (j <- w - 1 to 0 by -1) {
        val via = (j + 1 until w).map(m => edge(w)(m) && !io.lanes(m).is_root && reach(m)(j))
        reach(w)(j) = WireInit((via :+ edge(w)(j)).reduce(_||_))
      }
    }

    // younger(s)(t): slot t's table tag is younger than slot s's; invalid tags are oldest.
    // Independent of the dependency check, so it evaluates in parallel with it.
    val younger = Array.tabulate(n, n)((_, _) => false.B)
    for (s <- 0 until n; t <- s + 1 until n) {
      val a = all_slots(s).table_tag
      val b = all_slots(t).table_tag
      val t_younger = b.valid && (!a.valid || IsOlder(a.bits, b.bits, io.head))
      val s_younger = a.valid && (!b.valid || IsOlder(b.bits, a.bits, io.head))
      younger(s)(t) = WireInit(t_younger)
      younger(t)(s) = WireInit(s_younger)
    }

    (0 until plWidth).map { w =>
      // Table tags in this lane's tree: unbypassed sources of reached non-root lanes.
      val sel = all_slots.map { s =>
        val lane_in_tree = if (s.lane == w) true.B
                           else if (s.lane > w) false.B
                           else reach(w)(s.lane) && !io.lanes(s.lane).is_root
        WireInit(lane_in_tree && !s.bypassed)
      }
      val chosen = (0 until n).map(s => WireInit(sel(s) && !(0 until n).map(t => sel(t) && younger(s)(t)).reduce(_||_)))
      val age_tag = Wire(tagType)
      age_tag.valid := (0 until n).map(s => chosen(s) && all_slots(s).table_tag.valid).reduce(_||_)
      age_tag.bits  := (0 until n).map(s => Mux(chosen(s), all_slots(s).table_tag.bits, 0.U)).reduce(_|_)

      // Same-group roots reached by this lane (a root ends a path, so only the nearest count).
      val roots = (0 until w).map(j => reach(w)(j) && io.lanes(j).is_root)
      if (w == 0) {
        age_tag
      } else {
        val root_tag = Wire(tagType)
        root_tag.valid := true.B
        root_tag.bits  := PriorityMux(roots.reverse, (0 until w).reverse.map(j => io.lanes(j).rob_idx))
        Mux(roots.reduce(_||_), root_tag, age_tag)
      }
    }
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
