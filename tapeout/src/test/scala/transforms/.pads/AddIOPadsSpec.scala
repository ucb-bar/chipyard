// See LICENSE for license details.

package barstools.tapeout.transforms.pads

import java.io.File

import barstools.tapeout.transforms.HasSetTechnologyLocation
import chisel3._
import chisel3.experimental._
import chisel3.iotesters._
import chisel3.stage.ChiselStage
import chisel3.util.HasBlackBoxInline
import firrtl._
import org.scalatest.{FlatSpec, Matchers}

class BB extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val c = Input(SInt(14.W))
    val z = Output(SInt(16.W))
    val analog1 = Analog(3.W)
    val analog2 = Analog(3.W)
  })
  // Generates a "FakeBB.v" file with the following Verilog module
  setInline(
    "FakeBB.v",
    s"""
       |module BB(
       |  input [15:0] c,
       |  output [15:0] z,
       |  inout [2:0] analog1,
       |  inout [2:0] analog2
       |);
       |  always @* begin
       |    z = 2 * c;
       |    analog2 = analog1 + 1;
       |  end
       |endmodule
    """.stripMargin
  )
}

// If no template file is provided, it'll use the default one (example) in the resource folder
// Default pad side is Top if no side is specified for a given IO
// You can designate the number of different supply pads on each chip side
class ExampleTopModuleWithBB
    extends TopModule(
      supplyAnnos = Seq(
        SupplyAnnotation(padName = "vdd", leftSide = 3, bottomSide = 2),
        SupplyAnnotation(padName = "vss", rightSide = 1)
      )
    )
    with HasSetTechnologyLocation {
  val io = IO(new Bundle {
    val a = Input(UInt(15.W))
    val b = Input(a.cloneType)
    val c = Input(SInt(14.W))
    val x = Output(UInt(16.W))
    val y = Output(x.cloneType)
    val z = Output(SInt(16.W))
    val analog1 = Analog(3.W)
    val analog2 = analog1.cloneType
    val v = Output(Vec(3, UInt(5.W)))
  })

  setTechnologyLocation("./RealTech")

  // Can annotate aggregates with pad side location + pad name (should be a name in the yaml template)
  annotatePad(io.v, Right, "from_tristate_foundry")
  // Can annotate individual elements
  annotatePad(io.analog1, Left, "fast_custom")
  annotatePad(io.analog2, Bottom, "slow_foundry")
  // Looks for a pad that matches the IO type (digital in, digital out, analog) if no name is specified
  Seq(io.a, io.b, io.c, io.x).foreach { x => annotatePad(x, Left) }
  // Some signals might not want pads associated with them
  noPad(io.y)
  // Clk might come directly from bump
  noPad(clock)

  val bb = Module(new BB())
  bb.io.c := io.c
  io.z := bb.io.z
  bb.io.analog1 <> io.analog1
  bb.io.analog2 <> io.analog2

  io.x := io.a + 1.U
  io.y := io.b - 1.U

  io.v.foreach { lhs => lhs := io.a }

}

class SimpleTopModuleTester(c: ExampleTopModuleWithBB) extends PeekPokeTester(c) {
  val ax = Seq(5, 3)
  val bx = Seq(8, 2)
  val cx = Seq(-11, -9)
  for (i <- 0 until ax.length) {
    poke(c.io.a, ax(i))
    poke(c.io.b, bx(i))
    poke(c.io.c, cx(i))
    expect(c.io.x, ax(i) + 1)
    expect(c.io.y, bx(i) - 1)
    expect(c.io.z, 2 * cx(i))
    c.io.v.foreach { out => expect(out, ax(i)) }
  }
  // Analog can't be peeked + poked
}

// Notes: Annotations
// a in 15: left, default digital
// b in 15: left, default digital
// c in 14: left, default digital ; signed
// x out 16: left, default digital
// y out: NOPAD
// clk in: NOPAD
// analog1 3: left, fast_custom
// analog2 3: bottom, slow_foundry
// v (vec of 3 with 5, out): right, from_tristate_foundry
// reset in: UNSPECIFIED: top, default digital
// z out 16: UNSPECIFIED: top, default digital ; signed
// vdd, left: 3, group of 1
// vdd, bottom: 2, group of 1
// vss, right: 1, group of 2
// Notes: Used pads
// digital horizontal (from_tristate_foundry)
//  in + out
// analog fast_custom horizontal
// analog slow_foundry vertical
// digital vertical (from_tristate_foundry)
//  in + out
// vdd horizontal
// vdd vertical
// vss horizontal

class IOPadSpec extends FlatSpec with Matchers {

  def readOutputFile(dir: String, f: String): String = {
    FileUtils.getText(dir + File.separator + f)
  }
  def readResource(resource: String): String = {
    val stream = getClass.getResourceAsStream(resource)
    scala.io.Source.fromInputStream(stream).mkString
  }

