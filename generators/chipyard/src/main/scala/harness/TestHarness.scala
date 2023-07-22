package chipyard.harness

import chisel3._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockSinkParameters, ClockParameters}

import chipyard.harness.{ApplyHarnessBinders, HarnessBinders}
import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}
import chipyard.{ChipTop}

// -------------------------------
// Chipyard Test Harness
// -------------------------------

class TestHarness(implicit val p: Parameters) extends Module with HasHarnessInstantiators {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })
  val success = WireInit(false.B)
  io.success := success

  override val supportsMultiChip = true

  // By default, the chipyard makefile sets the TestHarness implicit clock to be 1GHz
  // This clock shouldn't be used by this TestHarness however, as most users
  // will use the AbsoluteFreqHarnessClockInstantiator, which generates clocks
  // in verilog blackboxes
  def referenceClockFreqMHz = 1000.0
  def referenceClock = clock
  def referenceReset = reset

  val lazyDuts = instantiateChipTops()
}
