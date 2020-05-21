package tracegen

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, BufferParams, ValName}
import freechips.rocketchip.groundtest.{DebugCombiner, TraceGenParams}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkPortSimple}

case object BoomTraceGenKey extends Field[Seq[TraceGenParams]](Nil)
case object TraceGenKey extends Field[Seq[TraceGenParams]](Nil)

trait HasTraceGenTiles { this: BaseSubsystem =>
  val rocket_tiles = p(TraceGenKey).zipWithIndex.map { case (params, i) =>
    LazyModule(new TraceGenTile(i, params, p))
  }
  val boom_tiles = p(BoomTraceGenKey).zipWithIndex.map { case (params, i) =>
    LazyModule(new BoomTraceGenTile(i, params, p))
  }

  val tiles = rocket_tiles ++ boom_tiles

  tiles.foreach { t =>
    sbus.fromTile(None, buffer = BufferParams.default) { t.masterNode }
  }

  implicit val valName = ValName(this.name)
  IntSinkNode(IntSinkPortSimple()) :=* ibus.toPLIC
}

trait HasTraceGenTilesModuleImp extends LazyModuleImp {
  val outer: HasTraceGenTiles
  val success = IO(Output(Bool()))

  outer.tiles.zipWithIndex.map { case(t, i) =>
    t.module.constants.hartid := i.U
  }

  val status = DebugCombiner(
    outer.rocket_tiles.map(_.module.status) ++
    outer.boom_tiles.map(_.module.status)
  )
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

class DRAMCacheTraceGenSystem(implicit p: Parameters) extends BaseSubsystem
    with HasTraceGenTiles
    with HasHierarchicalBusTopology
    with CanHaveMasterAXI4MemPort
    with memblade.cache.HasPeripheryDRAMCache {
  override lazy val module = new DRAMCacheTraceGenSystemModuleImp(this)
}

class DRAMCacheTraceGenSystemModuleImp(outer: DRAMCacheTraceGenSystem)
  extends BaseSubsystemModuleImp(outer)
  with HasTraceGenTilesModuleImp
  with memblade.cache.HasPeripheryDRAMCacheModuleImp
