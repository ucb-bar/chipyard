//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey, InstantiatesTiles}
import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, InModuleBody, ValName}
import freechips.rocketchip.util.{ResetCatchAndSync, RecordMap}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge, RationalClock}

import testchipip.{TLTileResetCtrl}

import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import chipyard.clocking._

object PDESClockingSchemes {
  // Mirrors chipyard's
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
    (chiptop.implicitClockSinkNode
      := ClockGroup()
      := aggregator)
    (systemAsyncClockGroup
      :*= resetSetter
      :*= ClockGroupNamePrefixer()
      :*= aggregator)

    val referenceClockSource =  ClockSourceNode(Seq(ClockSourceParameters()))
    (aggregator
      := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
      := ClockGroupResetSynchronizer()
      := DividerOnlyClockGenerator()
      := referenceClockSource)


    val clockAndReset = InModuleBody { referenceClockSource.makeIOs() }

    chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
      val (_, edge) = referenceClockSource.out.head
      val refPeriod = BigInt(Math.round(1e6 / edge.sink.take.get.freqMHz).toLong)
      val clockSource = Module(new midas.widgets.BlackBoxClockSourceBridge(refPeriod))
      clockAndReset.head.clock := clockSource.io.clockOut
      clockAndReset.head.reset := th.harnessReset
      th.harnessClock := clockSource.io.clockOut
      Nil })
  }

  val tileClockMuxing: ChipTop => Unit = { chiptop =>
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

    val tileClockMuxes = chiptop.lazySystem match {
      case sys: BaseSubsystem with InstantiatesTiles => TLTileClockMuxes(sys)
      case _ => ClockGroupEphemeralNode()
    }
    val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
    (chiptop.implicitClockSinkNode
      := ClockGroup()
      := aggregator)
    (systemAsyncClockGroup
      :*= resetSetter
      :*= ClockGroupNamePrefixer()
      :*= aggregator)

    val referenceClockSource =  ClockSourceNode(Seq(ClockSourceParameters()))
    (aggregator
      := tileClockMuxes
      := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
      := ClockGroupResetSynchronizer()
      := DividerOnlyClockGenerator()
      := referenceClockSource)


    val clockAndReset = InModuleBody { referenceClockSource.makeIOs() }

    chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
      val (_, edge) = referenceClockSource.out.head
      val refPeriod = BigInt(Math.round(1e6 / edge.sink.take.get.freqMHz).toLong)
      val clockSource = Module(new midas.widgets.BlackBoxClockSourceBridge(refPeriod))
      clockAndReset.head.clock := clockSource.io.clockOut
      clockAndReset.head.reset := th.harnessReset
      th.harnessClock := clockSource.io.clockOut
      Nil })
  }
}

class WithChipyardLikeClocking extends Config((site, here, up) => {
  case ClockingSchemeKey => PDESClockingSchemes.dividerOnlyClockGenerator
})

class WithTileClockMuxes extends Config((site, here, up) => {
  case ClockingSchemeKey => PDESClockingSchemes.tileClockMuxing
})


class FireSimPDES(implicit val p: Parameters) extends RawModule with HasHarnessSignalReferences {
  freechips.rocketchip.util.property.cover.setPropLib(new midas.passes.FireSimPropertyLibrary())
  val harnessClock = Wire(Clock())
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
    lazyModule match { case d: HasTestHarnessFunctions =>
      require(d.harnessFunctions.size == 1, "There should only be 1 harness function to connect clock+reset")
      d.harnessFunctions.foreach(_(this))
    }
    lazyModule match { case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
    }
    NodeIdx.increment()
  }
}
