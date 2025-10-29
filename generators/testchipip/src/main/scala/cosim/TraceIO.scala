package testchipip.cosim

import chisel3._
import chisel3.util._

import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink.{TLRAM}
import freechips.rocketchip.rocket.{TracedInstruction}
import freechips.rocketchip.util._
import freechips.rocketchip.tile.{BaseTile, TraceBundle}
import freechips.rocketchip.diplomacy.{BundleBridgeSource, BundleBroadcast, BundleBridgeNexusNode}

//***************************************************************************
// Trace Instruction Utilities:
// used to connect the TracerV or Dromajo bridges (in FireSim and normal sim)
//***************************************************************************

case class TraceBundleWidths(retireWidth: Int, iaddr: Int, insn: Int, wdata: Option[Int], cause: Int, tval: Int, custom: Option[Int])

object TraceBundleWidths {
  def apply(t: TraceBundle): TraceBundleWidths = TraceBundleWidths(
    retireWidth = t.insns.size,
    iaddr = t.insns.head.iaddr.getWidth,
    insn = t.insns.head.insn.getWidth,
    wdata = t.insns.head.wdata.map(_.getWidth),
    cause = t.insns.head.cause.getWidth,
    tval = t.insns.head.tval.getWidth,
    custom = t.custom.map(_.getWidth))
}

// A TracedInstruction that can have its parameters serialized (insnWidths is serializable)
class SerializableTracedInstruction(widths: TraceBundleWidths) extends Bundle {
  val valid = Bool()
  val iaddr = UInt(widths.iaddr.W)
  val insn = UInt(widths.insn.W)
  val wdata = widths.wdata.map { w => UInt(w.W) }
  val priv = UInt(3.W)
  val exception = Bool()
  val interrupt = Bool()
  val cause = UInt(widths.cause.W)
  val tval = UInt(widths.tval.W)
}

class SerializableTraceBundle(val widths: TraceBundleWidths) extends Bundle {
  val insns = Vec(widths.retireWidth, new SerializableTracedInstruction(widths))
  val time = UInt(64.W)
  val custom = widths.custom.map { w => UInt(w.W) }
}


class SerializableTileTraceIO(val widths: TraceBundleWidths) extends Bundle {
  val clock = Clock()
  val reset = Bool()
  val trace = new SerializableTraceBundle(widths)
}

// A per-tile interface that includes the tile's clock and reset
class TileTraceIO(_traceType: TraceBundle) extends Bundle {
  val clock = Clock()
  val reset = Bool()
  val trace = traceType.cloneType
  def traceType = _traceType
  def numInsns = traceType.insns.size
  def traceBundleWidths = TraceBundleWidths(traceType)
  def serializableType = new SerializableTileTraceIO(traceBundleWidths)
  def asSerializableTileTrace: SerializableTileTraceIO = {
    val serializable_trace = Wire(serializableType)
    serializable_trace.clock := clock
    serializable_trace.reset := reset
    serializable_trace.trace.insns.zip(trace.insns).map{ case (l, r) =>
      l.valid := r.valid
      l.iaddr := r.iaddr
      l.insn := r.insn
      l.wdata.zip(r.wdata).map { case (l, r) => l := r }
      l.priv := r.priv
      l.exception := r.exception
      l.interrupt := r.interrupt
      l.cause := r.cause
      l.tval := r.tval
    }
    serializable_trace.trace.time := trace.time
    serializable_trace.trace.custom.zip(trace.custom).map { case (l, r) => l := r.asTypeOf(l) }
    serializable_trace
  }
}

// The IO matched on by the TracerV bridge: a wrapper around a heterogenous
// bag of vectors. Each entry is trace associated with a single tile (vector of committed instructions + clock + reset)
class TraceOutputTop(coreTraces: Seq[TraceBundle]) extends Bundle {
  val traces = Output(HeterogeneousBag.apply(coreTraces.map(t => new TileTraceIO(t))))
}

//**********************************************
// Trace IO Key/Traits:
// Used to enable/add the tport on the top level
//**********************************************

case class TracePortParams(
  print: Boolean = false
)

object TracePortKey extends Field[Option[TracePortParams]](None)

trait CanHaveTraceIO { this: HasHierarchicalElementsRootContext with InstantiatesHierarchicalElements =>
  implicit val p: Parameters

  val tileTraceNodes = traceNodes.values

  val traceIO = InModuleBody { p(TracePortKey) map ( traceParams => {
    val tileTraces = tileTraceNodes.map(_.in(0)._1).toSeq
    val tio = IO(Output(new TraceOutputTop(tileTraces)))

    // Since clock & reset are not included with the traced instruction, plumb that out manually
    (tio.traces zip (tile_prci_domains.values zip tileTraces)).foreach { case (port, (prci, trace)) =>
      port.clock := prci.module.clock
      port.reset := prci.module.reset.asBool
      port.trace := trace
    }


    if (traceParams.print) {
      for ((trace, idx) <- tio.traces.zipWithIndex ) {
        withClockAndReset(trace.clock, trace.reset) {
          // The reverse is here to match the behavior the Cat used in the bridge
          printf(s"TRACEPORT ${idx}: %x\n", trace.trace.insns.reverse.asUInt.pad(512))
        }
      }
    }

    tio
  })}
}
