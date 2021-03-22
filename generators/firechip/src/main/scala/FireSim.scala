//See LICENSE for license details.

package firesim.firesim

import scala.collection.mutable.{LinkedHashMap}

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
import chipyard.clocking._

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
  private val _harnessClockMap: LinkedHashMap[String, (Double, Clock)] = LinkedHashMap.empty

  // Assumes that the supernode implementation results in duplicated clocks
  //   (i.e. only 1 set of clocks is generated for all BuildTop designs)
  private val _buildtopClockMap: LinkedHashMap[String, (RationalClock, Clock)] = LinkedHashMap.empty
  private var _buildtopRefTuple: Option[(String, Double)] = None

  /**
   * Request a clock at a particular frequency
   *
   * @param name An identifier for the associated clock domain
   *
   * @param freqRequested Freq. for the domain in Hz
   */
  def requestClock(name: String, freqRequested: Double): Clock = {
    val clkWire = Wire(new Clock)
    _harnessClockMap(name) = (freqRequested, clkWire)
    clkWire
  }

  /**
   * Get a RecordMap of clocks for a set of input RationalClocks
   *
   * @param allClocks Seq. of RationalClocks that want a clock
   *
   * @param baseClockName Name of domain that the allClocks is rational to
   *
   * @param baseFreqRequested Freq. for the reference domain in Hz
   */
  def requestClockRecordMap(allClocks: Seq[RationalClock], baseClockName: String, baseFreqRequested: Double): RecordMap[Clock] = {
    require(!_buildtopRefTuple.isDefined, "Can only request one RecordMap of Clocks")

    val ratClockRecordMapWire = Wire(RecordMap(allClocks.map { c => (c.name, Clock()) }:_*))

    _buildtopRefTuple = Some((baseClockName, baseFreqRequested))
    for (clock <- allClocks) {
      val clkWire = Wire(new Clock)
      _buildtopClockMap(clock.name) = (clock, clkWire)
      ratClockRecordMapWire(clock.name).get := clkWire
    }

    ratClockRecordMapWire
  }

  /**
   * Connect all clocks requested to ClockBridge
   */
  def instantiateFireSimClockBridge: Unit = {
    require(_buildtopRefTuple.isDefined, "Must have rational clocks to assign to")
    require(_buildtopClockMap.exists(_._1 == _buildtopRefTuple.get._1),
      s"Provided base-clock name for rational clocks, ${_buildtopRefTuple.get._1}, doesn't match a name within specified rational clocks." +
      "Available clocks:\n " + _buildtopClockMap.map(_._1).mkString("\n "))

    // Simplify the RationalClocks ratio's
    val refRatClock = _buildtopClockMap.find(_._1 == _buildtopRefTuple.get._1).get._2._1
    val simpleRatClocks = _buildtopClockMap.map { t =>
      val ratClock = t._2._1
      ratClock.copy(
        multiplier = ratClock.multiplier * refRatClock.divisor,
        divisor = ratClock.divisor * refRatClock.multiplier).simplify
    }

    // Determine all the clock dividers (harness + rational clocks)
    //   Note: Requires that the BuildTop reference frequency is requested with proper freq.
    val refRatClockFreq = _buildtopRefTuple.get._2
    val refRatSinkParams = ClockSinkParameters(take=Some(ClockParameters(freqMHz=refRatClockFreq / (1000 * 1000))),name=Some(_buildtopRefTuple.get._1))
    val harSinkParams = _harnessClockMap.map { case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))),name=Some(name))
    }.toSeq
    val allSinkParams = harSinkParams :+ refRatSinkParams

    // Use PLL config to determine overall div's
    val pllConfig = new SimplePllConfiguration("firesimOverallClockBridge", allSinkParams)
    pllConfig.emitSummaries

    // Adjust all BuildTop RationalClocks with the div determined by the PLL
    val refRatDiv = pllConfig.sinkDividerMap(refRatSinkParams)
    val adjRefRatClocks = simpleRatClocks.map { clock =>
      clock.copy(divisor = clock.divisor * refRatDiv).simplify
    }

    // Convert harness clocks to RationalClocks
    val harRatClocks = harSinkParams.map { case ClockSinkParameters(_, _, _, _, clkParamsOpt, nameOpt) =>
      RationalClock(nameOpt.get, 1, pllConfig.referenceFreqMHz.toInt / clkParamsOpt.get.freqMHz.toInt)
    }

    val allAdjRatClks = adjRefRatClocks ++ harRatClocks

    // Removes clocks that have the same frequency before instantiating the
    //   clock bridge to avoid unnecessary BUFGCE use.
    val allDistinctRatClocks = allAdjRatClks.foldLeft(Seq(RationalClock(pllConfig.referenceSinkParams.name.get, 1, 1))) {
      case (list, candidate) => if (list.exists { clock => clock.equalFrequency(candidate) }) list else list :+ candidate
    }

    val clockBridge = Module(new RationalClockBridge(allDistinctRatClocks))
    val cbVecTuples = allDistinctRatClocks.zip(clockBridge.io.clocks)

    // Connect all clocks (harness + BuildTop clocks)
    for (clock <- allAdjRatClks) {
      val (_, cbClockField) = cbVecTuples.find(_._1.equalFrequency(clock)).get
      _buildtopClockMap.get(clock.name).map { case (_, clk) => clk := cbClockField }
      _harnessClockMap.get(clock.name).map { case (_, clk) => clk := cbClockField }
    }
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
    (systemAsyncClockGroup :*= ClockGroupNamePrefixer() :*= aggregator)

    val inputClockSource = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))

    (aggregator
      := ClockGroupResetSynchronizer()
      := ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
      := inputClockSource)


    InModuleBody {
      val (clockGroupBundle, clockGroupEdge) = inputClockSource.out.head
      val input_clocks = IO(Input(RecordMap((clockGroupEdge.sink.members.map { m => (m.name.get, Clock()) }):_* )))
        .suggestName("clocks")
      val reset = IO(Input(Reset())).suggestName("reset")

      (clockGroupBundle.member.data zip input_clocks.data).foreach { case (clockBundle, inputClock) =>
        clockBundle.clock := inputClock
        clockBundle.reset := reset
      }

      val pllConfig = new SimplePllConfiguration("firesimBuildTopClockGenerator", clockGroupEdge.sink.members)
      pllConfig.emitSummaries
      val rationalClockSpecs = for ((sinkP, division) <- pllConfig.sinkDividerMap) yield {
        RationalClock(sinkP.name.get, 1, division)
      }

      chiptop.harnessFunctions += ((th: HasHarnessSignalReferences) => {
        reset := th.buildtopReset
        input_clocks := p(ClockBridgeInstantiatorKey)
          .requestClockRecordMap(rationalClockSpecs.toSeq, p(FireSimBaseClockNameKey), pllConfig.referenceFreqMHz * (1000 * 1000))
        Nil })

      // return the reference frequency
      pllConfig.referenceFreqMHz
    }
  }
})

