package firesim.firesim

import chisel3._
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

import midas.models.AXI4BundleWithEdge
import midas.targetutils.{ExcludeInstanceAsserts, MemModelAnnotation}

/** Copied from RC and modified to change the IO type of the Imp to include the Diplomatic edges
  *  associated with each port. This drives FASED functional model sizing
  */
trait CanHaveFASEDOptimizedMasterAXI4MemPort { this: BaseSubsystem =>
  val module: CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp

  val memAXI4Node = p(ExtMem).map { case MemoryPortParams(memPortParams, nMemoryChannels) =>
    val portName = "axi4"
    val device = new MemoryDevice

    val memAXI4Node = AXI4SlaveNode(Seq.tabulate(nMemoryChannels) { channel =>
      val base = AddressSet.misaligned(memPortParams.base, memPortParams.size)
      val filter = AddressSet(channel * mbus.blockBytes, ~((nMemoryChannels-1) * mbus.blockBytes))

      AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = base.flatMap(_.intersect(filter)),
          resources     = device.reg,
          regionType    = RegionType.UNCACHED, // cacheable
          executable    = true,
          supportsWrite = TransferSizes(1, mbus.blockBytes),
          supportsRead  = TransferSizes(1, mbus.blockBytes),
          interleavedId = Some(0))), // slave does not interleave read responses
        beatBytes = memPortParams.beatBytes)
    })

    memAXI4Node := mbus.toDRAMController(Some(portName)) {
      AXI4UserYanker() := AXI4IdIndexer(memPortParams.idBits) := TLToAXI4()
    }

    memAXI4Node
  }
}

/** Actually generates the corresponding IO in the concrete Module */
trait CanHaveFASEDOptimizedMasterAXI4MemPortModuleImp extends LazyModuleImp {
  val outer: CanHaveFASEDOptimizedMasterAXI4MemPort

  val mem_axi4 = outer.memAXI4Node.map(x => IO(HeterogeneousBag(AXI4BundleWithEdge.fromNode(x.in))))
  (mem_axi4 zip outer.memAXI4Node) foreach { case (io, node) =>
    (io zip node.in).foreach { case (io, (bundle, _)) => io <> bundle }
  }

  def connectSimAXIMem() {
    (mem_axi4 zip outer.memAXI4Node).foreach { case (io, node) =>
      (io zip node.in).foreach { case (io, (_, edge)) =>
        val mem = LazyModule(new SimAXIMem(edge, size = p(ExtMem).get.master.size))
        Module(mem.module).io.axi4.head <> io
      }
    }
  }
}

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
    traceprint := traceIO.asUInt
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
  // DOC include start: ChiselAnnotation
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
  // DOC include end: ChiselAnnotation

  outer.rocketTiles.foreach({ tile =>
    annotate(MemModelAnnotation(tile.module.core.rocketImpl.rf.rf))
    tile.module.fpuOpt.foreach(fpu => annotate(MemModelAnnotation(fpu.fpuImpl.regfile)))
  })
}
