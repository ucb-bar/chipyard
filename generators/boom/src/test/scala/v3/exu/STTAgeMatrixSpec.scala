// [boom-ustt] Age Matrix equivalence against the baseline bypass (#5).
//
// Random dispatch groups in the style of reference/uSTT/rtl/test/STTTestGen.py:
// dense register reuse from a small pool, in-group renaming, and chained state
// (each group starts from the previous groups' table, map table and ROB). Both
// taint tables get identical inputs every cycle; each firing lane's tag must
// match a sequential reference model, and therefore each other.

package boom.v3.exu

import scala.collection.mutable
import scala.util.Random

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec

/** One bypass table and one Age Matrix table on the same inputs. */
class STTTaintPair(plWidth: Int, numInt: Int, numFp: Int, lregSz: Int, pregSz: Int, robAddrSz: Int) extends Module
{
  val bypass = Module(new STTTaintTable(plWidth, numInt, numFp, lregSz, pregSz, robAddrSz,
                                        ageMatrix = false, debugTables = true))
  val age    = Module(new STTTaintTable(plWidth, numInt, numFp, lregSz, pregSz, robAddrSz,
                                        ageMatrix = true, debugTables = true))

  val io = IO(new Bundle {
    val lanes     = Input(Vec(plWidth, new STTTaintLane(lregSz, pregSz, robAddrSz)))
    val vp        = Input(Valid(UInt(robAddrSz.W)))
    val head      = Input(UInt(robAddrSz.W))
    val flush     = Input(Bool())
    val yrot      = Output(Vec(plWidth, Valid(UInt(robAddrSz.W))))
    val yrot_age  = Output(Vec(plWidth, Valid(UInt(robAddrSz.W))))
    val debug     = Output(chiselTypeOf(bypass.io.debug.get))
    val debug_age = Output(chiselTypeOf(age.io.debug.get))
  })

  for (t <- Seq(bypass, age)) {
    t.io.lanes := io.lanes
    t.io.vp    := io.vp
    t.io.head  := io.head
    t.io.flush := io.flush
  }
  io.yrot     := bypass.io.yrot
  io.yrot_age := age.io.yrot
  io.debug     := bypass.io.debug.get
  io.debug_age := age.io.debug.get
}

object STTAgeMatrixSpec
{
  val FIX = 0
  val FLT = 1
  val X   = 2
  val PAS = 3

  case class Src(rtype: Int, arch: Int)
  case class Op(name: String, root: Boolean, branch: Boolean, srcs: Seq[Src], rs3: Option[Int], dst: Option[Src])

  /** A renamed lane of a dispatch group. */
  case class Lane(op: Op, prs: Seq[Int], prs3: Int, pdst: Int, ldstVal: Boolean)

  def older(a: Int, b: Int, head: Int): Boolean = (a < b) ^ (a < head) ^ (b < head)
}

class STTAgeMatrixSpec extends AnyFunSpec
{
  import STTAgeMatrixSpec._

