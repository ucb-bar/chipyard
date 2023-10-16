//See LICENSE for license details.

package firesim.firesim

import scala.collection.mutable.{LinkedHashMap}

import chisel3._
import chisel3.experimental.{IO, annotate}

import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, SubsystemDriveAsyncClockGroupsKey, HasTiles}
import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, InModuleBody, ValName}
import freechips.rocketchip.util.{ResetCatchAndSync, RecordMap}
import freechips.rocketchip.tile.{RocketTile}
import boom.common.{BoomTile}

import midas.widgets.{Bridge, PeekPokeBridge, RationalClockBridge, RationalClock, ResetPulseBridge, ResetPulseBridgeParameters}
import midas.targetutils.{MemModelAnnotation, EnableModelMultiThreadingAnnotation}
import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import chipyard.clocking._

case object FireSimMultiCycleRegFile extends Field[Boolean](false)
case object FireSimFAME5 extends Field[Boolean](false)

/**
  * Under FireSim's current multiclock implementation there can be only a
  * single clock bridge. This requires, therefore, that it  be instantiated in
  * the harness and reused across all supernode instances. This class attempts to
  * memoize its instantiation such that it can be referenced from within a ClockScheme function.
  */
class FireSimClockBridgeInstantiator extends HarnessClockInstantiator {
  // connect all clock wires specified to the RationalClockBridge
  def instantiateHarnessClocks(refClock: Clock, refClockFreqMHz: Double): Unit = {
    val sinks = clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))), name=Some(name))
    }).toSeq

    val pllConfig = new SimplePllConfiguration("firesimRationalClockBridge", sinks)
    pllConfig.emitSummaries()

    var instantiatedClocks = LinkedHashMap[Int, (Clock, Seq[String])]()
    // connect wires to clock source
    def findOrInstantiate(freqMHz: Int, name: String): Clock = {
      if (!instantiatedClocks.contains(freqMHz)) {
        val clock = Wire(Clock())
        instantiatedClocks(freqMHz) = (clock, Seq(name))
      } else {
        instantiatedClocks(freqMHz) = (instantiatedClocks(freqMHz)._1, instantiatedClocks(freqMHz)._2 :+ name)
      }
      instantiatedClocks(freqMHz)._1
    }
    for ((name, (freq, clock)) <- clockMap) {
      val freqMHz = (freq / (1000 * 1000)).toInt
      clock := findOrInstantiate(freqMHz, name)
    }

    // The undivided reference clock as calculated by pllConfig must be instantiated
    findOrInstantiate(pllConfig.referenceFreqMHz.toInt, "reference")

    val ratClocks = instantiatedClocks.map { case (freqMHz, (clock, names)) =>
      (RationalClock(names.mkString(","), 1, pllConfig.referenceFreqMHz.toInt / freqMHz), clock)
    }.toSeq
    val clockBridge = Module(new RationalClockBridge(ratClocks.map(_._1)))
    (clockBridge.io.clocks zip ratClocks).foreach { case (clk, rat) =>
      rat._2 := clk
    }
  }
}

class FireSim(implicit val p: Parameters) extends RawModule with HasHarnessInstantiators {
  require(harnessClockInstantiator.isInstanceOf[FireSimClockBridgeInstantiator])
  freechips.rocketchip.util.property.cover.setPropLib(new midas.passes.FireSimPropertyLibrary())

  // The peek-poke bridge must still be instantiated even though it's
  // functionally unused. This will be removed in a future PR.
  val dummy = WireInit(false.B)
  val peekPokeBridge = PeekPokeBridge(harnessBinderClock, dummy)

  val resetBridge = Module(new ResetPulseBridge(ResetPulseBridgeParameters()))
  // In effect, the bridge counts the length of the reset in terms of this clock.
  resetBridge.io.clock := harnessBinderClock

  def referenceClockFreqMHz = 0.0
  def referenceClock = false.B.asClock // unused
  def referenceReset = resetBridge.io.reset
  def success = { require(false, "success should not be used in Firesim"); false.B }

  override val supportsMultiChip = true

  val chiptops = instantiateChipTops()

  // Ensures FireSim-synthesized assertions and instrumentation is disabled
  // while resetBridge.io.reset is asserted.  This ensures assertions do not fire at
  // time zero in the event their local reset is delayed (typically because it
  // has been pipelined)
  midas.targetutils.GlobalResetCondition(resetBridge.io.reset)


  // FireSim multi-cycle regfile optimization
  // FireSim ModelMultithreading
  chiptops.foreach {
    case c: ChipTop => c.lazySystem match {
      case ls: HasTiles => {
        if (p(FireSimMultiCycleRegFile)) ls.tiles.map {
          case r: RocketTile => {
            annotate(MemModelAnnotation(r.module.core.rocketImpl.rf.rf))
            r.module.fpuOpt.foreach(fpu => annotate(MemModelAnnotation(fpu.fpuImpl.regfile)))
          }
          case b: BoomTile => {
            val core = b.module.core
            core.iregfile match {
              case irf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(irf.regfile))
            }
            if (core.fp_pipeline != null) core.fp_pipeline.fregfile match {
              case frf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(frf.regfile))
            }
          }
          case _ =>
        }
        if (p(FireSimFAME5)) ls.tiles.map {
          case b: BoomTile =>
            annotate(EnableModelMultiThreadingAnnotation(b.module))
          case r: RocketTile =>
            annotate(EnableModelMultiThreadingAnnotation(r.module))
          case _ => Nil
        }
      }
      case _ =>
    }
    case _ =>
  }
}
