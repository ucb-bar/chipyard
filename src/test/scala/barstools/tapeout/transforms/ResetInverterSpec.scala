// See LICENSE for license details.

package barstools.tapeout.transforms

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import firrtl.{EmittedFirrtlCircuitAnnotation, EmittedFirrtlModuleAnnotation}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ExampleModuleNeedsResetInverted extends Module with ResetInverter {
  val io = IO(new Bundle {
    val out = Output(UInt(32.W))
  })

  val r = RegInit(0.U)

  io.out := r

  invert(this)
}

class ResetNSpec extends AnyFreeSpec with Matchers {
  "Inverting reset needs to be done throughout module in Chirrtl" in {
    val chirrtl = (new ChiselStage).emitChirrtl(new ExampleModuleNeedsResetInverted, Array("--target-dir", "test_run_dir/reset_n_spec"))
    chirrtl should include("input reset :")
    (chirrtl should not).include("input reset_n :")
    (chirrtl should not).include("node reset = not(reset_n)")
  }

  "Inverting reset needs to be done throughout module when generating firrtl" in {
    // generate low-firrtl
    val firrtl = (new ChiselStage)
      .execute(
        Array("-X", "low", "--target-dir", "test_run_dir/reset_inverting_spec"),
        Seq(ChiselGeneratorAnnotation(() => new ExampleModuleNeedsResetInverted))
      )
      .collect {
        case EmittedFirrtlCircuitAnnotation(a) => a
        case EmittedFirrtlModuleAnnotation(a)  => a
      }
      .map(_.value)
      .mkString("")

    firrtl should include("input reset_n :")
    firrtl should include("node reset = not(reset_n)")
    (firrtl should not).include("input reset :")
  }
}
