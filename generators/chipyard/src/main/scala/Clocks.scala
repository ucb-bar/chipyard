package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey}
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy.{OutwardNodeHandle, InModuleBody, LazyModule}
import freechips.rocketchip.util.{ResetCatchAndSync, Pow2ClockDivider}

import barstools.iocell.chisel._

import chipyard.clocking.{IdealizedPLL, ClockGroupNamePrefixer, ClockGroupFrequencySpecifier}

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


case object ClockingSchemeKey extends Field[ChipTop => Unit](ClockingSchemeGenerators.idealizedPLL)
/**
  * This is a Seq of assignment functions, that accept a clock name and return an optional frequency.
  * Functions that appear later in this seq have higher precedence that earlier ones.
  * If no function returns a non-empty value, the value specified in
  * [[DefaultClockFrequencyKey]] will be used -- DFU.
  */
case object ClockFrequencyAssignersKey extends Field[Seq[(String) => Option[Double]]](Seq.empty)
case object DefaultClockFrequencyKey extends Field[Double]()

class ClockNameMatchesAssignment(name: String, fMHz: Double) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => if (cName == name) Some(fMHz) else None)
})

class ClockNameContainsAssignment(name: String, fMHz: Double) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => if (cName.contains(name)) Some(fMHz) else None)
})

object ClockingSchemeGenerators {
  val idealizedPLL: ChipTop => Unit = { chiptop =>
    implicit val p = chiptop.p

    // Requires existence of undriven asyncClockGroups in subsystem
    val systemAsyncClockGroup = chiptop.lazySystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) =>
        l.asyncClockGroupsNode
    }

    val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
    chiptop.implicitClockSinkNode := ClockGroup() := aggregator
    systemAsyncClockGroup := ClockGroupNamePrefixer() := aggregator

    val referenceClockSource =  ClockSourceNode(Seq(ClockSourceParameters()))
    (aggregator
      := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
      := IdealizedPLL()
      := referenceClockSource)


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

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
        clock_io := th.harnessClock
        Nil })
    }
  }
}
