// SPDX-License-Identifier: Apache-2.0

package barstools.tapeout.transforms

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.stage.ChiselStage
import firrtl.FileUtils
import org.scalatest.freespec.AnyFreeSpec

import java.io.{File, PrintWriter}

class BlackBoxInverter extends ExtModule {
  val in = IO(Input(Bool()))
  val out = IO(Output(Bool()))
}

class GenerateExampleModule extends MultiIOModule {
  val in = IO(Input(Bool()))
  val out = IO(Output(Bool()))

  val inverter = Module(new BlackBoxInverter)
  inverter.in := in
  val inverted = inverter.out

  val reg = RegInit(0.U(8.W))
  reg := reg + inverted.asUInt
  out := reg
}

class ToBeMadeExternal extends MultiIOModule {
  val in = IO(Input(Bool()))
  val out = IO(Output(Bool()))

  val reg = RegInit(0.U(8.W))
  reg := reg + in.asUInt + 2.U
  out := reg
}

class GenerateExampleTester extends MultiIOModule {
  val success = IO(Output(Bool()))

  val  mod = Module(new GenerateExampleModule)
  mod.in := 1.U

  val  mod2 = Module(new ToBeMadeExternal)
  mod2.in := 1.U

  val reg = RegInit(0.U(8.W))
  reg := reg + mod.out + mod2.out

  success := reg === 100.U

  when(reg === 100.U) {
    stop()
  }
}

class GenerateSpec extends AnyFreeSpec {
  "generate test data" in {
    val targetDir = "test_run_dir/generate_spec_source"
    FileUtils.makeDirectory(targetDir)

    val printWriter = new PrintWriter(new File(s"$targetDir/GenerateExampleTester.fir"))
    printWriter.write((new ChiselStage()).emitFirrtl(new GenerateExampleTester, Array("--target-dir", targetDir)))
    printWriter.close()

    val blackBoxInverterText = """
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

  "generate top test" in {
    val sourceDir = "test_run_dir/generate_spec_source"
    val targetDir = "test_run_dir/generate_spec"

    GenerateTop.main(Array(
      "-i", s"$sourceDir/GenerateExampleTester.fir",
      "-o", s"$targetDir/GenerateExampleTester.v"
    ))
  }
}
