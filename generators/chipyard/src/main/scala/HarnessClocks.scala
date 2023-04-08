package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci._

import chipyard.harness.{ApplyHarnessBinders, HarnessBinders}
import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}
import chipyard.HarnessClockInstantiatorKey

trait HarnessClockInstantiator {
  val _clockMap: LinkedHashMap[String, (Double, ClockBundle)] = LinkedHashMap.empty

  // request a clock bundle at a particular frequency
  def requestClockBundle(name: String, freqRequested: Double): ClockBundle = {
    val clockBundle = Wire(new ClockBundle(ClockBundleParameters()))
    _clockMap(name) = (freqRequested, clockBundle)
    clockBundle
  }

  def instantiateHarnessClocks(refClock: ClockBundle): Unit
}

class DividerOnlyHarnessClockInstantiator extends HarnessClockInstantiator {
  // connect all clock wires specified to a divider only PLL
  def instantiateHarnessClocks(refClock: ClockBundle): Unit = {
    val sinks = _clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))), name=Some(name))
    }).toSeq

    val pllConfig = new SimplePllConfiguration("harnessDividerOnlyClockGenerator", sinks)
    pllConfig.emitSummaries()

    val dividedClocks = LinkedHashMap[Int, Clock]()
    def instantiateDivider(div: Int): Clock = {
      val divider = Module(new ClockDividerN(div))
      divider.suggestName(s"ClockDivideBy${div}")
      divider.io.clk_in := refClock.clock
      dividedClocks(div) = divider.io.clk_out
      divider.io.clk_out
    }

    // connect wires to clock source
    for (sinkParams <- sinks) {
      // bypass the reference freq. (don't create a divider + reset sync)
      val (divClock, divReset) = if (sinkParams.take.get.freqMHz != pllConfig.referenceFreqMHz) {
        val div = pllConfig.sinkDividerMap(sinkParams)
        val divClock = dividedClocks.getOrElse(div, instantiateDivider(div))
        (divClock, ResetCatchAndSync(divClock, refClock.reset.asBool))
      } else {
        (refClock.clock, refClock.reset)
      }

      _clockMap(sinkParams.name.get)._2.clock := divClock
      _clockMap(sinkParams.name.get)._2.reset := divReset
    }
  }
}

class AbsoluteFreqHarnessClockInstantiator extends HarnessClockInstantiator {
  def instantiateHarnessClocks(refClock: ClockBundle): Unit = {
    val sinks = _clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))), name=Some(name))
    }).toSeq

    // connect wires to clock source
    for (sinkParams <- sinks) {
      val source = Module(new ClockSourceAtFreq(sinkParams.take.get.freqMHz))
      source.io.power := true.B
      source.io.gate := false.B

      _clockMap(sinkParams.name.get)._2.clock := source.io.clk
      _clockMap(sinkParams.name.get)._2.reset := refClock.reset
    }
  }
}

class WithAbsoluteFreqHarnessClockInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new AbsoluteFreqHarnessClockInstantiator
})
