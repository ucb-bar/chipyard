// See LICENSE for license details.

package barstools.tapeout.transforms.clkgen

import chisel3._
import firrtl._
import org.scalatest.{FlatSpec, Matchers}
import chisel3.experimental._
import chisel3.iotesters._
import chisel3.util.HasBlackBoxInline
import barstools.tapeout.transforms.pads.TopModule

// Purely to see that clk src tagging works with BBs
class FakeBBClk extends BlackBox with HasBlackBoxInline with IsClkModule {
  val io = IO(new Bundle {
    val inClk = Input(Clock())
    val outClk = Output(Vec(3, Clock())) 
  })

  annotateClkPort(io.inClk, Sink())
  val generatedClks = io.outClk.map { case elt => 
    val id = getIOName(elt)
    val srcId = getIOName(io.inClk)
    annotateClkPort(elt.asInstanceOf[Element]) 
    GeneratedClk(id, Seq(srcId), Seq(0, 1, 2))
  }.toSeq

  annotateDerivedClks(ClkDiv, generatedClks)

  // Generates a "FakeBB.v" file with the following Verilog module
  setInline("FakeBBClk.v",
    s"""
      |module FakeBBClk(
      |  input inClk,
      |  output outClk_0,
      |  output outClk_1,
      |  output outClk_2
      |);
      |  always @* begin
      |    outClk_0 = inClk;
      |    outClk_1 = inClk;
      |    outClk_2 = inClk;      
      |  end
      |endmodule
    """.stripMargin)
}

class ModWithNestedClkIO(numPhases: Int) extends Bundle {
  val inClk = Input(Clock())
  val bbOutClk = Output(Vec(3, Clock()))
  val clkDivOut = Output(Vec(numPhases, Clock()))
}

class TestModWithNestedClkIO(numPhases: Int) extends Bundle {
  val bbOutClk = Output(Vec(3, Bool()))
  val clkDivOut = Output(Vec(numPhases, Bool()))
}

class ModWithNestedClk(divBy: Int, phases: Seq[Int], syncReset: Boolean) extends Module {

  val io = IO(new ModWithNestedClkIO(phases.length))

  val bb = Module(new FakeBBClk)
  bb.io.inClk := io.inClk
  io.bbOutClk := bb.io.outClk
  val clkDiv = Module(new SEClkDivider(divBy, phases, syncReset = syncReset))
  clkDiv.io.reset := reset
  clkDiv.io.inClk := io.inClk
  phases.zipWithIndex.foreach { case (phase, idx) => io.clkDivOut(idx) := clkDiv.io.outClks(phase) }

}

class TopModuleWithClks(val divBy: Int, val phases: Seq[Int]) extends TopModule(usePads = false) {
  val io = IO(new Bundle {
    val gen1 = new TestModWithNestedClkIO(phases.length)
    val gen2 = new TestModWithNestedClkIO(phases.length) 
    val gen3 = new TestModWithNestedClkIO(phases.length)
    val fakeClk1 = Input(Clock())
    val fakeClk2 = Input(Clock())
  })

  // TODO: Don't have to type Some
  annotateClkPort(clock, 
    id = "clock", // not in io bundle
    sink = Sink(Some(ClkSrc(period = 5.0, async = Seq(getIOName(io.fakeClk1)))))
  )
  annotateClkPort(io.fakeClk1, Sink(Some(ClkSrc(period = 4.0))))
  annotateClkPort(io.fakeClk2, Sink(Some(ClkSrc(period = 3.0))))

  // Most complicated: test chain of clock generators
  val gen1 = Module(new ModWithNestedClk(divBy, phases, syncReset = true))
  io.gen1.bbOutClk := Vec(gen1.io.bbOutClk.map(x => x.asUInt))
  io.gen1.clkDivOut := Vec(gen1.io.clkDivOut.map(x => x.asUInt))
  gen1.io.inClk := clock
  // ClkDiv on generated clk -> reset occurs before first input clk edge
  val gen2 = Module(new ModWithNestedClk(divBy, phases, syncReset = false))
  io.gen2.bbOutClk := Vec(gen2.io.bbOutClk.map(x => x.asUInt))
  io.gen2.clkDivOut := Vec(gen2.io.clkDivOut.map(x => x.asUInt))
  gen2.io.inClk := gen1.io.clkDivOut.last
  val gen3 = Module(new ModWithNestedClk(divBy, phases, syncReset = false))
  io.gen3.bbOutClk := Vec(gen3.io.bbOutClk.map(x => x.asUInt))
  io.gen3.clkDivOut := Vec(gen3.io.clkDivOut.map(x => x.asUInt))
  gen3.io.inClk := gen1.io.clkDivOut.last
}

