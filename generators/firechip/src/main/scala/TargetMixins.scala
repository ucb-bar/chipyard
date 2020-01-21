package firesim.firesim

import chisel3._
import chisel3.util.Cat
import chisel3.experimental.annotate
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import freechips.rocketchip.tile.RocketTile
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket.TracedInstruction
import firesim.bridges.{TracerVBridge}
import firesim.util.{HasAdditionalClocks, FireSimClockKey}

import midas.targetutils.MemModelAnnotation

import boom.common.BoomTile

/* Wires out tile trace ports to the top; and wraps them in a Bundle that the
 * TracerV bridge can match on.
 */
case object PrintTracePort extends Field[Boolean](false)
case object InstantiateTracerVBridges extends Field[Boolean](false)

trait HasTraceIO {
  this: HasTiles =>
  val module: HasTraceIOImp

  // Bind all the trace nodes to a BB; we'll use this to generate the IO in the imp
  val tileTraceNodes = tiles.map({ tile =>
    val node = BundleBridgeSink[Vec[TracedInstruction]]
    node := tile.traceNode
    node
  })
}

trait HasTraceIOImp extends LazyModuleImp {
  val outer: HasTraceIO
  outer.tileTraceNodes.zipWithIndex.foreach({ case (node, idx) =>
    if (p(InstantiateTracerVBridges)) {
      val b = TracerVBridge(node.bundle)
      // Used for verifying the TracerV bridge
      if (p(PrintTracePort)) {
        withClockAndReset(node.bundle.head.clock, node.bundle.head.reset) {
          val traceprint = WireDefault(0.U(512.W))
          // The reverse is here to match the behavior the Cat used in the bridge
          traceprint := b.io.traces.reverse.asUInt
          printf(s"TRACEPORT ${idx}: %x\n", traceprint)
        }
      }
    }
  })
}

trait CanHaveMultiCycleRegfileImp {
  val outer: utilities.HasBoomAndRocketTiles

  outer.tiles.map {
    case r: RocketTile => {
      annotate(MemModelAnnotation(r.module.core.rocketImpl.rf.rf))
      r.module.fpuOpt.foreach(fpu => annotate(MemModelAnnotation(fpu.fpuImpl.regfile)))
    }
    case b: BoomTile => {
      val core = b.module.core
      core.iregfile match {
        case irf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(irf.regfile))
        case _ => Nil
      }
      if (core.fp_pipeline != null) core.fp_pipeline.fregfile match {
        case frf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(frf.regfile))
        case _ => Nil
      }
    }
  }
}

trait HasFireSimClockingImp extends HasAdditionalClocks {
  val outer: HasTiles
  val (tileClock, tileReset) = p(FireSimClockKey).additionalClocks.headOption match {
    case Some((numer, denom)) if numer != denom => (clocks(1), ResetCatchAndSync(clocks(1), reset.toBool))
    case None => (clocks(0), reset)
  }

  outer.tiles.foreach({ case tile =>
    tile.module.clock := tileClock
    tile.module.reset := tileReset
  })
}
