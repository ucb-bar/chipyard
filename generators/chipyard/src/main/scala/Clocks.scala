package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{OutwardNodeHandle, InModuleBody, LazyModule}
import freechips.rocketchip.util.{ResetCatchAndSync, Pow2ClockDivider}

import barstools.iocell.chisel._

import chipyard.clocking.{IdealizedPLL, ClockGroupDealiaser}

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
    chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
      reset_io := th.dutReset
      Nil
    })
    reset_wire
  }
}


case object ClockingSchemeKey extends Field[ChipTop => Unit](ClockingSchemeGenerators.harnessClock)



object ClockingSchemeGenerators {
  // A simple clock provider, for testing
  val harnessClock: ChipTop => Unit = { chiptop =>
    implicit val p = chiptop.p

    val implicitClockSourceNode = ClockSourceNode(Seq(ClockSourceParameters()))
    chiptop.implicitClockSinkNode := implicitClockSourceNode

    // Drive the diplomaticclock graph of the DigitalTop (if present)
    val simpleClockGroupSourceNode = chiptop.lazySystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) => {
        val n = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
        l.asyncClockGroupsNode := n
        Some(n)
      }
      case _ => None
    }

    InModuleBody {
      //this needs directionality so generateIOFromSignal works
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = GenerateReset(chiptop, clock_wire)
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, Some("iocell_clock"))
      chiptop.iocells ++= clockIOCell
      clock_io.suggestName("clock")

      implicitClockSourceNode.out.unzip._1.map { o =>
        o.clock := clock_wire
        o.reset := reset_wire
      }

      simpleClockGroupSourceNode.map { n => n.out.unzip._1.map { out: ClockGroupBundle =>
        out.member.data.foreach { o =>
          o.clock := clock_wire
          o.reset := reset_wire
        }
      }}

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
        clock_io := th.harnessClock
        Nil
      })
    }

  }


  val harnessDividedClock: ChipTop => Unit = { chiptop =>
    implicit val p = chiptop.p

    val implicitClockSourceNode = ClockSourceNode(Seq(ClockSourceParameters()))
    chiptop.implicitClockSinkNode := implicitClockSourceNode

    val simpleClockGroupSourceNode = chiptop.lazySystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) => {
        val n = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
        l.asyncClockGroupsNode := n
        Some(n)
      }
      case _ => throw new Exception("Harness multiclock assumes BaseSubsystem")
    }

    InModuleBody {
      // this needs directionality so generateIOFromSignal works
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = GenerateReset(chiptop, clock_wire)
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, Some("iocell_clock"))
      chiptop.iocells ++= clockIOCell
      clock_io.suggestName("clock")
      val div_clock = Pow2ClockDivider(clock_wire, 2)

      implicitClockSourceNode.out.unzip._1.map { o =>
        o.clock := div_clock
        o.reset := reset_wire
      }

      simpleClockGroupSourceNode.map { n => n.out.unzip._1.map { out: ClockGroupBundle =>
        out.member.elements.map { case (name, data) =>
          // This is mega hacks, how are you actually supposed to do this?
          data.clock := (if (name.contains("core")) clock_wire else div_clock)
          data.reset := reset_wire
        }
      }}

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
        clock_io := th.harnessClock
        Nil
      })
    }
  }

  val idealizedPLL: ChipTop => Unit = { chiptop =>
    implicit val p = chiptop.p

    // Requires existence of undriven asyncClockGroups in subsystem
    val systemAsyncClockGroup = chiptop.lSystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) =>
        l.asyncClockGroupsNode
    }

    val aggregator = ClockGroupAggregator()
    chiptop.implicitClockSinkNode := ClockGroup() := aggregator
    systemAsyncClockGroup := aggregator

    val referenceClockSource =  ClockSourceNode(Seq(ClockSourceParameters()))
    aggregator := ClockGroupDealiaser() := IdealizedPLL() := referenceClockSource

    InModuleBody {

      val clock_wire = Wire(Input(Clock()))
      val reset_wire = GenerateReset(chiptop, clock_wire)
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, Some("iocell_clock"))
      chiptop.iocells ++= clockIOCell
      clock_io.suggestName("clock")

      referenceClockSource.out.unzip._1.map { o =>
        o.clock := clock_wire
        o.reset := reset_wire
      }

      chiptop.harnessFunctions += ((th: HasHarnessUtils) => {
        clock_io := th.harnessClock
        Nil })
    }
  }
}
