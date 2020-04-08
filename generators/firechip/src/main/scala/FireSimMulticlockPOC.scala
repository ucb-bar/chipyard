//See LICENSE for license details.

package firesim.firesim

import chisel3._

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, RationalCrossing}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.{ResetCatchAndSync}

import boom.common.{BoomTilesKey, BoomCrossingKey}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge, RationalClock}
import firesim.configs._

import chipyard.{BuildSystem, DigitalTop, DigitalTopModule}
import chipyard.config.ConfigValName._
import chipyard.iobinders.{IOBinders}

// WIP! This file is a sketch of one means of defining a multiclock target-design
// that can be simulated in FireSim, pending a canonicalized form in Chipyard.
//
// Note, the main prerequisite for supporting an additional clock domain in a
// FireSim simulation is to supply an additional clock parameter
// (RationalClock) to the clock bridge (RationalClockBridge). The bridge
// produces a vector of clocks, based on the provided parameter list, which you
// may use freely without further modifications to your target design.

case class FireSimClockParameters(additionalClocks: Seq[RationalClock]) {
  def numClocks(): Int = additionalClocks.size + 1
}
case object FireSimClockKey extends Field[FireSimClockParameters](FireSimClockParameters(Seq()))

trait HasAdditionalClocks extends LazyModuleImp {
  val clocks = IO(Vec(p(FireSimClockKey).numClocks, Input(Clock())))
}

// Presupposes only 1 or 2 clocks.
trait HasFireSimClockingImp extends HasAdditionalClocks {
  val outer: HasTiles
  val (tileClock, tileReset) = p(FireSimClockKey).additionalClocks.headOption match {
    case Some(RationalClock(_, numer, denom)) if numer != denom => (clocks(1), ResetCatchAndSync(clocks(1), reset.toBool))
    case None => (clocks.head, reset)
  }

  outer.tiles.foreach({ case tile =>
    tile.module.clock := tileClock
    tile.module.reset := tileReset
  })
}

// Config Fragment
class WithSingleRationalTileDomain(multiplier: Int, divisor: Int) extends Config((site, here, up) => {
  case FireSimClockKey => FireSimClockParameters(Seq(RationalClock("TileDomain", multiplier, divisor)))
  case RocketCrossingKey => up(RocketCrossingKey, site) map { r =>
    r.copy(crossingType = RationalCrossing())
  }
  case BoomCrossingKey => up(BoomCrossingKey, site) map { r =>
    r.copy(crossingType = RationalCrossing())
  }
})

class HalfRateUncore extends WithSingleRationalTileDomain(2,1)

class WithFiresimMulticlockTop extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => LazyModule(new FiresimMulticlockTop()(p)).suggestName("system")
})

// Complete Config
class FireSimQuadRocketMulticlockConfig extends Config(
  new HalfRateUncore ++
  new WithFiresimMulticlockTop ++
  new FireSimQuadRocketConfig)

// Top Definition
class FiresimMulticlockTop(implicit p: Parameters) extends chipyard.DigitalTop
{
  override lazy val module = new FiresimMulticlockTopModule(this)
}

class FiresimMulticlockTopModule[+L <: DigitalTop](l: L) extends chipyard.DigitalTopModule(l) with HasFireSimClockingImp

// Harness Definition
class FireSimMulticlockPOC(implicit val p: Parameters) extends RawModule {
  val clockBridge = Module(new RationalClockBridge(p(FireSimClockKey).additionalClocks:_*))
  val refClock = clockBridge.io.clocks.head
  val reset = WireInit(false.B)
  withClockAndReset(refClock, reset) {
    // Instantiate multiple instances of the DUT to implement supernode
    val targets = Seq.fill(p(NumNodes)) {
      val lazyModule = p(BuildSystem)(p)
      (lazyModule, Module(lazyModule.module))
    }
    val peekPokeBridge = PeekPokeBridge(refClock, reset)
    // A Seq of partial functions that will instantiate the right bridge only
    // if that Mixin trait is present in the target's class instance
    //
    // Apply each partial function to each DUT instance
    for ((lazyModule, module) <- targets) {
      p(IOBinders).values.foreach(f => f(lazyModule) ++ f(module))
    }
    targets.collect({ case (_, t: HasAdditionalClocks) => t.clocks := clockBridge.io.clocks })
  }
}


