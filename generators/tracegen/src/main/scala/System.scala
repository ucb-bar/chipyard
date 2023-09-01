package tracegen

import chisel3._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.interrupts.{IntSyncXbar, NullIntSyncSource}
import freechips.rocketchip.groundtest.{DebugCombiner, GroundTestTile}
import freechips.rocketchip.subsystem._
import boom.lsu.BoomTraceGenTile

class TraceGenSystem(implicit p: Parameters) extends BaseSubsystem with HasTiles with CanHaveMasterAXI4MemPort {

  def coreMonitorBundles   = Nil
  val tileStatusNodes      = tiles.collect {
    case t: GroundTestTile   => t.statusNode.makeSink()
    case t: BoomTraceGenTile => t.statusNode.makeSink()
  }
  lazy val debugNode       = IntSyncXbar() := NullIntSyncSource()
  override lazy val module = new TraceGenSystemModuleImp(this)
}

class TraceGenSystemModuleImp(outer: TraceGenSystem) extends BaseSubsystemModuleImp(outer) {
  val success = IO(Output(Bool()))

  val status = dontTouch(DebugCombiner(outer.tileStatusNodes.map(_.bundle)))

  success := outer.tileCeaseSinkNode.in.head._1.asUInt.andR

}
