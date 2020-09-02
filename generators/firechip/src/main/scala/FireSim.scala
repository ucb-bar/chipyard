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

import chipyard.{BuildSystem, BuildTop, HasHarnessSignalReferences, ChipyardSubsystem, ClockingSchemeKey, ChipTop}
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
  case ClockingSchemeKey => { chiptop: ChipTop =>
    implicit val p = chiptop.p

    val implicitClockSourceNode = ClockSourceNode(Seq(ClockSourceParameters()))
    chiptop.implicitClockSinkNode := implicitClockSourceNode

    // Drive the diplomaticclock graph of the DigitalTop (if present)
    val simpleClockGroupSourceNode = chiptop.lSystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) => {
        val n = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
        l.asyncClockGroupsNode := n
        Some(n)
      }
      case _ => None
    }

    InModuleBody {
      val clock = IO(Input(Clock())).suggestName("clock")
      val reset = IO(Input(Reset())).suggestName("reset")

      implicitClockSourceNode.out.unzip._1.map { o =>
        o.clock := clock
        o.reset := reset
      }

      simpleClockGroupSourceNode.map { n => n.out.unzip._1.map { out: ClockGroupBundle =>
        out.member.data.foreach { o =>
          o.clock := clock
          o.reset := reset
        }
      }}

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
        clock := th.harnessClock
        reset := th.harnessReset
        Nil
      })
    }
  }
})

class WithFireSimRationalTileDomain(multiplier: Int, divisor: Int) extends Config((site, here, up) => {
  case FireSimClockKey => FireSimClockParameters(Seq(RationalClock("TileDomain", multiplier, divisor)))
  case ClockingSchemeKey => { chiptop: ChipTop =>
    implicit val p = chiptop.p

    val implicitClockSourceNode = ClockSourceNode(Seq(ClockSourceParameters()))
    chiptop.implicitClockSinkNode := implicitClockSourceNode

    // Drive the diplomaticclock graph of the DigitalTop (if present)
    val simpleClockGroupSourceNode = chiptop.lSystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) => {
        val n = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
        l.asyncClockGroupsNode := n
        Some(n)
      }
      case _ => None
    }

    InModuleBody {
      val uncore_clock = IO(Input(Clock())).suggestName("uncore_clock")
      val tile_clock   = IO(Input(Clock())).suggestName("tile_clock")
      val reset        = IO(Input(Reset())).suggestName("reset")

      implicitClockSourceNode.out.unzip._1.map { o =>
        o.clock := uncore_clock
        o.reset := reset
      }

      simpleClockGroupSourceNode.map { n => n.out.unzip._1.map { out: ClockGroupBundle =>
        out.member.elements.map { case (name, data) =>
          // This is mega hacks, how are you actually supposed to do this?
          if (name.contains("core")) {
            data.clock := tile_clock
            data.reset := ResetCatchAndSync(tile_clock, reset.asBool)
          } else {
            data.clock := uncore_clock
            data.reset := reset
          }
        }
      }}

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
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

class FireSim(implicit val p: Parameters) extends RawModule with HasHarnessSignalReferences {
  freechips.rocketchip.util.property.cover.setPropLib(new midas.passes.FireSimPropertyLibrary())
  val clockBridge = Module(new RationalClockBridge(p(FireSimClockKey).additionalClocks:_*))
  val harnessClock = clockBridge.io.clocks.head // This is the reference clock
  val additionalClocks = clockBridge.io.clocks.tail
  val harnessReset = WireInit(false.B)
  val peekPokeBridge = PeekPokeBridge(harnessClock, harnessReset)
  def dutReset = { require(false, "dutReset should not be used in Firesim"); false.B }
  def success = { require(false, "success should not be used in Firesim"); false.B }

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
