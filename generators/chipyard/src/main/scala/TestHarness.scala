package chipyard

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Field, Parameters}
import chipyard.iobinders.types.{TestHarnessFunction}
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
  // resetOverride can be overridden via a harnessFunction
  val resetOverride = Wire(Bool())
  resetOverride := reset
  dut.harnessFunctions.foreach(_(this))

  // Aliases for clock, reset, and success
  def c  = clock
  def r  = reset.asBool
  def ro = resetOverride
  def s  = io.success
}

