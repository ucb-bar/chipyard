// [boom-ustt] Age Matrix and bypass against uSTT's own test vectors (#5).
//
// Runs reference/uSTT/rtl/test/STTTestGen.py unmodified and replays its vectors.
// Two encodings differ and are translated:
//  - uSTT tags a load's result with its branch epoch (the ROB index of the
//    youngest older branch); our table tags it with the load's own ROB index.
//    Each of our tags is mapped to the epoch uSTT assigns that load. Epochs
//    never decrease in program order, so the youngest root has the largest
//    epoch and the mapping is exact even when loads share an epoch.
//  - uSTT has no untainted state and never clears tags. The VP is held at the
//    fixed branch head, so no tag is ever safe and nothing clears.
// Each vector line carries the previous group's outputs (the circuit registers
// them), so group k's renamed operands and expected tags are read from line k+1.

package boom.v3.exu

import java.io.File
import scala.collection.mutable
import scala.io.Source
import scala.sys.process._

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec

class STTAgeMatrixUSTTVectorsSpec extends AnyFunSpec
{
  // STTTestGen.py's parameters.
  val NUM_DECODE = 10
  val NUM_ARCH   = 31
  val NUM_PHY    = 380
  val NUM_ROB    = 512
  val PHY_W      = 9
  val ARCH_W     = 5
  val YROT_W     = 9
  val HEAD       = NUM_ROB / 2

  case class Inst(load: Boolean, branch: Boolean, dest: Int, src1: Int, src2: Int)
  case class Line(rat: Seq[Int], phyreg: Seq[Int], insts: Seq[Inst], outInsts: Seq[(Int, Int, Int)],
                  lastEpoch: Int, branchTail: Int, youngerOutputs: Seq[Int])

  def parse(s: String): Line = {
    var pos = 0
    def take(n: Int): Int = { val v = Integer.parseInt(s.substring(pos, pos + n), 2); pos += n; v }
    def tag(): Int = { take(1); take(YROT_W) } // drop the wrap-tracking msb
    val rat    = Seq.fill(NUM_ARCH)(take(PHY_W))
    val phyreg = Seq.fill(NUM_DECODE)(take(PHY_W))
    val insts  = Seq.fill(NUM_DECODE) {
      val ty = take(2)
      Inst(load = (ty & 1) == 1, branch = (ty & 2) == 2, dest = take(ARCH_W), src1 = take(ARCH_W), src2 = take(ARCH_W))
    }
    val outInsts = Seq.fill(NUM_DECODE)((take(PHY_W), take(PHY_W), take(PHY_W)))
    Seq.fill(NUM_ARCH)(tag()) // yrot_table, not needed
    val lastEpoch = tag()
    val branchTail = take(YROT_W)
    take(1) // wrap_bit
    val younger = Seq.fill(NUM_DECODE)(tag())
    require(pos == s.length, s"vector length ${s.length}, parsed $pos")
    Line(rat, phyreg, insts, outInsts, lastEpoch, branchTail, younger)
  }

  def repoRoot: File = {
    var d = new File(System.getProperty("user.dir")).getAbsoluteFile
    while (d != null && !new File(d, "reference/uSTT/rtl/test/STTTestGen.py").exists) d = d.getParentFile
    assume(d != null, "reference/uSTT submodule not checked out")
    d
  }

