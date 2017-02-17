// See LICENSE for license details.

package barstools.tapeout.transforms

import chisel3._
import chisel3.util.RegInit
import firrtl._
import org.scalatest.{FreeSpec, Matchers}

class ExampleModuleNeedsResetInverted extends Module with ResetInverter {
  val io = IO(new Bundle {
    val out = Output(UInt(32.W))
  })

  val r = RegInit(0.U)

  invert(this)
}

class ResetNSpec extends FreeSpec with Matchers {

  "Inverting reset needs to be done throughout module" in {
    val optionsManager = new ExecutionOptionsManager("dsptools") with HasChiselExecutionOptions with HasFirrtlOptions {
      firrtlOptions = firrtlOptions.copy(compilerName = "low")
    }
    chisel3.Driver.execute(optionsManager, () => new ExampleModuleNeedsResetInverted) match {
      case ChiselExecutionSuccess(_, chirrtl, Some(FirrtlExecutionSuccess(_, firrtl))) =>
        chirrtl should include ("input reset :")
        chirrtl should not include "input reset_n :"
        chirrtl should not include "node reset = not(reset_n)"

        firrtl should include ("input reset_n :")
        firrtl should include ("node reset = not(reset_n)")
        firrtl should not include "input reset :"
      case _ =>
        // bad
    }
  }
}