  def checkOutputs(dir: String): Unit = {
    // Show that black box source helper is run
    //readOutputFile(dir, "black_box_verilog_files.f") should include ("pad_supply_vdd_horizontal.v")

    val padBBEx = s"""// Digital Pad Example
                     |// Signal Direction: Input
                     |// Pad Orientation: Horizontal
                     |// Call your instance PAD
                     |module pad_digital_from_tristate_foundry_horizontal_input(
                     |  input in,
                     |  output reg out
                     |);
                     |  // Where you would normally dump your pad instance
                     |  always @* begin
                     |    out = in;
                     |  end
                     |endmodule
                     |
                     |module pad_digital_from_tristate_foundry_horizontal_input_array #(
                     |  parameter int WIDTH=1
                     |)(
                     |  input [WIDTH-1:0] in,
                     |  output reg [WIDTH-1:0] out
                     |);
                     |  pad_digital_from_tristate_foundry_horizontal_input pad_digital_from_tristate_foundry_horizontal_input[WIDTH-1:0](
                     |    .in(in),
                     |    .out(out)
                     |  );""".stripMargin
    // Make sure black box templating is OK
    readOutputFile(dir, "pad_digital_from_tristate_foundry_horizontal_input_array.v") should include(padBBEx)

    val verilog = readOutputFile(dir, "ExampleTopModuleWithBB.v")
    // Pad frame + top should be exact
    verilog should include(readResource("/PadAnnotationVerilogPart.v"))
    // Pad Placement IO file should be exact
    val padIO = readOutputFile(dir, "pads.io")
    padIO should include(readResource("/PadPlacement.io"))
  }

  behavior.of("Pad Annotations")

  it should "serialize pad annotations" in {
    val noIOPadAnnotation = NoIOPadAnnotation("dog")
    noIOPadAnnotation.serialize should include("noPad: dog")

    val ioPadAnnotation = IOPadAnnotation("left", "oliver")
    ioPadAnnotation.serialize should include(
      """padSide: left
        |padName: oliver
        |""".stripMargin)

    val modulePadAnnotation = ModulePadAnnotation(
      "top",
      11,
      42,
      Seq(
        SupplyAnnotation("mypad, 1, 2 ,3 , 4"),
        SupplyAnnotation("yourpad, 9, 8, 7, 6")
      )
    )

    modulePadAnnotation.serialize should be(
      """defaultPadSide: top
        |coreWidth: 11
        |coreHeight: 42
        |supplyAnnos:
        |- rightSide: 0
        |  padName: mypad, 1, 2 ,3 , 4
        |  leftSide: 0
        |  bottomSide: 0
        |  topSide: 0
        |- rightSide: 0
        |  padName: yourpad, 9, 8, 7, 6
        |  leftSide: 0
        |  bottomSide: 0
        |  topSide: 0
        |""".stripMargin
    )
  }

  behavior.of("top module with blackbox")

  it should "pass simple testbench" in {
    val optionsManager = new TesterOptionsManager {
      firrtlOptions = firrtlOptions.copy(
        compilerName = "verilog"
      )
      testerOptions = testerOptions.copy(isVerbose = true, backendName = "verilator", displayBase = 10)
      commonOptions = commonOptions.copy(targetDirName = "test_run_dir/PadsTB")
    }
    iotesters.Driver.execute(() => new ExampleTopModuleWithBB, optionsManager) { c =>
      val dir = optionsManager.commonOptions.targetDirName
      checkOutputs(dir)
      new SimpleTopModuleTester(c)
    } should be(true)
  }
  /*
  it should "create proper IO pads + black box in low firrtl" in {
    val optionsManager = new ExecutionOptionsManager("barstools") with HasChiselExecutionOptions with HasFirrtlOptions {
      firrtlOptions = firrtlOptions.copy(compilerName = "low")
      commonOptions = commonOptions.copy(targetDirName = "test_run_dir/LoFirrtl")
      //commonOptions = commonOptions.copy(globalLogLevel = logger.LogLevel.Info)
    }
    val success = chisel3.Driver.execute(optionsManager, () => new ExampleTopModuleWithBB) match {
      case ChiselExecutionSuccess(_, chirrtl, Some(FirrtlExecutionSuccess(_, firrtl))) =>
        firrtl should include ("ExampleTopModuleWithBB_PadFrame")
        firrtl should include ("ExampleTopModuleWithBB_Internal")
        firrtl should not include ("FakeBBPlaceholder")
        true
      case _ => false
    }
    success should be (true)
  }
   */
  it should "create proper IO pads + black box in verilog" in {
    val dir = "test_run_dir/PadsVerilog"
    (new ChiselStage).emitFirrtl(
      new ExampleTopModuleWithBB,
      Array("-td", dir, "-X", "verilog")
    )
    checkOutputs(dir)
  }

}
