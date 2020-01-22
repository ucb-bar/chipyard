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
import firesim.bridges.{TraceOutputTop, DeclockedTracedInstruction}

import midas.targetutils.MemModelAnnotation

import boom.common.BoomTile

/* Wires out tile trace ports to the top; and wraps them in a Bundle that the
 * TracerV bridge can match on.
 */
object PrintTracePort extends Field[Boolean](false)

trait HasTraceIO {
  this: HasTiles =>
  val module: HasTraceIOImp

  // Bind all the trace nodes to a BB; we'll use this to generate the IO in the imp
  val traceNexus = BundleBridgeNexus[Vec[TracedInstruction]]
  val tileTraceNodes = tiles.map(tile => tile.traceNode)
  tileTraceNodes foreach { traceNexus := _ }
}

trait HasTraceIOImp extends LazyModuleImp {
  val outer: HasTraceIO

  val traceIO = IO(Output(new TraceOutputTop(
    DeclockedTracedInstruction.fromNode(outer.traceNexus.in))))
  (traceIO.traces zip outer.traceNexus.in).foreach({ case (port, (tileTrace, _)) =>
    port := DeclockedTracedInstruction.fromVec(tileTrace)
  })

  // Enabled to test TracerV trace capture
  if (p(PrintTracePort)) {
    val traceprint = Wire(UInt(512.W))
    traceprint := Cat(traceIO.traces.map(_.reverse.asUInt))
    printf("TRACEPORT: %x\n", traceprint)
  }
}

trait CanHaveMultiCycleRegfileImp {
  val outer: chipyard.HasBoomAndRocketTiles

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

