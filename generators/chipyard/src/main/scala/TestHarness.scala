package chipyard

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Field, Parameters}
import chipyard.iobinders.{TestHarnessFunction}
import chipyard.config.ConfigValName._

// -------------------------------
// BOOM and/or Rocket Test Harness
// -------------------------------

case object BuildTop extends Field[Parameters => HasTestHarnessFunctions]((p: Parameters) => Module(new ChipTop()(p)).suggestName("top"))

trait HasTestHarnessFunctions {
  val harnessFunctions: Seq[TestHarnessFunction]
}

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(p)
  io.success := false.B

  // dutReset can be overridden via a harnessFunction, but by default it is just reset
  val dutReset = Wire(Bool())
  dutReset := reset

  dut.harnessFunctions.foreach(_(this))

  def success = io.success
  def harnessReset = this.reset.asBool

}

