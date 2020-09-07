package tracegen

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, BufferParams}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkPortSimple}
import freechips.rocketchip.groundtest.{DebugCombiner, TraceGenParams, GroundTestTile}
import freechips.rocketchip.subsystem._

class TraceGenSystem(implicit p: Parameters) extends BaseSubsystem
    with HasTiles
    with CanHaveMasterAXI4MemPort {

  def coreMonitorBundles = Nil
  val tileStatusNodes = tiles.collect {
    case t: GroundTestTile => t.statusNode.makeSink()
    case t: BoomTraceGenTile => t.statusNode.makeSink()
  }
  override lazy val module = new TraceGenSystemModuleImp(this)
}

class TraceGenSystemModuleImp(outer: TraceGenSystem)
  extends BaseSubsystemModuleImp(outer)
{
  val success = IO(Output(Bool()))

  val status = dontTouch(DebugCombiner(outer.tileStatusNodes.map(_.bundle)))

  success := outer.tileCeaseSinkNode.in.head._1.asUInt.andR

}