  def run(width: Int, cycles: Int, seed: Long): Unit = {
    val rng       = new Random(seed)
    val robRows   = 16
    val bankSz    = log2Ceil(width)
    val robAddrSz = log2Ceil(robRows) + bankSz
    val numInt    = 32 + 4 * width
    val numFp     = 32 + 4 * width
    val pregSz    = log2Ceil(numInt max numFp)
    val lregSz    = 6
    // As in STTTestGen.py: a pool of about three registers per lane forces collisions.
    val pool      = (3 * width) min 31

    // Model state.
    val mapInt  = Array.tabulate(32)(i => i)
    val mapFp   = Array.tabulate(32)(i => i)
    val freeInt = mutable.Queue((32 until numInt): _*)
    val freeFp  = mutable.Queue((32 until numFp): _*)
    val tagInt  = mutable.Map[Int, Int]() // preg -> root ROB index; absent = untainted
    val tagFp   = mutable.Map[Int, Int]()
    var headRow = 0
    var tailRow = 0
    val pending = mutable.Set[Int]()       // ROB indices of unresolved branches
    var group: Option[Array[Option[Lane]]] = None
    var dispatched = Array.fill(width)(false)
    var toFree = Seq[(Int, Int)]()          // (rtype, preg) freed when the group completes

    var compared = 0
    var bypassHits = 0

    def randInt() = Src(FIX, rng.nextInt(pool))
    def randFp()  = Src(FLT, rng.nextInt(pool))
    def randOp(): Op = rng.nextInt(12) match {
      case 0  => Op("ld",    true,  false, Seq(randInt()), None, Some(randInt()))
      case 1  => Op("fld",   true,  false, Seq(randInt()), None, Some(randFp()))
      case 2  => Op("amo",   true,  false, Seq(randInt(), randInt()), None, Some(randInt()))
      case 3  => Op("sd",    false, false, Seq(randInt(), randInt()), None, None)
      case 4  => Op("fsd",   false, false, Seq(randInt(), randFp()), None, None)
      case 5  => Op("beq",   false, true,  Seq(randInt(), randInt()), None, None)
      case 6  => Op("jalr",  false, true,  Seq(randInt()), None, Some(randInt()))
      case 7  => Op("fmadd", false, false, Seq(randFp(), randFp()), Some(rng.nextInt(pool)), Some(randFp()))
      case 8  => Op("fmv.x", false, false, Seq(randFp()), None, Some(randInt()))
      case 9  => Op("fcvt",  false, false, Seq(randInt()), None, Some(randFp()))
      case 10 => Op("csrr",  false, false, Seq(Src(PAS, rng.nextInt(32))), None, Some(randInt()))
      case _  => Op("add",   false, false, Seq(randInt(), randInt()), None, Some(randInt()))
    }

    def newGroup(): Unit = {
      toFree = Seq()
      val lanes = Array.fill[Option[Lane]](width)(None)
      for (w <- 0 until width if rng.nextInt(8) != 0) {
        val op = randOp()
        // Sources read the map as updated by older lanes in this group (in-group rename).
        val prs = op.srcs.map {
          case Src(FIX, a) => mapInt(a)
          case Src(FLT, a) => mapFp(a)
          case Src(_, a)   => a
        }
        val prs3 = op.rs3.map(mapFp(_)).getOrElse(0)
        var pdst = 0
        var ldstVal = false
        op.dst match {
          case Some(Src(FIX, a)) if a != 0 =>
            pdst = freeInt.dequeue(); toFree :+= ((FIX, mapInt(a))); mapInt(a) = pdst; ldstVal = true
          case Some(Src(FLT, a)) =>
            pdst = freeFp.dequeue(); toFree :+= ((FLT, mapFp(a))); mapFp(a) = pdst; ldstVal = true
          case _ =>
        }
        lanes(w) = Some(Lane(op, prs, prs3, pdst, ldstVal))
      }
      group = Some(lanes)
      dispatched = Array.fill(width)(false)
    }

    def drive(c: STTTaintPair, w: Int, lane: Option[Lane], fire: Boolean): Unit = {
      val l = c.io.lanes(w)
      lane match {
        case Some(Lane(op, prs, prs3, pdst, ldstVal)) =>
          val s1 = op.srcs.lift(0).getOrElse(Src(X, 0))
          val s2 = op.srcs.lift(1).getOrElse(Src(X, 0))
          l.fire.poke(fire.B)
          l.is_root.poke(op.root.B)
          l.lrs1_rtype.poke(s1.rtype.U); l.lrs1.poke(s1.arch.U); l.prs1.poke(prs.lift(0).getOrElse(0).U)
          l.lrs2_rtype.poke(s2.rtype.U); l.lrs2.poke(s2.arch.U); l.prs2.poke(prs.lift(1).getOrElse(0).U)
          l.frs3_en.poke(op.rs3.isDefined.B); l.prs3.poke(prs3.U)
          l.ldst_val.poke(ldstVal.B)
          l.dst_rtype.poke(op.dst.map(_.rtype).getOrElse(X).U)
          l.pdst.poke(pdst.U)
        case None =>
          // Empty lane: arbitrary contents must not matter.
          l.fire.poke(false.B)
          l.is_root.poke(rng.nextBoolean().B)
          l.lrs1_rtype.poke(rng.nextInt(4).U); l.lrs1.poke(rng.nextInt(32).U); l.prs1.poke(rng.nextInt(numInt).U)
          l.lrs2_rtype.poke(rng.nextInt(4).U); l.lrs2.poke(rng.nextInt(32).U); l.prs2.poke(rng.nextInt(numInt).U)
          l.frs3_en.poke(rng.nextBoolean().B); l.prs3.poke(rng.nextInt(numFp).U)
          l.ldst_val.poke(rng.nextBoolean().B)
          l.dst_rtype.poke(rng.nextInt(4).U)
          l.pdst.poke(rng.nextInt(numInt).U)
      }
      l.rob_idx.poke(((tailRow << bankSz) | w).U)
    }

    simulate(new STTTaintPair(width, numInt, numFp, lregSz, pregSz, robAddrSz)) { c =>
      c.io.flush.poke(false.B)
      c.io.vp.valid.poke(false.B); c.io.vp.bits.poke(0.U); c.io.head.poke(0.U)
      for (w <- 0 until width) drive(c, w, None, false)
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      for (cycle <- 0 until cycles) {
        val head = headRow << bankSz
        val vp = pending.reduceOption((a, b) => if (older(a, b, head)) a else b)
        def safe(t: Int) = vp.forall(v => older(t, v, head))

        val rowsInUse = (tailRow - headRow + robRows) % robRows
        val flush = group.isEmpty && rng.nextInt(50) == 0
        if (group.isEmpty && !flush && rowsInUse < robRows - 2) newGroup()

        // Fire a prefix of the remaining valid lanes; the rest stall.
        val lanes = group.getOrElse(Array.fill[Option[Lane]](width)(None))
        val limit = if (rng.nextInt(4) == 0) rng.nextInt(width + 1) else width
        val fire = (0 until width).map(w => lanes(w).isDefined && !dispatched(w) && w < limit)
        for (w <- 0 until width) drive(c, w, if (dispatched(w)) None else lanes(w), fire(w))
        c.io.vp.valid.poke(vp.isDefined.B)
        c.io.vp.bits.poke(vp.getOrElse(0).U)
        c.io.head.poke(head.U)
        c.io.flush.poke(flush.B)

        // Reference: normalized table reads, then lanes in program order.
        val tInt = mutable.Map(tagInt.filter { case (_, t) => !safe(t) }.toSeq: _*)
        val tFp  = mutable.Map(tagFp.filter { case (_, t) => !safe(t) }.toSeq: _*)
        if (flush) { tInt.clear(); tFp.clear() }
        for (w <- 0 until width if fire(w)) {
          val l = lanes(w).get
          val srcTags = l.op.srcs.zip(l.prs).collect {
            case (Src(FIX, a), p) if a != 0 => tInt.get(p)
            case (Src(FLT, _), p)           => tFp.get(p)
          } ++ l.op.rs3.map(_ => tFp.get(l.prs3)).toSeq
          val expected = srcTags.flatten.reduceOption((a, b) => if (older(a, b, head)) b else a)

          def tag(y: Valid[UInt]) = if (y.valid.peek().litToBoolean) Some(y.bits.peek().litValue.toInt) else None
          val got_bypass = tag(c.io.yrot(w))
          val got_age    = tag(c.io.yrot_age(w))
          val ctx = s"width=$width seed=$seed cycle=$cycle lane=$w op=${l.op.name}"
          assert(got_bypass === expected, s"bypass mismatch, $ctx")
          assert(got_age === expected, s"Age Matrix mismatch, $ctx")
          compared += 1
          if (l.prs.exists(p => (0 until w).exists(j => fire(j) && lanes(j).exists(o => o.ldstVal && o.pdst == p)))) bypassHits += 1

          val dstTag = if (l.op.root) Some((tailRow << bankSz) | w) else expected
          if (l.ldstVal) {
            val t = if (l.op.dst.get.rtype == FIX) tInt else tFp
            dstTag match { case Some(v) => t(l.pdst) = v; case None => t.remove(l.pdst) }
          }
        }
        c.clock.step()

        // Advance the model. Only rows complete before this cycle may retire.
        val tailAtStart = tailRow
        tagInt.clear(); tagInt ++= tInt
        tagFp.clear(); tagFp ++= tFp
        for (w <- 0 until width if fire(w)) {
          dispatched(w) = true
          if (lanes(w).get.op.branch) pending += ((tailRow << bankSz) | w)
        }
        if (group.isDefined && (0 until width).forall(w => lanes(w).isEmpty || dispatched(w))) {
          for ((rt, p) <- toFree) (if (rt == FIX) freeInt else freeFp).enqueue(p)
          tailRow = (tailRow + 1) % robRows
          group = None
        }
        if (flush) { pending.clear(); headRow = tailRow }
        // Retire the head row if it held no unresolved branch this cycle; a branch
        // cannot resolve and retire in the same cycle.
        if (headRow != tailAtStart && rng.nextBoolean() &&
            !vp.exists(v => (v >> bankSz) == headRow)) headRow = (headRow + 1) % robRows
        for (b <- pending.toSeq if rng.nextInt(5) == 0) pending -= b

        // Lifetime (spec section 5): every stored tag names an entry still in
        // the ROB window, or in the tail row of a group that is still dispatching.
        val newHead = headRow << bankSz
        val tail = tailRow << bankSz
        def live(t: Int) = older(t, tail, newHead) || (group.isDefined && (t >> bankSz) == tailRow)
        // On the model: a sanity check that the stimulus itself respects the ROB.
        for (t <- tagInt.values ++ tagFp.values)
          assert(live(t), s"model tag $t outside window [$newHead, $tail), width=$width seed=$seed cycle=$cycle")
        // On the hardware: both tables, every entry, read out after the edge. Each
        // must equal the model exactly, so a stale tag that is never read again
        // still fails here even though no lane's yrot would show it.
        for ((d, name) <- Seq((c.io.debug, "bypass"), (c.io.debug_age, "Age Matrix"))) {
          for ((valid, tags, n, model, cls) <- Seq(
                 (d.int_valid, d.int_tags, numInt, tagInt, "int"),
                 (d.fp_valid, d.fp_tags, numFp, tagFp, "fp"))) {
            val v = valid.peek().litValue
            val b = tags.peek().litValue
            for (i <- 0 until n) {
              val hw = if (v.testBit(i)) Some(((b >> (i * robAddrSz)) & ((BigInt(1) << robAddrSz) - 1)).toInt) else None
              val ctx = s"$name $cls preg $i, width=$width seed=$seed cycle=$cycle"
              assert(hw === model.get(i), s"table differs from model, $ctx")
              hw.foreach(t => assert(live(t), s"stored tag $t outside window [$newHead, $tail), $ctx"))
            }
          }
        }
      }
    }
    info(s"width $width: $compared firing lanes compared, $bypassHits read a same-cycle older lane")
    assert(width == 1 || bypassHits >= 100, s"stimulus exercised too few same-group dependencies ($bypassHits)")
  }

  describe("STTTaintTable with ageMatrix") {
    for (width <- Seq(1, 2, 3, 4, 8)) {
      it(s"matches the bypass and the reference model on random groups, width $width") {
        for (seed <- Seq(1L, 2L, 3L)) run(width, cycles = 3000, seed = seed * 1000 + width)
      }
    }
  }
}
