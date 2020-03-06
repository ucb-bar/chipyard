//See LICENSE for license details.

package firesim.firesim

import chisel3._

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}

import midas.widgets.{Bridge, PeekPokeBridge}

import chipyard.{BuildTop}
import chipyard.iobinders.{IOBinders}

// Determines the number of times to instantiate the DUT in the harness.
// Subsumes legacy supernode support
case object NumNodes extends Field[Int](1)

class WithNumNodes(n: Int) extends Config((pname, site, here) => {
  case NumNodes => n
})

class FireSim(implicit val p: Parameters) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = WireInit(false.B)
  withClockAndReset(clock, reset) {
    // Instantiate multiple instances of the DUT to implement supernode
    val targets = Seq.fill(p(NumNodes))(p(BuildTop)(p))
    val peekPokeBridge = PeekPokeBridge(reset)
    // A Seq of partial functions that will instantiate the right bridge only
    // if that Mixin trait is present in the target's class instance
    //
    // Apply each partial function to each DUT instance
    for ((target) <- targets) {
      p(IOBinders).toSeq.sortBy(_._1).map { case (name, fn) =>
        fn(clock, reset.asBool, false.B, target)
      }
    }
  }
}
