// [boom-ustt] Module tests for the STT taint table (#4).

package boom.v3.exu

import chisel3._
import chisel3.util.log2Ceil
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec

object STTTaintTableSpec
{
  // Small enough to read, wide enough for a two-lane (Medium-like) group.
  val plWidth   = 2
  val numInt    = 16
  val numFp     = 8
  val lregSz    = 6
  val pregSz    = 4
  val robAddrSz = 5

  val FIX = 0
  val FLT = 1
  val X   = 2

  /** One lane's operands. A source or destination of type X is absent. */
  case class Op(
    fire: Boolean = false,
    rob: Int = 0,
    root: Boolean = false,
    rs1: (Int, Int, Int) = (X, 0, 0), // (rtype, lreg, preg)
    rs2: (Int, Int, Int) = (X, 0, 0),
    rs3: Option[Int] = None,          // FP preg
    dst: (Int, Int) = (X, 0))         // (rtype, preg)

  def int(lreg: Int, preg: Int) = (FIX, lreg, preg)
  def fp(preg: Int) = (FLT, 1, preg)
}

class STTTaintTableSpec extends AnyFunSpec
{
  import STTTaintTableSpec._

  def dut = new STTTaintTable(plWidth, numInt, numFp, lregSz, pregSz, robAddrSz)

  def drive(c: STTTaintTable, w: Int, op: Op): Unit = {
    val l = c.io.lanes(w)
    l.fire.poke(op.fire.B)
    l.rob_idx.poke(op.rob.U)
    l.is_root.poke(op.root.B)
    l.lrs1_rtype.poke(op.rs1._1.U)
    l.lrs1.poke(op.rs1._2.U)
    l.prs1.poke(op.rs1._3.U)
    l.lrs2_rtype.poke(op.rs2._1.U)
    l.lrs2.poke(op.rs2._2.U)
    l.prs2.poke(op.rs2._3.U)
    l.frs3_en.poke(op.rs3.isDefined.B)
    l.prs3.poke(op.rs3.getOrElse(0).U)
    l.ldst_val.poke((op.dst._1 != X).B)
    l.dst_rtype.poke(op.dst._1.U)
    l.pdst.poke(op.dst._2.U)
  }

  def lanes(c: STTTaintTable, ops: Op*): Unit = {
    for (w <- 0 until plWidth) drive(c, w, if (w < ops.size) ops(w) else Op())
  }

  def vp(c: STTTaintTable, valid: Boolean, idx: Int = 0, head: Int = 0): Unit = {
    c.io.vp.valid.poke(valid.B)
    c.io.vp.bits.poke(idx.U)
    c.io.head.poke(head.U)
  }

  def reset(c: STTTaintTable): Unit = {
    lanes(c)
    vp(c, valid = false)
    c.io.flush.poke(false.B)
    c.reset.poke(true.B)
    c.clock.step()
    c.reset.poke(false.B)
  }

  /** Tag seen on lane 0 for one source, without dispatching. */
  def probe(c: STTTaintTable, src: (Int, Int, Int)): Option[Int] = {
    drive(c, 0, Op(rs1 = src))
    val y = c.io.yrot(0)
    if (y.valid.peek().litToBoolean) Some(y.bits.peek().litValue.toInt) else None
  }

  def yrot(c: STTTaintTable, w: Int): Option[Int] = {
    val y = c.io.yrot(w)
    if (y.valid.peek().litToBoolean) Some(y.bits.peek().litValue.toInt) else None
  }

  def load(rob: Int, addr: (Int, Int, Int), dst: Int) =
    Op(fire = true, rob = rob, root = true, rs1 = addr, dst = (FIX, dst))

