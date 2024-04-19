// SPDX-License-Identifier: Apache-2.0

package barstools.tapeout.transforms

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.stage.ChiselStage
import firrtl.FileUtils
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.{File, PrintWriter}

class BlackBoxInverter extends ExtModule {
  val in = IO(Input(Bool()))
  val out = IO(Output(Bool()))
}

class GenerateExampleModule extends Module {
  val in = IO(Input(Bool()))
  val out = IO(Output(Bool()))

  val inverter = Module(new BlackBoxInverter)
  inverter.in := in
  val inverted = inverter.out

  val reg = RegInit(0.U(8.W))
  reg := reg + inverted.asUInt
  out := reg
}

class ToBeMadeExternal extends Module {
  val in = IO(Input(Bool()))
  val out = IO(Output(Bool()))

  val reg = RegInit(0.U(8.W))
  reg := reg + in.asUInt + 2.U
  out := reg
}

class GenerateExampleTester extends Module {
  val success = IO(Output(Bool()))

  val mod = Module(new GenerateExampleModule)
  mod.in := 1.U

  val mod2 = Module(new ToBeMadeExternal)
  mod2.in := 1.U

  val reg = RegInit(0.U(8.W))
  reg := reg + mod.out + mod2.out

  success := reg === 100.U

  when(reg === 100.U) {
    stop()
  }
}

class GenerateSpec extends AnyFreeSpec {

  def generateTestData(targetDir: String): Unit = {
    FileUtils.makeDirectory(targetDir)

    new ChiselStage().emitFirrtl(new GenerateExampleTester, Array("--target-dir", targetDir))

    val blackBoxInverterText =
      """
        |module BlackBoxInverter(
        |    input  [0:0] in,
        |    output [0:0] out
        |);
        |  assign out = !in;
        |endmodule
        |""".stripMargin

    val printWriter2 = new PrintWriter(new File(s"$targetDir/BlackBoxInverter.v"))
    printWriter2.write(blackBoxInverterText)
    printWriter2.close()
  }

  "generate test data" in {
    val targetDir = "test_run_dir/generate_spec_source"
    generateTestData(targetDir)

    new File(s"$targetDir/GenerateExampleTester.fir").exists() should be(true)
  }

  "generate top test" in {
    val targetDir = "test_run_dir/generate_spec"
    generateTestData(targetDir)

    GenerateModelStageMain.main(
      Array(
        "-i",
        s"$targetDir/GenerateExampleTester.fir",
        "-o",
        s"$targetDir/GenerateExampleTester.v"
      )
    )
    new File(s"$targetDir/GenerateExampleTester.v").exists() should be(true)
  }
}
