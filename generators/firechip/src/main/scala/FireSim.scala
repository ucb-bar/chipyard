//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey}
import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, InModuleBody, ValName}
import freechips.rocketchip.util.{ResetCatchAndSync, RecordMap}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge, RationalClock}

import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import chipyard.clocking.{FrequencyUtils, ClockGroupNamePrefixer, ClockGroupFrequencySpecifier, SimplePllConfiguration}

// Determines the number of times to instantiate the DUT in the harness.
// Subsumes legacy supernode support
case object NumNodes extends Field[Int](1)

class WithNumNodes(n: Int) extends Config((pname, site, here) => {
  case NumNodes => n
})

// Hacky: Set before each node is generated. Ideally we'd give IO binders
// accesses to the the Harness's parameters instance. We could then alter that.
object NodeIdx {
  private var idx = 0
  def increment(): Unit = {idx = idx + 1 }
  def apply(): Int = idx
}


/**
  * Under FireSim's current multiclock implementation there can be only a
  * single clock bridge. This requires, therefore, that it  be instantiated in
  * the harness and reused across all supernode instances. This class attempts to 
  * memoize its instantiation such that it can be referenced from within a ClockScheme function.
  */
class ClockBridgeInstantiator {
  private var _clockRecord: Option[RecordMap[Clock]] = None

  def getClockRecord: RecordMap[Clock] = _clockRecord.get

  def getClockRecordOrInstantiate(allClocks: Seq[RationalClock], baseClockName: String): RecordMap[Clock] = {
    if (_clockRecord.isEmpty) {
      require(allClocks.exists(_.name == baseClockName),
        s"Provided base-clock name, ${baseClockName}, does not match a defined clock. Available clocks:\n " +
        allClocks.map(_.name).mkString("\n "))

      val baseClock = allClocks.find(_.name == baseClockName).get
      val simplified = allClocks.map { c =>
        c.copy(multiplier = c.multiplier * baseClock.divisor, divisor = c.divisor * baseClock.multiplier)
         .simplify
      }

    /**
      * Removes clocks that have the same frequency before instantiating the
      * clock bridge to avoid unnecessary BUFGCE use.
      */
      val distinct = simplified.foldLeft(Seq(RationalClock(baseClockName, 1, 1))) { case (list, candidate) =>
        if (list.exists { clock => clock.equalFrequency(candidate) }) list else list :+ candidate
      }

      val clockBridge = Module(new RationalClockBridge(distinct))
      val cbVecTuples = distinct.zip(clockBridge.io.clocks)
      val outputWire = Wire(RecordMap(simplified.map { c => (c.name, Clock()) }:_*))
      for (parameter <- simplified) {
        val (_, cbClockField) = cbVecTuples.find(_._1.equalFrequency(parameter)).get
        outputWire(parameter.name).get := cbClockField
      }
      _clockRecord = Some(outputWire)
    }
    getClockRecord
  }
}

case object ClockBridgeInstantiatorKey extends Field[ClockBridgeInstantiator](new ClockBridgeInstantiator)
case object FireSimBaseClockNameKey extends Field[String]("implicit_clock")

class WithFireSimSimpleClocks extends Config((site, here, up) => {
  case ClockingSchemeKey => { chiptop: ChipTop =>
    implicit val p = chiptop.p
    // Figure out what provides this in the chipyard scheme
    implicit val valName = ValName("FireSimClocking")

    // Requires existence of undriven asyncClockGroups in subsystem
    val systemAsyncClockGroup = chiptop.lazySystem match {
      case l: BaseSubsystem if (p(SubsystemDriveAsyncClockGroupsKey).isEmpty) =>
        l.asyncClockGroupsNode
    }

    val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
    (chiptop.implicitClockSinkNode := ClockGroup() := aggregator)
    (systemAsyncClockGroup := ClockGroupNamePrefixer() := aggregator)

    val inputClockSource = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))

    (aggregator
      := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
      := inputClockSource)


    InModuleBody {
      val (clockGroupBundle, clockGroupEdge) = inputClockSource.out.head
      val input_clocks = IO(Input(RecordMap((clockGroupEdge.sink.members.map { m => (m.name.get, Clock()) }):_* )))
        .suggestName("clocks")
      val reset = IO(Input(Reset())).suggestName("reset")

      (clockGroupBundle.member.data zip input_clocks.data).foreach { case (clockBundle, inputClock) =>
        clockBundle.clock := inputClock
      }

      // Assign resets. The synchronization scheme is still WIP.
      for ((name, clockBundle) <- clockGroupBundle.member.elements) {
        if (name.contains("core")) {
            clockBundle.reset := ResetCatchAndSync(clockBundle.clock, reset.asBool)
        } else {
            clockBundle.reset := reset
        }
      }

      val pllConfig = new SimplePllConfiguration("FireSim RationalClockBridge", clockGroupEdge.sink.members)
      val rationalClockSpecs = for ((sinkP, division) <- pllConfig.sinkDividerMap) yield {
        RationalClock(sinkP.name.get, 1, division)
      }

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
        reset := th.harnessReset
        input_clocks := p(ClockBridgeInstantiatorKey)
          .getClockRecordOrInstantiate(rationalClockSpecs.toSeq, p(FireSimBaseClockNameKey))
        Nil })
    }
  }
})

class FireSim(implicit val p: Parameters) extends RawModule with HasHarnessSignalReferences {
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
      ApplyHarnessBinders(this, d.lazySystem, p(HarnessBinders), d.portMap.toMap)
    }
    NodeIdx.increment()
  }
  harnessClock := p(ClockBridgeInstantiatorKey).getClockRecord("implicit_clock").get
}
