package chipyard.harness

import chisel3._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockSinkParameters, ClockParameters}
import freechips.rocketchip.stage.phases.TargetDirKey

import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}
import chipyard.{ChipTop}

// -------------------------------
// Chipyard Test Harness
// -------------------------------

case object MultiChipParameters extends Field[Seq[Parameters]](Nil)
case object BuildTop extends Field[Parameters => LazyModule]((p: Parameters) => new ChipTop()(p))
case object DefaultClockFrequencyKey extends Field[Double](100.0) // MHz
case object HarnessClockInstantiatorKey extends Field[() => HarnessClockInstantiator](() => new DividerOnlyHarnessClockInstantiator)

class WithMultiChip(p: Parameters) extends Config((site, here, up) => {
  case MultiChipParameters => up(MultiChipParameters) :+ p
})

trait HasHarnessSignalReferences {
  implicit val p: Parameters
  val harnessClockInstantiator = p(HarnessClockInstantiatorKey)()
  // clock/reset of the chiptop reference clock (can be different than the implicit harness clock/reset)
  var refClockFreq: Double = p(DefaultClockFrequencyKey)
  def setRefClockFreq(freqMHz: Double) = { refClockFreq = freqMHz }
  def getRefClockFreq: Double = refClockFreq
  def buildtopClock: Clock
  def buildtopReset: Reset
  def success: Bool
}

class TestHarness(implicit val p: Parameters) extends Module with HasHarnessSignalReferences {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  // These drive harness blocks
  val buildtopClock = Wire(Clock())
  val buildtopReset = Wire(Reset())

  val nChips = if (p(MultiChipParameters).isEmpty) 1 else p(MultiChipParameters).size
  def chipParameters(i: Int) = if (p(MultiChipParameters).isEmpty) p else p(MultiChipParameters)(i).alterPartial {
    case TargetDirKey => p(TargetDirKey)
  }

  val lazyDuts = (0 until nChips).map { i =>
    val q: Parameters = chipParameters(i)
    LazyModule(q(BuildTop)(q)).suggestName(s"chiptop$i")
  }
  val duts = lazyDuts.map(l => Module(l.module))

  io.success := false.B

  val success = io.success

  lazyDuts.zipWithIndex.foreach {
    case (d: HasIOBinders, i: Int) => ApplyHarnessBinders(this, d.lazySystem, d.portMap)(chipParameters(i))
    case _ =>
  }

  val refClkBundle = harnessClockInstantiator.requestClockBundle("buildtop_reference_clock", getRefClockFreq * (1000 * 1000))

  buildtopClock := refClkBundle.clock
  buildtopReset := WireInit(refClkBundle.reset)

  val implicitHarnessClockBundle = Wire(new ClockBundle(ClockBundleParameters()))
  implicitHarnessClockBundle.clock := clock
  implicitHarnessClockBundle.reset := reset
  harnessClockInstantiator.instantiateHarnessClocks(implicitHarnessClockBundle)
}
