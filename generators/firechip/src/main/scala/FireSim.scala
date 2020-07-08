//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey}
import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, InModuleBody}
import freechips.rocketchip.util.{ResetCatchAndSync}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge, RationalClock}

import chipyard.{BuildSystem, BuildTop, HasHarnessUtils, ChipyardSubsystem, ChipyardClockKey, ChipTop}
import chipyard.iobinders.{IOBinders}

// Determines the number of times to instantiate the DUT in the harness.
// Subsumes legacy supernode support
case object NumNodes extends Field[Int](1)

class WithNumNodes(n: Int) extends Config((pname, site, here) => {
  case NumNodes => n
})

// Note, the main prerequisite for supporting an additional clock domain in a
// FireSim simulation is to supply an additional clock parameter
// (RationalClock) to the clock bridge (RationalClockBridge). The bridge
// produces a vector of clocks, based on the provided parameter list, which you
// may use freely without further modifications to your target design.
case class FireSimClockParameters(additionalClocks: Seq[RationalClock]) {
  def numClocks(): Int = additionalClocks.size + 1
}
case object FireSimClockKey extends Field[FireSimClockParameters](FireSimClockParameters(Seq()))

// Hacky: Set before each node is generated. Ideally we'd give IO binders
// accesses to the the Harness's parameters instance. We could then alter that.
object NodeIdx {
  private var idx = 0
  def increment(): Unit = {idx = idx + 1 }
  def apply(): Int = idx
}

class WithFireSimSimpleClocks extends Config((site, here, up) => {
  case ChipyardClockKey => { chiptop: ChipTop =>
    implicit val p = chiptop.p
    val simpleClockGroupSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    val clockAggregator = LazyModule(new ClockGroupAggregator("clocks"))

    // Aggregate all 3 possible clock groups with the clockAggregator
    chiptop.systemClockGroup.node := clockAggregator.node
    if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) {
      chiptop.lSystem match { case l: BaseSubsystem => l.asyncClockGroupsNode := clockAggregator.node }
    }
    chiptop.lSystem match { case l: ChipyardSubsystem => l.tileClockGroupNode := clockAggregator.node }

    clockAggregator.node := simpleClockGroupSourceNode
    InModuleBody {
      val clock      = IO(Input(Clock())).suggestName("clock")
      val reset      = IO(Input(Reset())).suggestName("reset")

      simpleClockGroupSourceNode.out.unzip._1.flatMap(_.member).map { o =>
        o.clock := clock
        o.reset := reset
      }

      chiptop.harnessFunctions += ((th: HasHarnessUtils) => {
        clock := th.harnessClock
        reset := th.harnessReset
        Nil
      })
    }
  }
})

class WithFireSimRationalTileDomain(multiplier: Int, divisor: Int) extends Config((site, here, up) => {
  case FireSimClockKey => FireSimClockParameters(Seq(RationalClock("TileDomain", multiplier, divisor)))
  case ChipyardClockKey => { chiptop: ChipTop =>
    implicit val p = chiptop.p
    val simpleClockGroupSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters(), ClockGroupSourceParameters()))
    val uncoreClockAggregator = LazyModule(new ClockGroupAggregator("uncore_clocks"))

    // Aggregate only the uncoreclocks
    chiptop.systemClockGroup.node := uncoreClockAggregator.node
    if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) {
      chiptop.lSystem match { case l: BaseSubsystem => l.asyncClockGroupsNode := uncoreClockAggregator.node }
    }

    uncoreClockAggregator.node := simpleClockGroupSourceNode
    chiptop.lSystem match {
      case l: ChipyardSubsystem => l.tileClockGroupNode := simpleClockGroupSourceNode
      case _ => throw new Exception("MultiClock assumes ChipyardSystem")
    }

    InModuleBody {
      val uncore_clock = IO(Input(Clock())).suggestName("uncore_clock")
      val tile_clock   = IO(Input(Clock())).suggestName("tile_clock")
      val reset        = IO(Input(Reset())).suggestName("reset")

      simpleClockGroupSourceNode.out(0)._1.member.map { o =>
        o.clock := uncore_clock
        o.reset := reset
      }

      simpleClockGroupSourceNode.out(1)._1.member.map { o =>
        o.clock := tile_clock
        o.reset := ResetCatchAndSync(tile_clock, reset.asBool)
      }

      chiptop.harnessFunctions += ((th: HasHarnessUtils) => {
        uncore_clock := th.harnessClock
        reset        := th.harnessReset
        th match {
          case f: FireSim => tile_clock := f.additionalClocks(0)
          case _ => throw new Exception("FireSimMultiClock must be used with FireSim")
        }
        Nil
      })
    }
  }
})

class FireSim(implicit val p: Parameters) extends RawModule with HasHarnessUtils {
  freechips.rocketchip.util.property.cover.setPropLib(new midas.passes.FireSimPropertyLibrary())
  val clockBridge = Module(new RationalClockBridge(p(FireSimClockKey).additionalClocks:_*))
  val harnessClock = clockBridge.io.clocks.head // This is the reference clock
  val additionalClocks = clockBridge.io.clocks.tail
  val harnessReset = WireInit(false.B)
  val peekPokeBridge = PeekPokeBridge(harnessClock, harnessReset)
  val dutReset = false.B // unused (if used, its a bug)
  val success = false.B // unused (if used, its a bug)

  // Instantiate multiple instances of the DUT to implement supernode
  for (i <- 0 until p(NumNodes)) {
    // It's not a RC bump without some hacks...
    // Copy the AsyncClockGroupsKey to generate a fresh node on each
    // instantiation of the dut, otherwise the initial instance will be
    // reused across each node
    import freechips.rocketchip.subsystem.AsyncClockGroupsKey
    val lazyModule = LazyModule(p(BuildTop)(p.alterPartial({
      case AsyncClockGroupsKey => p(AsyncClockGroupsKey).copy
    })))
    val module = Module(lazyModule.module)
    require(lazyModule.harnessFunctions.size == 1, "There should only be 1 harness function to connect clock+reset")
    lazyModule.harnessFunctions.foreach(_(this))
    NodeIdx.increment()
  }
}