  describe("STTTaintTable") {
    it("gives a load's destination its own ROB index, without tainting its input") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        lanes(c, load(rob = 7, addr = int(1, 1), dst = 5))
        assert(yrot(c, 0) === None, "a clean-address load has an untainted input")
        c.clock.step()
        lanes(c)
        assert(probe(c, int(5, 5)) === Some(7))
        assert(probe(c, int(1, 1)) === None)
      }
    }

    it("bypasses an older lane's destination tag within a dispatch group") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        lanes(c,
          load(rob = 8, addr = int(1, 1), dst = 6),
          Op(fire = true, rob = 9, rs1 = int(6, 6), dst = (FIX, 7)))
        assert(yrot(c, 1) === Some(8), "same-cycle consumer sees the new root")
        c.clock.step()
        lanes(c)
        assert(probe(c, int(7, 7)) === Some(8), "the consumer's destination inherits the root")
      }
    }

    it("does not write or bypass from lanes that do not fire (stalled suffix)") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        lanes(c,
          load(rob = 8, addr = int(1, 1), dst = 6),
          Op(fire = false, rob = 9, rs1 = int(6, 6), dst = (FIX, 7)))
        c.clock.step()
        // Later acceptance of the stalled lane, now in lane position 1 with lane 0 empty.
        lanes(c, Op(), Op(fire = true, rob = 9, rs1 = int(6, 6), dst = (FIX, 7)))
        assert(yrot(c, 1) === Some(8), "the re-read sees the prefix's committed write")
        c.clock.step()
        lanes(c)
        assert(probe(c, int(7, 7)) === Some(8))

        // A non-firing older lane is not a bypass source.
        lanes(c,
          Op(fire = false, rob = 10, root = true, rs1 = int(1, 1), dst = (FIX, 9)),
          Op(fire = true, rob = 11, rs1 = int(9, 9), dst = (FIX, 10)))
        assert(yrot(c, 1) === None)
        c.clock.step()
        lanes(c)
        assert(probe(c, int(9, 9)) === None)
        assert(probe(c, int(10, 10)) === None)
      }
    }

    it("selects the youngest valid source in circular ROB order") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 26, head = 24)
        lanes(c,
          load(rob = 30, addr = int(1, 1), dst = 5),
          load(rob = 31, addr = int(1, 1), dst = 4))
        c.clock.step()
        lanes(c, load(rob = 2, addr = int(1, 1), dst = 6))
        c.clock.step()
        lanes(c, Op(rs1 = int(5, 5), rs2 = int(6, 6)))
        assert(yrot(c, 0) === Some(2), "wrapped index 2 is younger than 30")
        lanes(c, Op(rs1 = int(6, 6), rs2 = int(4, 4)))
        assert(yrot(c, 0) === Some(2))
        lanes(c, Op(rs1 = int(5, 5), rs2 = int(4, 4)))
        assert(yrot(c, 0) === Some(31))
        lanes(c, Op(rs1 = int(1, 1), rs2 = int(4, 4)))
        assert(yrot(c, 0) === Some(31), "an untainted source contributes nothing")
      }
    }

    it("ignores integer x0 but tracks FP f0, the third operand and cross-domain moves") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        lanes(c,
          load(rob = 7, addr = int(1, 1), dst = 5),
          Op(fire = true, rob = 8, root = true, rs1 = int(1, 1), dst = (FLT, 2))) // fld
        c.clock.step()
        lanes(c)
        // x0 maps to nothing, even if its physical index happens to be tainted.
        assert(probe(c, (FIX, 0, 5)) === None)
        // FP logical register 0 is an ordinary register.
        assert(probe(c, (FLT, 0, 2)) === Some(8))
        // FP tag is per register class: integer preg 2 is untainted.
        assert(probe(c, int(2, 2)) === None)

        // fmv.x.d x9, f(p2): FP source, integer destination.
        lanes(c, Op(fire = true, rob = 9, rs1 = fp(2), dst = (FIX, 9)))
        c.clock.step()
        lanes(c)
        assert(probe(c, int(9, 9)) === Some(8))

        // fmadd with only the third operand tainted.
        lanes(c, Op(fire = true, rob = 10, rs1 = fp(1), rs2 = fp(3), rs3 = Some(2), dst = (FLT, 4)))
        assert(yrot(c, 0) === Some(8))
        c.clock.step()
        lanes(c)
        assert(probe(c, fp(4)) === Some(8))
        // An integer store's data operand counts as an input.
        lanes(c, Op(rs1 = int(1, 1), rs2 = int(5, 5)))
        assert(yrot(c, 0) === Some(7))
      }
    }

    it("clears a tag once its root is older than the VP, and never restores it") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 7)
        lanes(c, load(rob = 7, addr = int(1, 1), dst = 5))
        c.clock.step()
        lanes(c)
        assert(probe(c, int(5, 5)) === Some(7), "equality with the VP is unsafe")

        // The branch at 7 is never this load in practice; the boundary is strict either way.
        vp(c, valid = true, idx = 9)
        assert(probe(c, int(5, 5)) === None, "released in the same cycle the VP moves")
        c.clock.step()
        // A younger branch arrives later; the stored tag must stay cleared.
        vp(c, valid = true, idx = 4)
        assert(probe(c, int(5, 5)) === None)
      }
    }

    it("clears everything when no branch is unresolved") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        lanes(c,
          load(rob = 7, addr = int(1, 1), dst = 5),
          Op(fire = true, rob = 8, root = true, rs1 = int(1, 1), dst = (FLT, 2)))
        c.clock.step()
        lanes(c)
        vp(c, valid = false)
        c.clock.step()
        vp(c, valid = true, idx = 3)
        assert(probe(c, int(5, 5)) === None)
        assert(probe(c, (FLT, 0, 2)) === None)
      }
    }

    it("keeps surviving taint across a mispredict and overwrites reclaimed registers") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        // Older surviving root (p5), and a wrong-path root (p6) younger than the branch at 9.
        lanes(c, load(rob = 7, addr = int(1, 1), dst = 5))
        c.clock.step()
        lanes(c, load(rob = 10, addr = int(1, 1), dst = 6))
        c.clock.step()
        // Mispredict: the table sees no flush. The branch at 3 resolves, the one at 9 remains.
        lanes(c)
        vp(c, valid = true, idx = 9)
        c.clock.step()
        assert(probe(c, int(5, 5)) === None, "root 7 is older than the remaining VP")
        // Correct path reuses ROB slot 10 and reallocates p6 to an untainted ALU op.
        lanes(c, Op(fire = true, rob = 10, rs1 = int(1, 1), dst = (FIX, 6)))
        c.clock.step()
        lanes(c)
        assert(probe(c, int(6, 6)) === None, "a reclaimed register does not keep the squashed tag")

        // A surviving root younger than the remaining VP keeps its taint.
        lanes(c, load(rob = 11, addr = int(1, 1), dst = 7))
        c.clock.step()
        lanes(c)
        c.clock.step(3)
        assert(probe(c, int(7, 7)) === Some(11))
      }
    }

    // index() truncates a physical register to log2Ceil(n) bits. For any n that is
    // not a power of two, some encodings are never allocated, which is where a
    // cross-register aliasing mistake would hide. Sweep the real BOOM sizes
    // (Small, Medium, Large, Mega) and a few awkward ones.
    for ((nInt, nFp) <- Seq((52, 48), (80, 64), (100, 96), (128, 128), (5, 3), (9, 17))) {
      it(s"keeps every register distinct with $nInt int and $nFp FP physical registers") {
        val pSz = log2Ceil(nInt max nFp)
        val rSz = log2Ceil(nInt + nFp + 1)
        simulate(new STTTaintTable(plWidth, nInt, nFp, lregSz, pSz, rSz)) { c =>
          reset(c)
          // A VP at the head makes every tag unsafe, so nothing clears.
          vp(c, valid = true, idx = 0, head = 0)
          // A distinct root per register: int p gets ROB index p, FP p gets nInt + p.
          val writes = (0 until nInt).map(p => Op(fire = true, rob = p, root = true, rs1 = int(1, 1), dst = (FIX, p))) ++
                       (0 until nFp).map(p => Op(fire = true, rob = nInt + p, root = true, rs1 = int(1, 1), dst = (FLT, p)))
          for (group <- writes.grouped(plWidth)) {
            lanes(c, group: _*)
            c.clock.step()
          }
          lanes(c)
          for (p <- 0 until nInt) assert(probe(c, int(1, p)) === Some(p), s"int preg $p of $nInt")
          for (p <- 0 until nFp) assert(probe(c, (FLT, 1, p)) === Some(nInt + p), s"fp preg $p of $nFp")
        }
      }
    }

    it("clears all state on a full flush, except registers written that cycle") {
      simulate(dut) { c =>
        reset(c)
        vp(c, valid = true, idx = 3)
        lanes(c,
          load(rob = 7, addr = int(1, 1), dst = 5),
          Op(fire = true, rob = 8, root = true, rs1 = int(1, 1), dst = (FLT, 2)))
        c.clock.step()
        lanes(c, load(rob = 9, addr = int(1, 1), dst = 6))
        c.io.flush.poke(true.B)
        c.clock.step()
        c.io.flush.poke(false.B)
        lanes(c)
        assert(probe(c, int(5, 5)) === None)
        assert(probe(c, (FLT, 0, 2)) === None)
        assert(probe(c, int(6, 6)) === Some(9))
      }
    }
  }
}
