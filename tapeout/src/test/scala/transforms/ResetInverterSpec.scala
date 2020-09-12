// See LICENSE for license details.

package barstools.tapeout.transforms

import chisel3._
import chisel3.stage.ChiselStage
import org.scalatest.{FreeSpec, Matchers}

class ExampleModuleNeedsResetInverted extends Module with ResetInverter {
  val io = IO(new Bundle {
    val out = Output(UInt(32.W))
  })

  val r = RegInit(0.U)

  io.out := r

  invert(this)
}

class ResetNSpec extends FreeSpec with Matchers {
  "Inverting reset needs to be done throughout module" in {
    val chirrtl = (new ChiselStage).emitChirrtl(new ExampleModuleNeedsResetInverted, Array())
    chirrtl should include("input reset :")
    (chirrtl should not).include("input reset_n :")
    (chirrtl should not).include("node reset = not(reset_n)")

    val firrtl = (new ChiselStage).emitFirrtl(new ExampleModuleNeedsResetInverted, Array("-X", "low"))
    firrtl should include("input reset_n :")
    firrtl should include("node reset = not(reset_n)")
    (firrtl should not).include("input reset :")
  }
}
