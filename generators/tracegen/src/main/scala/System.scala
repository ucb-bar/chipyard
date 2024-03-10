package tracegen

import chisel3._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, BufferParams}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkPortSimple, NullIntSyncSource, IntSyncXbar}
import freechips.rocketchip.groundtest.{DebugCombiner, TraceGenParams, GroundTestTile}
import freechips.rocketchip.subsystem._

class TraceGenSystem(implicit p: Parameters) extends BaseSubsystem
    with InstantiatesHierarchicalElements
    with HasTileNotificationSinks
    with HasTileInputConstants
    with HasHierarchicalElementsRootContext
    with HasHierarchicalElements
    with CanHaveMasterAXI4MemPort {

  def coreMonitorBundles = Nil

  val tileStatusNodes = totalTiles.values.toSeq.collect {
    case t: GroundTestTile => t.statusNode.makeSink()
    case t: boom.v3.lsu.BoomTraceGenTile => t.statusNode.makeSink()
    case t: boom.v4.lsu.BoomTraceGenTile => t.statusNode.makeSink()
  }

  lazy val fakeClockDomain = locateTLBusWrapper("sbus").generateSynchronousDomain

  lazy val clintOpt = None
  lazy val debugOpt = None
  lazy val plicOpt = None
  lazy val clintDomainOpt = Some(fakeClockDomain)
  lazy val plicDomainOpt = Some(fakeClockDomain)

  override lazy val module = new TraceGenSystemModuleImp(this)
}

class TraceGenSystemModuleImp(outer: TraceGenSystem)
  extends BaseSubsystemModuleImp(outer)
{
  val success = IO(Output(Bool()))

  val status = dontTouch(DebugCombiner(outer.tileStatusNodes.map(_.bundle)))

  success := outer.tileCeaseSinkNode.in.head._1.asUInt.andR

}
