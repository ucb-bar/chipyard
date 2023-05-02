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

case object MultiChipNChips extends Field[Int](0) // 0 means ignore MultiChipParams
case class MultiChipParameters(chipId: Int) extends Field[Parameters]
case object BuildTop extends Field[Parameters => LazyModule]((p: Parameters) => new ChipTop()(p))
case object DefaultClockFrequencyKey extends Field[Double](100.0) // MHz
case object HarnessClockInstantiatorKey extends Field[() => HarnessClockInstantiator](() => new DividerOnlyHarnessClockInstantiator)
case object MultiChipIdx extends Field[Int](0)

class WithMultiChip(id: Int, p: Parameters) extends Config((site, here, up) => {
  case MultiChipParameters(`id`) => p
  case MultiChipNChips => up(MultiChipNChips) max (id + 1)
})

class WithHomogeneousMultiChip(n: Int, p: Parameters, idStart: Int = 0) extends Config((site, here, up) => {
  case MultiChipParameters(id) => if (id >= idStart && id < idStart + n) p else up(MultiChipParameters(id))
  case MultiChipNChips => up(MultiChipNChips) max (idStart + n)
})

trait HasHarnessSignalReferences {
  implicit val p: Parameters
  val harnessClockInstantiator = p(HarnessClockInstantiatorKey)()
  // clock/reset of the chiptop reference clock (can be different than the implicit harness clock/reset)
  private var refClockFreq: Double = p(DefaultClockFrequencyKey)
  def setRefClockFreqMHz(freqMHz: Double) = { refClockFreq = freqMHz }
  def getRefClockFreqHz: Double = refClockFreq * 1000000
  def getRefClockFreqMHz: Double = refClockFreq
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

  val chipParameters = if (p(MultiChipNChips) == 0) {
    Seq(p)
  } else {
    (0 until p(MultiChipNChips)).map { i => p(MultiChipParameters(i)).alterPartial {
      case TargetDirKey => p(TargetDirKey) // hacky fix
      case MultiChipIdx => i
    }}
  }

  val lazyDuts = chipParameters.zipWithIndex.map { case (q,i) =>
    LazyModule(q(BuildTop)(q)).suggestName(s"chiptop$i")
  }
  val duts = lazyDuts.map(l => Module(l.module))

  io.success := false.B

  val success = io.success

  lazyDuts.zipWithIndex.foreach {
    case (d: HasIOBinders, i: Int) => ApplyHarnessBinders(this, d.lazySystem, d.portMap)(chipParameters(i))
    case _ =>
  }

  ApplyMultiHarnessBinders(this, lazyDuts)

  val refClkBundle = harnessClockInstantiator.requestClockBundle("buildtop_reference_clock", getRefClockFreqHz)
  buildtopClock := refClkBundle.clock
  buildtopReset := WireInit(refClkBundle.reset)

  val implicitHarnessClockBundle = Wire(new ClockBundle(ClockBundleParameters()))
  implicitHarnessClockBundle.clock := clock
  implicitHarnessClockBundle.reset := reset
  harnessClockInstantiator.instantiateHarnessClocks(implicitHarnessClockBundle)
}