class TopModuleWithClksTester(c: TopModuleWithClks) extends PeekPokeTester(c) {
  val maxT = c.divBy * c.divBy * 4
  val numSubClkOutputs = c.io.gen1.clkDivOut.length
  val gen1Out = Seq.fill(numSubClkOutputs)(scala.collection.mutable.ArrayBuffer[Int]())
  val gen2Out = Seq.fill(numSubClkOutputs)(scala.collection.mutable.ArrayBuffer[Int]())
  val gen3Out = Seq.fill(numSubClkOutputs)(scala.collection.mutable.ArrayBuffer[Int]())
  reset(10)
  for (t <- 0 until maxT) {
    for (k <- 0 until numSubClkOutputs) {
      gen1Out(k) += peek(c.io.gen1.clkDivOut(k)).intValue
      gen2Out(k) += peek(c.io.gen2.clkDivOut(k)).intValue
      gen3Out(k) += peek(c.io.gen3.clkDivOut(k)).intValue
    }
    step(1)
  }

  val clkCounts = (0 until maxT)
  val clkCountsModDiv = clkCounts.map(_ % c.divBy)
  for (k <- 0 until numSubClkOutputs) {
    val expected = clkCountsModDiv.map(x => if (x == c.phases(k)) 1 else 0)
    expect(gen1Out(k) == expected, s"gen1Out($k) incorrect!")
    println(s"gen1Out($k): \t${gen1Out(k).mkString("")}")
  }

  val gen1ClkCounts = (0 until maxT/c.divBy).map(i => Seq.fill(c.divBy)(i)).flatten
  val gen1ClkCountsModDiv = gen1ClkCounts.map(_ % c.divBy)

  for (k <- 0 until numSubClkOutputs) {
    // Handle initial transient
    val fillVal = if (c.phases.last == c.divBy - 1 && k == numSubClkOutputs - 1) 1 else 0
    val expected = Seq.fill(c.phases.last)(fillVal) ++ 
      gen1ClkCountsModDiv.map(x => if (x == c.phases(k)) 1 else 0).dropRight(c.phases.last)
    expect(gen2Out(k) == expected, s"gen1Out($k) incorrect!")
    println(s"gen2Out($k): \t${gen2Out(k).mkString("")}")
    println(s"expected: \t${expected.mkString("")}")
  }

  expect(gen2Out == gen3Out, "gen2Out should equal gen3Out")

}  

class ClkGenSpec extends FlatSpec with Matchers {

  def readOutputFile(dir: String, f: String): String = 
    scala.io.Source.fromFile(Seq(dir, f).mkString("/")).getLines.mkString("\n")
  def readResource(resource: String): String = {
    val stream = getClass.getResourceAsStream(resource)
    scala.io.Source.fromInputStream(stream).mkString
  }

  def checkOutputs(dir: String) = {
  }

  behavior of "top module with clk gens"

  it should "pass simple testbench" in {
    val optionsManager = new TesterOptionsManager {
      firrtlOptions = firrtlOptions.copy(
        compilerName = "verilog"
        /*annotations = List(passes.clocklist.ClockListAnnotation(
          s"-c:TopModuleWithClks:-m:TopModuleWithClks:-o:test.clk"
        )),
        customTransforms = Seq(new passes.clocklist.ClockListTransform())*/
      )
      testerOptions = testerOptions.copy(isVerbose = false, backendName = "verilator", displayBase = 10)
      commonOptions = commonOptions.copy(targetDirName = "test_run_dir/ClkTB")
    }
    // WARNING: TB requires that phase divBy - 1 should be at the end of the Seq to be OK during initial transient
    iotesters.Driver.execute(() => new TopModuleWithClks(4, Seq(0, 1, 3)), optionsManager) { c =>
      val dir = optionsManager.commonOptions.targetDirName
      checkOutputs(dir)   
      new TopModuleWithClksTester(c)
    } should be (true)
  }

}