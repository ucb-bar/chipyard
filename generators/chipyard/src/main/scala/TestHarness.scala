package chipyard

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Field, Parameters}
import chipyard.iobinders.{TestHarnessFunction}
import chipyard.config.ConfigValName._

// -------------------------------
// Chipyard Test Harness
// -------------------------------

case object BuildTop extends Field[Parameters => LazyModule with HasTestHarnessFunctions]((p: Parameters) => LazyModule(new ChipTop()(p)))

trait HasTestHarnessFunctions {
  val harnessFunctions: Seq[TestHarnessFunction]
}

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val ldut = p(BuildTop)(p)
  val dut = Module(ldut.module)
  io.success := false.B

  // dutReset assignment can be overridden via a harnessFunction, but by default it is just reset
  val dutReset = WireDefault(if (p(GlobalResetSchemeKey).pinIsAsync) reset.asAsyncReset else reset)

  ldut.harnessFunctions.foreach(_(this))

  def success = io.success
  def harnessReset = this.reset.asBool

}