  /** Runs the generator in a fresh directory and returns its vector file. */
  def generate(run: Int): File = {
    val out = new File(repoRoot, s"logs/issue-5/sttgen/run$run")
    out.mkdirs()
    out.listFiles.foreach(_.delete())
    val gen = new File(repoRoot, "reference/uSTT/rtl/test/STTTestGen.py").getPath
    val rc = (Process(Seq("python3", "-I", gen), out) #> new File(out, "debug.txt")).!
    assert(rc == 0, s"STTTestGen.py exited with $rc")
    new File(out, s"stt_${NUM_DECODE}_$YROT_W.input")
  }

  def replay(vectors: File): (Int, Int) = {
    val lines = Source.fromFile(vectors).getLines().map(parse).toIndexedSeq
    assert(lines.size >= 2, s"$vectors has ${lines.size} vectors")
    val pregSz = 9
    val robSz  = 9
    var compared = 0
    var sameGroup = 0

    simulate(new STTTaintPair(NUM_DECODE, NUM_PHY, 0, 6, pregSz, robSz)) { c =>
      def lane(w: Int, fire: Boolean, root: Boolean, rob: Int, prs1: Int, prs2: Int, pdst: Int,
               lrs1: Int = 1, lrs2: Int = 1, srcs: Boolean = true): Unit = {
        val l = c.io.lanes(w)
        l.fire.poke(fire.B); l.is_root.poke(root.B); l.rob_idx.poke(rob.U)
        // uSTT architectural register r is our r + 1, so none is mistaken for x0.
        l.lrs1_rtype.poke((if (srcs) 0 else 2).U); l.lrs1.poke(lrs1.U); l.prs1.poke(prs1.U)
        l.lrs2_rtype.poke((if (srcs) 0 else 2).U); l.lrs2.poke(lrs2.U); l.prs2.poke(prs2.U)
        l.frs3_en.poke(false.B); l.prs3.poke(0.U)
        l.ldst_val.poke(true.B); l.dst_rtype.poke(0.U); l.pdst.poke(pdst.U)
      }
      c.io.flush.poke(false.B)
      c.io.vp.valid.poke(true.B); c.io.vp.bits.poke(HEAD.U); c.io.head.poke(HEAD.U)
      for (w <- 0 until NUM_DECODE) lane(w, fire = false, root = false, rob = 0, prs1 = 0, prs2 = 0, pdst = 0)
      c.reset.poke(true.B); c.clock.step(); c.reset.poke(false.B)

      // Prime: every mapped register starts tainted with uSTT's initial tag (the head).
      for (chunk <- lines(0).rat.grouped(NUM_DECODE)) {
        for (w <- 0 until NUM_DECODE) {
          if (w < chunk.size) lane(w, fire = true, root = true, rob = HEAD, prs1 = 0, prs2 = 0, pdst = chunk(w), srcs = false)
          else lane(w, fire = false, root = false, rob = 0, prs1 = 0, prs2 = 0, pdst = 0)
        }
        c.clock.step()
      }

      val epochOf = mutable.Map[Int, Int]().withDefault(identity)
      for (k <- 0 until lines.size - 1) {
        val in  = lines(k)
        val out = lines(k + 1)
        var epoch = in.lastEpoch
        for (w <- 0 until NUM_DECODE) {
          val i = in.insts(w)
          val rob = (in.branchTail + w) % NUM_ROB
          val (pdst, p1, p2) = out.outInsts(w)
          assert(pdst == in.phyreg(w), s"line ${k + 1} lane $w: out_insts dest != allocated phyreg")
          lane(w, fire = true, root = i.load, rob = rob, prs1 = p1, prs2 = p2, pdst = pdst,
            lrs1 = i.src1 + 1, lrs2 = i.src2 + 1)
          if (i.load) epochOf(rob) = epoch
          if (i.branch) epoch = rob
          if ((0 until w).exists(j => in.phyreg(j) == p1 || in.phyreg(j) == p2)) sameGroup += 1
        }
        for (w <- 0 until NUM_DECODE) {
          val expected = out.youngerOutputs(w)
          val ctx = s"$vectors group $k lane $w"
          for ((y, name) <- Seq((c.io.yrot(w), "bypass"), (c.io.yrot_age(w), "Age Matrix"))) {
            assert(y.valid.peek().litToBoolean, s"$name: untainted, $ctx")
            val got = y.bits.peek().litValue.toInt
            assert(epochOf(got) === expected, s"$name: root $got has epoch ${epochOf(got)}, uSTT expects $expected, $ctx")
          }
          compared += 1
        }
        c.clock.step()
      }
    }
    (compared, sameGroup)
  }

  describe("STTTaintTable on uSTT's STTTestGen.py vectors") {
    it("matches uSTT's expected YRoTs with the bypass and the Age Matrix (10-wide groups)") {
      for (run <- 1 to 5) {
        val f = generate(run)
        val (n, dep) = replay(f)
        info(s"run $run (${f.getPath}): $n lanes compared, $dep read a same-group older lane")
        assert(dep > 0, "no same-group dependencies in this run")
      }
    }
  }
}
