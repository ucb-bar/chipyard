package tracegen

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.groundtest._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket.constants.{MemoryOpConstants}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci._
import edu.berkeley.cs.ucie.digital._

case class UCIeTraceGenTileAttachParams(
  tileParams: UCIeTraceGenParams,
  crossingParams: TileCrossingParamsLike
) extends CanAttachTile {
  type TileType = UCIeTraceGenTile
  val lookup: LookupByHartIdImpl = HartsWontDeduplicate(tileParams)
}

case class UCIeTraceGenParams(
    wordBits: Int,
    addrBits: Int,
    addrBag: List[BigInt],
    maxRequests: Int,
    memStart: BigInt,
    numGens: Int,
    dcache: Option[DCacheParams] = Some(DCacheParams()),
    hartId: Int = 0
) extends InstantiableTileParams[UCIeTraceGenTile] with GroundTestTileParams
{
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): UCIeTraceGenTile = {
    new UCIeTraceGenTile(this, crossing, lookup)
  }
  val beuAddr = None
  val blockerCtrlAddr = None
  val name = None
  val traceParams = TraceGenParams(wordBits, addrBits, addrBag, maxRequests, memStart, numGens, dcache, hartId)
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
}

class UCIeTraceGenTile private(
  val params: UCIeTraceGenParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters
) extends GroundTestTile(params, crossing, lookup, q)
{
  def this(params: UCIeTraceGenParams, crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  val masterNode: TLOutwardNode = TLIdentityNode() := visibilityNode := dcacheOpt.map(_.node).getOrElse(TLTempNode())

  override lazy val module = new UCIeTraceGenTileModuleImp(this)
}

class UCIeTraceGenTileModuleImp(outer: UCIeTraceGenTile) extends GroundTestTileModuleImp(outer) {

  val tracegen = Module(new TraceGenerator(outer.params.traceParams))
  tracegen.io.hartid := outer.hartIdSinkNode.bundle

  outer.dcacheOpt foreach { dcache =>
    val dcacheIF = Module(new SimpleHellaCacheIF())
    dcacheIF.io.requestor <> tracegen.io.mem
    dcache.module.io.cpu <> dcacheIF.io.cache
    tracegen.io.fence_rdy := dcache.module.io.cpu.ordered
  }

  outer.reportCease(Some(tracegen.io.finished))
  outer.reportHalt(Some(tracegen.io.timeout))
  outer.reportWFI(None)
  status.timeout.valid := tracegen.io.timeout
  status.timeout.bits := 0.U
  status.error.valid := false.B

  assert(!tracegen.io.timeout, s"UCIeTraceGen tile ${outer.tileParams.hartId}: request timed out")
}
