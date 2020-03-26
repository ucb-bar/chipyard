package chipyard

import chisel3._
import chisel3.experimental._

import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}

import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.{AsyncResetReg, GeneratorApp}
import freechips.rocketchip.devices.debug.{Debug, DebugIO, PSDIO}
import chipyard.config.ConfigValName._
import chipyard.iobinders.types.TestHarnessFunction

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

object TestHarnessUtils {

  // returns reset
  def connectSimDebug(c: Clock, r: Bool, s: Bool, debug: Option[DebugIO], psd: PSDIO)(implicit p: Parameters): Bool = {
    val dtm_success = Wire(Bool())
    Debug.connectDebug(debug, psd, c, r, dtm_success)
    when (dtm_success) { s := true.B }
    r | debug.map { debug => AsyncResetReg(debug.ndreset).asBool }.getOrElse(false.B)
  }

  def tieoffDebug(c: Clock, r: Bool, debug: Option[DebugIO], psd: PSDIO) {
    Debug.tieoffDebug(debug, psd)
    // tieoffDebug doesn't actually tie everything off :/
    debug.foreach(_.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare }))
  }

}
