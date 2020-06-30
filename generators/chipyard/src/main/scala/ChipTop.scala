package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.util.{ResetCatchAndSync}
import chipyard.config.ConfigValName._
import chipyard.iobinders.{IOBinders, TestHarnessFunction, IOBinderTuple}

import barstools.iocell.chisel._

case object BuildSystem extends Field[Parameters => LazyModule]((p: Parameters) => LazyModule(new DigitalTop()(p)))

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
 * The base class used for building chips. This constructor instantiates a module specified by the BuildSystem parameter,
 * named "system", which is an instance of DigitalTop by default. The default clock and reset for "system" are set by two
 * wires, "systemClock" and "systemReset", which are intended to be driven by traits mixed-in with this base class.
 */
abstract class BaseChipTop()(implicit val p: Parameters) extends RawModule with HasTestHarnessFunctions {

  // A publicly accessible list of IO cells (useful for a floorplanning tool, for example)
  val iocells = ArrayBuffer.empty[IOCell]
  // A list of functions to call in the test harness
  val harnessFunctions = ArrayBuffer.empty[TestHarnessFunction]
  // The system clock
  // These are given so that IOCell can use DataMirror and generate ports with
  // the right flow (Input/Output)
  val systemClock = Wire(Input(Clock()))
  val systemReset = Wire(Input(Reset()))

  // The system module specified by BuildSystem
  val lSystem = p(BuildSystem)(p).suggestName("system")
  val system = withClockAndReset(systemClock, systemReset) { Module(lSystem.module) }


  // Call all of the IOBinders and provide them with a default clock and reset
  withClockAndReset(systemClock, systemReset) {
    // Call each IOBinder on both the lazyModule instance and the module
    // instance. Generally, an IOBinder PF should only be defined on one, so
    // this should not lead to two invocations.
    val (_ports, _iocells, _harnessFunctions) = p(IOBinders).values.flatMap(f => f(lSystem, p) ++ f(system, p)).unzip3
    // We ignore _ports for now...
    iocells ++= _iocells.flatten
    harnessFunctions ++= _harnessFunctions.flatten
  }

}

/**
 * A simple clock and reset implementation that punches out clock and reset ports with the same
 * names as the implicit clock and reset for standard Module classes. Three basic reset schemes 
 * are provided. See [[GlobalResetScheme]].
 */
trait HasChipTopSimpleClockAndReset { this: BaseChipTop =>

  val (clock, systemClockIO) = IOCell.generateIOFromSignal(systemClock, Some("iocell_clock"))
  val (reset, systemResetIO) = p(GlobalResetSchemeKey) match {
    case GlobalResetSynchronous  =>
      IOCell.generateIOFromSignal(systemReset, Some("iocell_reset"))
    case GlobalResetAsynchronousFull =>
      IOCell.generateIOFromSignal(systemReset, Some("iocell_reset"), abstractResetAsAsync = true)
    case GlobalResetAsynchronous =>
      val asyncResetCore = Wire(Input(AsyncReset()))
      systemReset := ResetCatchAndSync(systemClock, asyncResetCore.asBool)
      IOCell.generateIOFromSignal(asyncResetCore, Some("iocell_reset"), abstractResetAsAsync = true)
  }

  iocells ++= systemClockIO
  iocells ++= systemResetIO

  // Add a TestHarnessFunction that connects clock and reset
  harnessFunctions += { (th: TestHarness) => {
    // Connect clock; it's not done implicitly with RawModule
    clock := th.clock
    // Connect reset; it's not done implicitly with RawModule
    // Note that we need to use dutReset, not harnessReset
    reset := th.dutReset
    Nil
  } }

}

class ChipTop()(implicit p: Parameters) extends BaseChipTop()(p)
  with HasChipTopSimpleClockAndReset
