package chipyard.harness

import chisel3._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockSinkParameters, ClockParameters}
import chipyard.stage.phases.TargetDirKey

import chipyard.harness.{ApplyHarnessBinders, HarnessBinders}
import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}
import chipyard.{ChipTop}

// -------------------------------
// Chipyard Test Harness
// -------------------------------

case object MultiChipNChips extends Field[Option[Int]](None) // None means ignore MultiChipParams
case class MultiChipParameters(chipId: Int) extends Field[Parameters]
case object BuildTop extends Field[Parameters => LazyModule]((p: Parameters) => new ChipTop()(p))
case object HarnessClockInstantiatorKey extends Field[() => HarnessClockInstantiator]()
case object HarnessBinderClockFrequencyKey extends Field[Double](100.0) // MHz
case object MultiChipIdx extends Field[Int](0)

class WithMultiChip(id: Int, p: Parameters) extends Config((site, here, up) => {
  case MultiChipParameters(`id`) => p
  case MultiChipNChips => Some(up(MultiChipNChips).getOrElse(0) max (id + 1))
})

class WithHomogeneousMultiChip(n: Int, p: Parameters, idStart: Int = 0) extends Config((site, here, up) => {
  case MultiChipParameters(id) => if (id >= idStart && id < idStart + n) p else up(MultiChipParameters(id))
  case MultiChipNChips => Some(up(MultiChipNChips).getOrElse(0) max (idStart + n))
})

class WithHarnessBinderClockFreqMHz(freqMHz: Double) extends Config((site, here, up) => {
  case HarnessBinderClockFrequencyKey => freqMHz
})

// A TestHarness mixing this in will
// - use the HarnessClockInstantiator clock provide
trait HasHarnessInstantiators {
  implicit val p: Parameters
  // clock/reset of the chiptop reference clock (can be different than the implicit harness clock/reset)
  private val harnessBinderClockFreq: Double = p(HarnessBinderClockFrequencyKey)
  def getHarnessBinderClockFreqHz: Double = harnessBinderClockFreq * 1000000
  def getHarnessBinderClockFreqMHz: Double = harnessBinderClockFreq

  // buildtopClock takes the refClockFreq, and drives the harnessbinders
  val harnessBinderClock = Wire(Clock())
  val harnessBinderReset = Wire(Reset())

  // classes which inherit this trait should provide the below definitions
  def referenceClockFreqMHz: Double
  def referenceClock: Clock
  def referenceReset: Reset
  def success: Bool

  // This can be accessed to get new clocks from the harness
  val harnessClockInstantiator = p(HarnessClockInstantiatorKey)()

  val supportsMultiChip: Boolean = false

  val chipParameters = p(MultiChipNChips) match {
    case Some(n) => (0 until n).map { i => p(MultiChipParameters(i)).alterPartial {
      case TargetDirKey => p(TargetDirKey) // hacky fix
      case MultiChipIdx => i
    }}
    case None => Seq(p)
  }

  // This shold be called last to build the ChipTops
  def instantiateChipTops(): Seq[LazyModule] = {
    require(p(MultiChipNChips).isEmpty || supportsMultiChip,
      s"Selected Harness does not support multi-chip")

    val lazyDuts = chipParameters.zipWithIndex.map { case (q,i) =>
      LazyModule(q(BuildTop)(q)).suggestName(s"chiptop$i")
    }
    val duts = lazyDuts.map(l => Module(l.module))

    withClockAndReset (harnessBinderClock, harnessBinderReset) {
      lazyDuts.zipWithIndex.foreach {
        case (d: HasIOBinders, i: Int) => ApplyHarnessBinders(this, d.portMap.values.flatten.toSeq)(chipParameters(i))
        case _ =>
      }
      ApplyMultiHarnessBinders(this, lazyDuts)
    }

    val harnessBinderClk = harnessClockInstantiator.requestClockMHz("harnessbinder_clock", getHarnessBinderClockFreqMHz)
    println(s"Harness binder clock is $harnessBinderClockFreq")
    harnessBinderClock := harnessBinderClk
    harnessBinderReset := ResetCatchAndSync(harnessBinderClk, referenceReset.asBool)

    harnessClockInstantiator.instantiateHarnessClocks(referenceClock, referenceClockFreqMHz)

    lazyDuts
  }
}
