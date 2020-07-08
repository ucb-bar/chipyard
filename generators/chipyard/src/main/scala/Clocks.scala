package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{OutwardNodeHandle, InModuleBody, LazyModule}
import freechips.rocketchip.util.{ResetCatchAndSync, Pow2ClockDivider}

import barstools.iocell.chisel._

/**
  * Chipyard provides three baseline, top-level reset schemes, set using the
  * [[GlobalResetSchemeKey]] in a Parameters instance. These are:
  *
  * 1) Synchronous: The input coming to the chip is synchronous to the provided
  *    clocks and will be used without modification as a synchronous reset.
  *    This is safe only for use in FireSim and SW simulation.
  *
  * 2) Asynchronous: The input reset is asynchronous to the input clock, but it
  *    is caught and synchronized to that clock before it is dissemenated.
  *    Thus, downsteam modules will be emitted with synchronously reset state
  *    elements.
  *
  * 3) Asynchronous Full: The input reset is asynchronous to the input clock,
  *    and is used globally as an async reset. Downstream modules will be emitted
  *    with asynchronously reset state elements.
  *
  */
sealed trait GlobalResetScheme {
  def pinIsAsync: Boolean
}
sealed trait HasAsyncInput { self: GlobalResetScheme =>
  def pinIsAsync = true
}

sealed trait HasSyncInput { self: GlobalResetScheme =>
  def pinIsAsync = false
}

case object GlobalResetSynchronous extends GlobalResetScheme with HasSyncInput
case object GlobalResetAsynchronous extends GlobalResetScheme with HasAsyncInput
case object GlobalResetAsynchronousFull extends GlobalResetScheme with HasAsyncInput
case object GlobalResetSchemeKey extends Field[GlobalResetScheme](GlobalResetSynchronous)

/**
 * A simple reset implementation that punches out reset ports
 * for standard Module classes. Three basic reset schemes
 * are provided. See [[GlobalResetScheme]].
 */
object GenerateReset {
  def apply(chiptop: ChipTop, clock: Clock): Reset = {
    implicit val p = chiptop.p
    // this needs directionality so generateIOFromSignal works
    val reset_wire = Wire(Input(Reset()))
    val (reset_io, resetIOCell) = p(GlobalResetSchemeKey) match {
      case GlobalResetSynchronous =>
        IOCell.generateIOFromSignal(reset_wire, Some("iocell_reset"))
      case GlobalResetAsynchronousFull =>
        IOCell.generateIOFromSignal(reset_wire, Some("iocell_reset"), abstractResetAsAsync = true)
      case GlobalResetAsynchronous => {
        val async_reset_wire = Wire(Input(AsyncReset()))
        reset_wire := ResetCatchAndSync(clock, async_reset_wire.asBool())
        IOCell.generateIOFromSignal(async_reset_wire, Some("iocell_reset"), abstractResetAsAsync = true)
      }
    }
    reset_io.suggestName("reset")
    chiptop.iocells ++= resetIOCell
    chiptop.harnessFunctions += ((th: HasHarnessUtils) => {
      reset_io := th.dutReset
      Nil
    })
    reset_wire
  }
}


case object ChipyardClockKey extends Field[ChipTop => Unit](ClockDrivers.harnessClock)



object ClockDrivers {
  // A simple clock provider, for testing. All clocks in system are aggregated into one,
  // and are driven by directly punching out to the TestHarness clock
  val harnessClock: ChipTop => Unit = { chiptop =>
    implicit val p = chiptop.p
    val simpleClockGroupSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    val clockAggregator = LazyModule(new ClockGroupAggregator("clocks"))

    // Aggregate all 3 possible clock groups with the clockAggregator
    chiptop.systemClockGroup.node := clockAggregator.node
    if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) {
      chiptop.lSystem match { case l: BaseSubsystem => l.asyncClockGroupsNode := clockAggregator.node }
    }
    chiptop.lSystem match {
      case l: ChipyardSubsystem => l.tileClockGroupNode := clockAggregator.node
      case _ =>
    }


    clockAggregator.node := simpleClockGroupSourceNode
    InModuleBody {
      // this needs directionality so generateIOFromSignal works
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = GenerateReset(chiptop, clock_wire)
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, Some("iocell_clock"))
      chiptop.iocells ++= clockIOCell
      clock_io.suggestName("clock")

      simpleClockGroupSourceNode.out.unzip._1.flatMap(_.member).map { o =>
        o.clock := clock_wire
        o.reset := reset_wire
      }
      chiptop.harnessFunctions += ((th: HasHarnessUtils) => {
        clock_io := th.harnessClock
        Nil
      })
    }
  }

  val harnessMultiClock: ChipTop => Unit = { chiptop =>
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
      // this needs directionality so generateIOFromSignal works
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = GenerateReset(chiptop, clock_wire)
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, Some("iocell_clock"))
      chiptop.iocells ++= clockIOCell
      clock_io.suggestName("clock")
      val div_clock = Pow2ClockDivider(clock_wire, 2)

      simpleClockGroupSourceNode.out(0)._1.member.map { o =>
        o.clock := div_clock
        o.reset := ResetCatchAndSync(div_clock, reset_wire.asBool)
      }
      simpleClockGroupSourceNode.out(1)._1.member.map { o =>
        o.clock := clock_wire
        o.reset := reset_wire
      }
      chiptop.harnessFunctions += ((th: HasHarnessUtils) => {
        clock_io := th.harnessClock
        Nil
      })
    }

  }
}
