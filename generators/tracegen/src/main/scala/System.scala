package tracegen

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, BufferParams}
import freechips.rocketchip.groundtest.{DebugCombiner, TraceGenParams}
import freechips.rocketchip.subsystem._

case object TraceGenKey extends Field[Seq[TraceGenParams]]

trait HasTraceGenTiles { this: BaseSubsystem =>
  val tiles = p(TraceGenKey).zipWithIndex.map { case (params, i) =>
    LazyModule(new TraceGenTile(i, params, p))
  }

  tiles.foreach { t =>
    sbus.fromTile(None, buffer = BufferParams.default) { t.masterNode }
  }
}

trait HasTraceGenTilesModuleImp extends LazyModuleImp {
  val outer: HasTraceGenTiles
  val success = IO(Output(Bool()))

  outer.tiles.zipWithIndex.map { case(t, i) =>
    t.module.constants.hartid := i.U
  }

  val status = DebugCombiner(outer.tiles.map(_.module.status))
  success := status.finished
}

class TraceGenSystem(implicit p: Parameters) extends BaseSubsystem
    with HasTraceGenTiles
    with HasHierarchicalBusTopology
    with CanHaveMasterAXI4MemPort {
  override lazy val module = new TraceGenSystemModuleImp(this)
}

class TraceGenSystemModuleImp(outer: TraceGenSystem)
  extends BaseSubsystemModuleImp(outer)
  with HasTraceGenTilesModuleImp
  with CanHaveMasterAXI4MemPortModuleImp
