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
  override lazy val module = new TraceGenSystemModuleImp(this)
}

class TraceGenSystemModuleImp(outer: TraceGenSystem)
  extends BaseSubsystemModuleImp(outer)
{
  val success = IO(Output(Bool()))

  outer.tiles.zipWithIndex.map { case(t, i) => t.module.constants.hartid := i.U }

  val status = dontTouch(DebugCombiner(outer.tiles.collect { case t: GroundTestTile => t.module.status }))
  success := outer.tileCeaseSinkNode.in.head._1.asUInt.andR

}
