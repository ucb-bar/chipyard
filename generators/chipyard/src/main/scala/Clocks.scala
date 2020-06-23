package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{OutwardNodeHandle, InModuleBody}
import freechips.rocketchip.util.{ResetCatchAndSync}
import chipyard.config.ConfigValName._

import barstools.iocell.chisel._

import ChipyardClockDrivers._

case object ChipyardClockKey extends Field[ClockInstantiationFn](simpleTestHarnessClock)


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
    chiptop.harnessFunctions += ((th: TestHarness) => {
      reset_io := th.dutReset
      Nil
    })
    reset_wire
  }
}

object ChipyardClockDrivers {
  type ClockInstantiationFn = ChipTop => OutwardNodeHandle[ClockGroupSourceParameters, ClockGroupSinkParameters, ClockGroupEdgeParameters, ClockGroupBundle]

  // A simple clock provider, for testing. All clocks in system are aggregated into one,
  // and are driven by directly punching out to the TestHarness clock
  val simpleTestHarnessClock: ClockInstantiationFn = { chiptop =>
    implicit val p = chiptop.p
    val simpleClockGroupSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
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

      chiptop.harnessFunctions += ((th: TestHarness) => {
        clock_io := th.clock
        Nil
      })
    }
    ClockGroupAggregator() := simpleClockGroupSourceNode
  }
}