class FireSim(implicit val p: Parameters) extends RawModule with HasHarnessSignalReferences {
  freechips.rocketchip.util.property.cover.setPropLib(new midas.passes.FireSimPropertyLibrary())

  val buildtopClock = Wire(Clock())
  val buildtopReset = WireInit(false.B)
  val peekPokeBridge = PeekPokeBridge(buildtopClock, buildtopReset)
  def dutReset = { require(false, "dutReset should not be used in Firesim"); false.B }
  def success = { require(false, "success should not be used in Firesim"); false.B }

  var btFreqMHz: Option[Double] = None

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

    btFreqMHz = Some(lazyModule match {
      case d: HasReferenceClockFreq => d.refClockFreqMHz
      case _ => p(DefaultClockFrequencyKey)
    })

    lazyModule match { case d: HasTestHarnessFunctions =>
      require(d.harnessFunctions.size == 1, "There should only be 1 harness function to connect clock+reset")
      d.harnessFunctions.foreach(_(this))
    }
    lazyModule match { case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
    }
    NodeIdx.increment()
  }

  buildtopClock := p(ClockBridgeInstantiatorKey).requestClock("buildtop_reference_clock", btFreqMHz.get * (1000 * 1000))

  p(ClockBridgeInstantiatorKey).instantiateFireSimClockBridge
}
