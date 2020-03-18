package chipyard

import chisel3._
import chisel3.util._

import freechips.rocketchip.subsystem.{BaseSubsystem, HasTiles}
import freechips.rocketchip.config.{Field, Config}
import freechips.rocketchip.diplomacy.{LazyModule, AddressSet, LazyModuleImp, BundleBridgeNexus}
import freechips.rocketchip.tilelink.{TLRAM}
import freechips.rocketchip.rocket.TracedInstruction
import freechips.rocketchip.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.{RocketTile}

import boom.common.{BoomTile}

import ariane.{ArianeTile}

import testchipip.{ExtendedTracedInstruction, TraceOutputTop, DeclockedTracedInstruction}

case class TracePortParams(
  print: Boolean = false
)

object TracePortKey extends Field[Option[TracePortParams]](None)

trait CanHaveTraceIO { this: HasChipyardTiles =>
  val module: CanHaveTraceIOModuleImp

  // Bind all the trace nodes to a BB; we'll use this to generate the IO in the imp
  val traceNexus = BundleBridgeNexus[Vec[TracedInstruction]]
  val tileTraceNodes = tiles.collect {
    case r: RocketTile => r
    case a: ArianeTile => a
  }.map { _.traceNode }

  val extTraceNexus = BundleBridgeNexus[Vec[ExtendedTracedInstruction]]
  val extTileTraceNodes = tiles.collect {
    case b: BoomTile => b
  }.map { _.extTraceNode }

  // Convert all instructions to extended type
  tileTraceNodes.foreach { traceNexus := _ }
  extTileTraceNodes.foreach { extTraceNexus := _ }
}

trait CanHaveTraceIOModuleImp extends LazyModuleImp {
  val outer: CanHaveTraceIO

  val traceIO = p(TracePortKey) map ( p => {

    // convert traceNexus signals into single lists
    val declkedPortsTypes =  DeclockedTracedInstruction.fromNode(outer.traceNexus.in) ++ DeclockedTracedInstruction.fromExtNode(outer.extTraceNexus.in)
    val declkedPorts = (outer.traceNexus.in).map {
      case (tileTrace, _) => DeclockedTracedInstruction.fromVec(tileTrace)
    } ++ (outer.extTraceNexus.in).map {
      case (tileTrace, _) => DeclockedTracedInstruction.fromExtVec(tileTrace)
    }

    // create io
    val trace_io = IO(new TraceOutputTop(declkedPortsTypes))

    // connect the traces to the top-level
    (trace_io.traces zip declkedPorts).foreach({ case (port, tileTracePort) =>
      port := tileTracePort
    })

    // conditional print
    if (p.print) {
      val traceprint = Wire(UInt(trace_io.traces.getWidth.W))
      traceprint := Cat(trace_io.traces.map(_.reverse.asUInt))
      printf("TRACEPORT: %x\n", traceprint)
    }

    trace_io
  })
}
