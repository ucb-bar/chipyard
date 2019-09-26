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
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket.TracedInstruction
import firesim.endpoints.{TraceOutputTop, DeclockedTracedInstruction}

import midas.targetutils.{ExcludeInstanceAsserts, MemModelAnnotation}

/* Wires out tile trace ports to the top; and wraps them in a Bundle that the
 * TracerV endpoint can match on.
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
    traceprint := Cat(traceIO.traces.map(_.asUInt))
    printf("TRACEPORT: %x\n", traceprint)
  }
}

// Prevent MIDAS from synthesizing assertions in the dummy TLB included in BOOM
trait ExcludeInvalidBoomAssertions extends LazyModuleImp {
  ExcludeInstanceAsserts(("NonBlockingDCache", "dtlb"))
}

trait CanHaveMultiCycleRegfileImp {
  val outer: utilities.HasBoomAndRocketTiles
  val boomCores = outer.boomTiles.map(tile => tile.module.core)
  boomCores.foreach({ core =>
    core.iregfile match {
      case irf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(irf.regfile))
      case _ => Nil
    }

     if (core.fp_pipeline != null) core.fp_pipeline.fregfile match {
      case irf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(irf.regfile))
      case _ => Nil
    }
  })

  outer.rocketTiles.foreach({ tile =>
    annotate(MemModelAnnotation(tile.module.core.rocketImpl.rf.rf))
    tile.module.fpuOpt.foreach(fpu => annotate(MemModelAnnotation(fpu.fpuImpl.regfile)))
  })
}
