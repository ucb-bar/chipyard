package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.prci.{ClockGroupIdentityNode, ClockSinkParameters, ClockSinkNode, ClockGroup}
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, LazyRawModuleImp, LazyModuleImpLike}
import freechips.rocketchip.util.{ResetCatchAndSync}
import chipyard.iobinders.{IOBinders, TestHarnessFunction, IOBinderTuple}

import barstools.iocell.chisel._

case object BuildSystem extends Field[Parameters => LazyModule]((p: Parameters) => new DigitalTop()(p))


/**
 * The base class used for building chips. This constructor instantiates a module specified by the BuildSystem parameter,
 * named "system", which is an instance of DigitalTop by default. The diplomatic clocks of System, as well as its implicit clock,
 * is aggregated into the clockGroupNode. The parameterized functions controlled by ChipyardClockKey and GlobalResetSchemeKey
 * drive clock and reset generation
 */

class ChipTop(implicit p: Parameters) extends LazyModule with HasTestHarnessFunctions {
  // A publicly accessible list of IO cells (useful for a floorplanning tool, for example)
  val iocells = ArrayBuffer.empty[IOCell]
  // A list of functions to call in the test harness
  val harnessFunctions = ArrayBuffer.empty[TestHarnessFunction]

  // The system module specified by BuildSystem
  val lSystem = LazyModule(p(BuildSystem)(p)).suggestName("system")

  // The implicitClockSinkNode provides the implicit clock and reset for the System
  val implicitClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters()))

  // Generate Clocks and Reset
  p(ChipyardClockKey)(this)

  // NOTE: Making this a LazyRawModule is moderately dangerous, as anonymous children
  // of ChipTop (ex: ClockGroup) do not receive clock or reset.
  // However. anonymous children of ChipTop should not need an implicit Clock or Reset
  // anyways, they probably need to be explicitly clocked.
  lazy val module: LazyModuleImpLike = new LazyRawModuleImp(this) {
    // These become the implicit clock and reset to the System
    val implicit_clock = implicitClockSinkNode.in.head._1.clock
    val implicit_reset = implicitClockSinkNode.in.head._1.reset


    // The implicit clock and reset for the system is also, by convention, used for all the IOBinders
    // TODO: This may not be the right thing to do in all cases
    withClockAndReset(implicit_clock, implicit_reset) {
      val (_ports, _iocells, _harnessFunctions) = p(IOBinders).values.flatMap(f => f(lSystem) ++ f(lSystem.module)).unzip3
      // We ignore _ports for now...
      iocells ++= _iocells.flatten
      harnessFunctions ++= _harnessFunctions.flatten
    }

    // Connect the implicit clock/reset, if present
    lSystem.module match { case l: LazyModuleImp => {
      l.clock := implicit_clock
      l.reset := implicit_reset
    }}
  }
}

