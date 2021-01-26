package chipyard

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey, InstantiatesTiles}
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy.{OutwardNodeHandle, InModuleBody, LazyModule}
import freechips.rocketchip.util.{ResetCatchAndSync}

import barstools.iocell.chisel._
import testchipip.{TLTileResetCtrl}

import chipyard.clocking._

/**
 * A simple reset implementation that punches out reset ports
 * for standard Module classes. Three basic reset schemes
 * are provided. See [[GlobalResetScheme]].
 */
object GenerateReset {
  def apply(chiptop: ChipTop, clock: Clock): Reset = {
    implicit val p = chiptop.p
    // this needs directionality so generateIOFromSignal works
    val async_reset_wire = Wire(Input(AsyncReset()))
    val (reset_io, resetIOCell) = IOCell.generateIOFromSignal(async_reset_wire, "reset",
      abstractResetAsAsync = true)

    chiptop.iocells ++= resetIOCell
    chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
      reset_io := th.dutReset
      Nil
    })
    async_reset_wire
  }
}


case object ClockingSchemeKey extends Field[ChipTop => Unit](ClockingSchemeGenerators.dividerOnlyClockGenerator)
/*
  * This is a Seq of assignment functions, that accept a clock name and return an optional frequency.
  * Functions that appear later in this seq have higher precedence that earlier ones.
  * If no function returns a non-empty value, the value specified in
  * [[DefaultClockFrequencyKey]] will be used.
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
  val dividerOnlyClockGenerator: ChipTop => Unit = { chiptop =>
    implicit val p = chiptop.p

    // Requires existence of undriven asyncClockGroups in subsystem
    val systemAsyncClockGroup = chiptop.lazySystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) =>
        l.asyncClockGroupsNode
    }

    // Add a control register for each tile's reset
    val resetSetter = chiptop.lazySystem match {
      case sys: BaseSubsystem with InstantiatesTiles => TLTileResetCtrl(sys)
      case _ => ClockGroupEphemeralNode()
    }

    val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
    // provides the implicit clock to the system
    (chiptop.implicitClockSinkNode
      := ClockGroup()
      := aggregator)
    // provides the system clocks (ex. the bus clocks)
    (systemAsyncClockGroup
      :*= resetSetter
      :*= ClockGroupNamePrefixer()
      :*= aggregator)

    val referenceClockSource =  ClockSourceNode(Seq(ClockSourceParameters()))
    // provides all the divided clocks (from the top-level clock)
    (aggregator
      := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
      := ClockGroupResetSynchronizer()
      := DividerOnlyClockGenerator()
      := referenceClockSource)

    InModuleBody {
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = GenerateReset(chiptop, clock_wire)
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock")
      chiptop.iocells ++= clockIOCell

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
