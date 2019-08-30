package tracegen

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{LazyModule, SynchronousCrossing}
import freechips.rocketchip.groundtest.{TraceGenerator, TraceGenParams, DummyPTW, GroundTestStatus}
import freechips.rocketchip.rocket.{DCache, NonBlockingDCache, SimpleHellaCacheIF}
import freechips.rocketchip.tile.{BaseTile, BaseTileModuleImp, HartsWontDeduplicate}
import freechips.rocketchip.tilelink.{TLInwardNode, TLIdentityNode}
import freechips.rocketchip.interrupts._

class TraceGenTile(val id: Int, val params: TraceGenParams, q: Parameters)
    extends BaseTile(params, SynchronousCrossing(), HartsWontDeduplicate(params), q) {
  val dcache = params.dcache.map { dc => LazyModule(
    if (dc.nMSHRs == 0) new DCache(hartId, crossing)
    else new NonBlockingDCache(hartId))
  }.get

  val intInwardNode: IntInwardNode = IntIdentityNode()
  val intOutwardNode: IntOutwardNode = IntIdentityNode()
  val slaveNode: TLInwardNode = TLIdentityNode()
  val ceaseNode: IntOutwardNode = IntIdentityNode()
  val haltNode: IntOutwardNode = IntIdentityNode()
  val wfiNode: IntOutwardNode = IntIdentityNode()

  val masterNode = visibilityNode
  masterNode := dcache.node

  override lazy val module = new TraceGenTileModuleImp(this)
}

class TraceGenTileModuleImp(outer: TraceGenTile)
    extends BaseTileModuleImp(outer) {
  val status = IO(new GroundTestStatus)
  val halt_and_catch_fire = None

  val ptw = Module(new DummyPTW(1))
  ptw.io.requestors.head <> outer.dcache.module.io.ptw

  val tracegen = Module(new TraceGenerator(outer.params))
  tracegen.io.hartid := constants.hartid

  val dcacheIF = Module(new SimpleHellaCacheIF())
  dcacheIF.io.requestor <> tracegen.io.mem
  outer.dcache.module.io.cpu <> dcacheIF.io.cache

  status.finished := tracegen.io.finished
  status.timeout.valid := tracegen.io.timeout
  status.timeout.bits := 0.U
  status.error.valid := false.B

  assert(!tracegen.io.timeout, s"TraceGen tile ${outer.id}: request timed out")
}
