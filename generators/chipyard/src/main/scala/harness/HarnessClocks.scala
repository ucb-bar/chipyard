package chipyard.harness

import chisel3._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import freechips.rocketchip.diplomacy.{LazyModule}
import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci._

import chipyard.harness.{ApplyHarnessBinders, HarnessBinders, HarnessClockInstantiatorKey}
import chipyard.iobinders.HasIOBinders
import chipyard.clocking.{SimplePllConfiguration, ClockDividerN}


// HarnessClockInstantiators are classes which generate clocks that drive
// TestHarness simulation models and any Clock inputs to the ChipTop
trait HarnessClockInstantiator {
  val clockMap: LinkedHashMap[String, (Double, ClockBundle)] = LinkedHashMap.empty

  // request a clock bundle at a particular frequency
  def requestClockBundle(name: String, freqRequested: Double): ClockBundle = {
    if (clockMap.contains(name)) {
      require(freqRequested == clockMap(name)._1,
        s"Request clock freq = $freqRequested != previously requested ${clockMap(name)._2} for requested clock $name")
      clockMap(name)._2
    } else {
      val clockBundle = Wire(new ClockBundle(ClockBundleParameters()))
      clockMap(name) = (freqRequested, clockBundle)
      clockBundle
    }
  }

  // refClock is the clock generated by TestDriver that is
  // passed to the TestHarness as its implicit clock
  def instantiateHarnessClocks(refClock: ClockBundle): Unit
}

// The DividerOnlyHarnessClockInstantiator uses synthesizable clock divisors
// to approximate frequency ratios between the requested clocks
class DividerOnlyHarnessClockInstantiator extends HarnessClockInstantiator {
  // connect all clock wires specified to a divider only PLL
  def instantiateHarnessClocks(refClock: ClockBundle): Unit = {
    val sinks = clockMap.map({ case (name, (freq, bundle)) =>
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

      clockMap(sinkParams.name.get)._2.clock := divClock
      clockMap(sinkParams.name.get)._2.reset := divReset
    }
  }
}

// The AbsoluteFreqHarnessClockInstantiator uses a Verilog blackbox to
// provide the precise requested frequency.
// This ClockInstantiator cannot be synthesized, run in Verilator, or run in FireSim
// It is useful for VCS/Xcelium-driven RTL simulations
class AbsoluteFreqHarnessClockInstantiator extends HarnessClockInstantiator {
  def instantiateHarnessClocks(refClock: ClockBundle): Unit = {
    val sinks = clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))), name=Some(name))
    }).toSeq

    // connect wires to clock source
    for (sinkParams <- sinks) {
      val source = Module(new ClockSourceAtFreq(sinkParams.take.get.freqMHz))
      source.io.power := true.B
      source.io.gate := false.B

      clockMap(sinkParams.name.get)._2.clock := source.io.clk
      clockMap(sinkParams.name.get)._2.reset := refClock.reset
    }
  }
}

class WithAbsoluteFreqHarnessClockInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new AbsoluteFreqHarnessClockInstantiator
})

class AllClocksFromHarnessClockInstantiator extends HarnessClockInstantiator {
  def instantiateHarnessClocks(refClock: ClockBundle): Unit = {
    val freqs = clockMap.map(_._2._1)
    freqs.tail.foreach(t => require(t == freqs.head, s"Mismatching clocks $t != ${freqs.head}"))
    for ((_, (_, bundle)) <- clockMap) {
      bundle.clock := refClock.clock
      bundle.reset := refClock.reset
    }
  }
}

class WithAllClocksFromHarnessClockInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new AllClocksFromHarnessClockInstantiator
})
