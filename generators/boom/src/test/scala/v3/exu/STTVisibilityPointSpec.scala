// [boom-ustt] Module tests for the STT visibility point and isSafe (#4).

package boom.v3.exu

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec

import boom.v3.common.STTIsSafe

class STTIsSafeHarness(robAddrSz: Int) extends Module
{
  val io = IO(new Bundle {
    val yrot = Input(Valid(UInt(robAddrSz.W)))
    val vp   = Input(Valid(UInt(robAddrSz.W)))
    val head = Input(UInt(robAddrSz.W))
    val safe = Output(Bool())
  })
  io.safe := STTIsSafe(io.yrot, io.vp, io.head)
}

class STTIsSafeSpec extends AnyFunSpec
{
  describe("STTIsSafe") {
    it("matches the spec's Small ROB cases (spec-interface.md section 4)") {
      // (head, vp valid, vp, yrot valid, yrot, safe)
      val cases = Seq(
        (28, true,  2, true,  30, true),
        (28, true,  2, true,  4,  false),
        (28, true,  2, true,  2,  false),
        (28, true,  2, false, 0,  true),
        (28, false, 0, true,  4,  true),
        (0,  true,  9, true,  8,  true),
        (0,  true,  9, true,  31, false))
      simulate(new STTIsSafeHarness(5)) { c =>
        for ((head, vpv, vp, yv, y, safe) <- cases) {
          c.io.head.poke(head.U)
          c.io.vp.valid.poke(vpv.B)
          c.io.vp.bits.poke(vp.U)
          c.io.yrot.valid.poke(yv.B)
          c.io.yrot.bits.poke(y.U)
          c.io.safe.expect(safe.B, s"head=$head vp=($vpv,$vp) yrot=($yv,$y)")
        }
      }
    }
  }
}

class STTVisibilityPointSpec extends AnyFunSpec
{
  // Medium-like banking: 2 banks of 16 rows, flat index row << 1 | bank.
  val rows = 16
  val width = 2
  val maxBr = 4

  def dut = new STTVisibilityPoint(rows, width, maxBr)

  /** Test-side model of the ROB state the module reads. */
  class Rob(c: STTVisibilityPoint) {
    val valid = Array.fill(rows * width)(false)
    val tag   = Array.fill(rows * width)(0)
    def drive(): Unit = for (i <- 0 until rows * width) {
      c.io.entry_valid(i).poke(valid(i).B)
      c.io.entry_br_tag(i).poke(tag(i).U)
    }
  }

  def idle(c: STTVisibilityPoint): Unit = {
    for (w <- 0 until width) {
      c.io.enq_valids(w).poke(false.B)
      c.io.enq_is_br(w).poke(false.B)
    }
    c.io.resolve_mask.poke(0.U)
  }

  def reset(c: STTVisibilityPoint): Rob = {
    val rob = new Rob(c)
    idle(c)
    rob.drive()
    c.io.enq_row.poke(0.U)
    c.io.head.poke(0.U)
    c.reset.poke(true.B)
    c.clock.step()
    c.reset.poke(false.B)
    rob
  }

  /** Enqueue one row, as the ROB does: entry state updates at the same edge. */
  def enq(c: STTVisibilityPoint, rob: Rob, row: Int, lanes: Seq[Option[Int]]): Unit = {
    c.io.enq_row.poke(row.U)
    for ((l, w) <- lanes.zipWithIndex) {
      c.io.enq_valids(w).poke(true.B)
      c.io.enq_is_br(w).poke(l.isDefined.B)
    }
    c.clock.step()
    idle(c)
    for ((l, w) <- lanes.zipWithIndex) {
      rob.valid(row * width + w) = true
      rob.tag(row * width + w) = l.getOrElse(0)
    }
    rob.drive()
  }

  def vp(c: STTVisibilityPoint): Option[Int] =
    if (c.io.vp.valid.peek().litToBoolean) Some(c.io.vp.bits.peek().litValue.toInt) else None

  describe("STTVisibilityPoint") {
    it("is invalid with a zero payload when no branch is pending") {
      simulate(dut) { c =>
        val rob = reset(c)
        assert(vp(c) === None)
        c.io.vp.bits.expect(0.U)
        enq(c, rob, 0, Seq(None, None))
        assert(vp(c) === None, "non-branch entries are not VPs")
      }
    }

    it("names the oldest unresolved branch, including bank position") {
      simulate(dut) { c =>
        val rob = reset(c)
        enq(c, rob, 2, Seq(None, Some(0)))   // branch at flat index 5
        enq(c, rob, 4, Seq(Some(1), None))   // branch at flat index 8
        assert(vp(c) === Some(5))
      }
    }

    it("advances only past resolved branches when they resolve out of order") {
      simulate(dut) { c =>
        val rob = reset(c)
        enq(c, rob, 2, Seq(None, Some(0)))
        enq(c, rob, 4, Seq(Some(1), None))
        enq(c, rob, 5, Seq(Some(2), None))
        c.io.resolve_mask.poke("b0010".U)    // younger branch at 8 first
        assert(vp(c) === Some(5))
        c.clock.step()
        idle(c)
        assert(vp(c) === Some(5))
        c.io.resolve_mask.poke("b0001".U)    // now the oldest
        assert(vp(c) === Some(5), "the resolving branch is still the VP during its cycle")
        c.clock.step()
        idle(c)
        assert(vp(c) === Some(10), "advanced in the next cycle, skipping resolved index 8")
      }
    }

    it("keeps a new branch that reuses a tag resolving in the same cycle") {
      simulate(dut) { c =>
        val rob = reset(c)
        enq(c, rob, 1, Seq(Some(0), None))   // index 2, tag 0
        // Tag 0 resolves while a new branch with tag 0 enters row 3. The new
        // slot's stored tag is stale (0) this cycle, as in the ROB.
        c.io.resolve_mask.poke("b0001".U)
        enq(c, rob, 3, Seq(None, Some(0)))   // index 7
        assert(vp(c) === Some(7))
      }
    }

    it("orders wrapped entries relative to the head") {
      simulate(dut) { c =>
        val rob = reset(c)
        enq(c, rob, 1, Seq(Some(0), None))   // index 2
        enq(c, rob, 15, Seq(Some(1), None))  // index 30
        c.io.head.poke(28.U)
        assert(vp(c) === Some(30))
        c.io.head.poke(0.U)
        assert(vp(c) === Some(2))
      }
    }

    it("drops killed or retired entries") {
      simulate(dut) { c =>
        val rob = reset(c)
        enq(c, rob, 2, Seq(None, Some(0)))   // index 5
        enq(c, rob, 4, Seq(Some(1), None))   // index 8
        rob.valid(5) = false
        rob.drive()
        assert(vp(c) === Some(8))
        rob.valid(8) = false
        rob.drive()
        assert(vp(c) === None)
        // The stale pending bit must not survive reuse of the slot by a non-branch.
        enq(c, rob, 2, Seq(None, None))
        assert(vp(c) === None)
      }
    }
  }
}
