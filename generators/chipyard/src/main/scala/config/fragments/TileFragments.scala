package chipyard.config

import chisel3._

import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams, PgLevels}

import cva6.{CVA6TileAttachParams}
import sodor.common.{SodorTileAttachParams}
import ibex.{IbexTileAttachParams}
import testchipip.cosim.{TracePortKey, TracePortParams}
import barf.{TilePrefetchingMasterPortParams}

class WithL2TLBs(entries: Int) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case other => other
  }
})

class WithTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(trace = true)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(trace = true)))
    case tp: CVA6TileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case other => other
  }
  case TracePortKey => Some(TracePortParams())
})

class WithNoTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(trace = false)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(trace = false)))
    case tp: CVA6TileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = false))
    case other => other
  }
  case TracePortKey => None
})

class WithNPerfCounters(n: Int = 29) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case other => other
  }
})

class WithNPMPs(n: Int = 8) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case other => other
  }
})

class WithRocketCacheRowBits(rowBits: Int = 64) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem)) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcache = tp.tileParams.dcache.map(_.copy(rowBits = rowBits)),
      icache = tp.tileParams.icache.map(_.copy(rowBits = rowBits))
    ))
  }
})

class WithRocketICacheScratchpad extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      icache = tp.tileParams.icache.map(_.copy(itimAddr = Some(0x300000 + tp.tileParams.tileId * 0x10000)))
    ))
  }
})

class WithRocketDCacheScratchpad extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcache = tp.tileParams.dcache.map(_.copy(nSets = 32, nWays = 1, scratch = Some(0x200000 + tp.tileParams.tileId * 0x10000)))
    ))
  }
})

class WithTilePrefetchers extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
    case tp: SodorTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
    case tp: IbexTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
    case tp: CVA6TileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
  }
})

// Adds boundary buffers to RocketTiles, which places buffers between the caches and the TileLink interface
// This typically makes it easier to close timing
class WithRocketBoundaryBuffers(buffers: Option[RocketTileBoundaryBufferParams] = Some(RocketTileBoundaryBufferParams(true))) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem)) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams=tp.tileParams.copy(
      boundaryBuffers=buffers
    ))
  }
})

// Uses SV48 if possible, otherwise default to the Rocket Chip core default
class WithSV48IfPossible extends Config((site, here, up) => {
  case PgLevels => if (site(XLen) == 64) 4 /* Sv48 */ else up(PgLevels)
})

// Uses SV39 if possible, otherwise default to the Rocket Chip core default
class WithSV39 extends Config((site, here, up) => {
  case PgLevels => { require(site(XLen) == 64); 3; }
})
