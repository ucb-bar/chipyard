//See LICENSE for license details.

package firesim.firesim

import chisel3._

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem.{HasTiles}
import freechips.rocketchip.util.{ResetCatchAndSync}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge, RationalClock}

import chipyard.{BuildTop}
import chipyard.iobinders.{IOBinders}

// Determines the number of times to instantiate the DUT in the harness.
// Subsumes legacy supernode support
case object NumNodes extends Field[Int](1)

class WithNumNodes(n: Int) extends Config((pname, site, here) => {
  case NumNodes => n
})

case class FireSimClockParameters(additionalClocks: Seq[RationalClock]) {
  def numClocks(): Int = additionalClocks.size + 1
}
case object FireSimClockKey extends Field[FireSimClockParameters](FireSimClockParameters(Seq()))

trait HasAdditionalClocks extends LazyModuleImp {
  val clocks = IO(Vec(p(FireSimClockKey).numClocks, Input(Clock())))
}

trait HasFireSimClockingImp extends HasAdditionalClocks {
  val outer: HasTiles
  val (tileClock, tileReset) = p(FireSimClockKey).additionalClocks.headOption match {
    case Some(RationalClock(_, numer, denom)) if numer != denom => (clocks(1), ResetCatchAndSync(clocks(1), reset.toBool))
    case None => (clocks(0), reset)
  }

  outer.tiles.foreach({ case tile =>
    tile.module.clock := tileClock
    tile.module.reset := tileReset
  })
}

class FireSim[T <: LazyModule](implicit val p: Parameters) extends RawModule {
  val clockBridge = Module(new RationalClockBridge(p(FireSimClockKey).additionalClocks:_*))
  val refClock = clockBridge.io.clocks(0)
  val reset = WireInit(false.B)
  withClockAndReset(refClock, reset) {
    // Instantiate multiple instances of the DUT to implement supernode
    val targets = Seq.fill(p(NumNodes))(p(BuildTop)(p))
    val peekPokeBridge = PeekPokeBridge(refClock, reset)
    // A Seq of partial functions that will instantiate the right bridge only
    // if that Mixin trait is present in the target's class instance
    //
    // Apply each partial function to each DUT instance
    for ((target) <- targets) {
      p(IOBinders).values.map(fn => fn(refClock, reset.asBool, false.B, target))
    }
    targets.collect({ case t: HasAdditionalClocks => t.clocks := clockBridge.io.clocks })
  }
}
