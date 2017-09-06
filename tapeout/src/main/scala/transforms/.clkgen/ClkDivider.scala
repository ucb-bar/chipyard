package barstools.tapeout.transforms.clkgen

import chisel3.experimental.{withClockAndReset, withClock, withReset}
import chisel3._
import barstools.tapeout.transforms._
import chisel3.util.HasBlackBoxInline

// WARNING: ONLY WORKS WITH VERILATOR B/C YOU NEED ASYNC RESET!

class SEClkDividerIO(phases: Seq[Int]) extends Bundle {
  val reset = Input(Bool())
  val inClk = Input(Clock())
  val outClks = Output(CustomIndexedBundle(Clock(), phases))
  override def cloneType = (new SEClkDividerIO(phases)).asInstanceOf[this.type]
}

class SEClkDividerBB(phases: Seq[Int], f: String) extends BlackBox with HasBlackBoxInline {
  val verilog = scala.io.Source.fromFile(f).getLines.mkString("\n")
  // names without io
  val io = IO(new SEClkDividerIO(phases))
  val modName = this.getClass.getSimpleName
  require(verilog contains modName, "Clk divider Verilog module must be named ClkDividerBB")
  io.elements foreach { case (field, elt) => 
    require(verilog contains field, s"Verilog file should contain io ${field}")}
  setInline(s"${modName}.v", verilog)
}

class AsyncRegInit extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val reset = Input(Bool())
    val init = Input(Bool())
    val in = Input(Bool())
    val out = Output(Bool())
  })

  setInline("AsyncRegInit.v",
    s"""
      |module AsyncRegInit(
      |  input clk,
      |  input reset,
      |  input init,
      |  input in,
      |  output reg out
      |);
      |  always @ (posedge clk or posedge reset) begin
      |    if (reset) begin
      |      out <= init;
      |    end else begin
      |      out <= in;     
      |    end
      |  end
      |endmodule
    """.stripMargin)
}

object AsyncRegInit {
  def apply(clk: Clock, reset: Bool, init: Bool): AsyncRegInit = {
    val asyncRegInit = Module(new AsyncRegInit)
    asyncRegInit.io.clk := clk
    asyncRegInit.io.reset := reset
    asyncRegInit.io.init := init
    asyncRegInit
  }
}

// TODO: Convert analogFile into implicit?
// If syncReset = false, it's implied that reset is strobed before any clk rising edge happens
// i.e. when this is a clkgen fed by another clkgen --> need to adjust the indexing b/c
// you're already shifting on the first clk rising edge
class SEClkDivider(divBy: Int, phases: Seq[Int], analogFile: String = "", syncReset: Boolean = true) 
    extends Module with IsClkModule {

  require(phases.distinct.length == phases.length, "Phases should be distinct!")
  phases foreach { p => 
    require(p < divBy, "Phases must be < divBy")
  }

  val io = IO(new SEClkDividerIO(phases))

  annotateClkPort(io.inClk, Sink())

  val referenceEdges = phases.map(p => Seq(2 * p, 2 * (p + 1), 2 * (p + divBy)))

  val generatedClks = io.outClks.elements.zip(referenceEdges).map { case ((field, eltx), edges) =>
    val elt = eltx.asInstanceOf[Element]
    annotateClkPort(elt) 
    GeneratedClk(getIOName(elt), sources = Seq(getIOName(io.inClk)), edges)
  }.toSeq

  annotateDerivedClks(ClkDiv, generatedClks)

  require(divBy >= 1, "Clk division factor must be >= 1")

  divBy match {
    case i: Int if i == 1 => 
      require(phases == Seq(0), "Clk division by 1 shouldn't generate new phases")
      io.outClks(0) := io.inClk
    case i: Int if i > 1 && analogFile == "" =>
      // Shift register based clock divider (duty cycle is NOT 50%)
      val initVals = Seq(true.B) ++ Seq.fill(divBy - 1)(false.B)

      /************ Real design assumes asnyc reset!!!
      withClockAndReset(io.inClk, io.reset) {
        val regs = initVals.map(i => RegInit(i))
        // Close the loop
        regs.head := regs.last
        // Shift register
        regs.tail.zip(regs.init) foreach { case (lhs, rhs) => lhs := rhs }
        // Assign register output to correct clk out
        phases foreach { idx => io.outClks(idx) := regs(idx).asClock }
      }
      *************/

      val regs = initVals.map(i => AsyncRegInit(io.inClk, io.reset, i))
      regs.head.io.in := regs.last.io.out
      regs.tail.zip(regs.init) foreach { case (lhs, rhs) => lhs.io.in := rhs.io.out }
      phases foreach { idx => 
        val regIdx = if (syncReset) idx else (idx + 1) % divBy
        io.outClks(idx) := regs(regIdx).io.out.asClock 
      }

    case _ =>
      if (new java.io.File(analogFile).exists) {
        val bb = Module(new SEClkDividerBB(phases, analogFile))
        io <> bb.io
      }
      else throw new Exception("Clock divider Verilog file invalid!")
  }
}
