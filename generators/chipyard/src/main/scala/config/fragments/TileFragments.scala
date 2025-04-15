package chipyard.config

import chisel3._

import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.diplomacy._

import cva6.{CVA6TileAttachParams}
import sodor.common.{SodorTileAttachParams}
import ibex.{IbexTileAttachParams}
import vexiiriscv.{VexiiRiscvTileAttachParams}
import testchipip.cosim.{TracePortKey, TracePortParams}
import barf.{TilePrefetchingMasterPortParams}
import freechips.rocketchip.trace.{TraceEncoderParams, TraceCoreParams}
import tacit.{TacitEncoder, TacitBPParams}
import shuttle.common.{ShuttleTileAttachParams}

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

// Add a Tacit encoder to each tile
class WithTacitEncoder extends Config((site, here, up) => {
   case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
     case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      traceParams = Some(TraceEncoderParams(
        encoderBaseAddr = 0x3000000 + tp.tileParams.tileId * 0x1000,
        buildEncoder = (p: Parameters) => LazyModule(new TacitEncoder(new TraceCoreParams(
          nGroups = 1,
          xlen = tp.tileParams.core.xLen,
          iaddrWidth = tp.tileParams.core.xLen
        ), 
        bufferDepth = 16, 
        coreStages = 5, 
        bpParams = TacitBPParams(xlen = tp.tileParams.core.xLen, n_entries = 1024))(p)),
        useArbiterMonitor = false
      )),
      core = tp.tileParams.core.copy(enableTraceCoreIngress=true)))
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      traceParams = Some(TraceEncoderParams(
        encoderBaseAddr = 0x3000000 + tp.tileParams.tileId * 0x1000,
        buildEncoder = (p: Parameters) => LazyModule(new TacitEncoder(new TraceCoreParams(
          nGroups = tp.tileParams.core.retireWidth,
          xlen = tp.tileParams.core.xLen,
          iaddrWidth = tp.tileParams.core.xLen
        ), 
        bufferDepth = 16, 
        coreStages = 7, 
        bpParams = TacitBPParams(xlen = tp.tileParams.core.xLen, n_entries = 1024))(p)),
        useArbiterMonitor = false
      )),
      core = tp.tileParams.core.copy(enableTraceCoreIngress=true)))
   }
 })

// Add a monitor to RTL print the sinked packets into a file for debugging
class WithTraceArbiterMonitor extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      traceParams = Some(tp.tileParams.traceParams.get.copy(useArbiterMonitor = true))))
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      traceParams = Some(tp.tileParams.traceParams.get.copy(useArbiterMonitor = true))))
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
    case tp: chipyard.SpikeTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case other => other
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
    case tp: VexiiRiscvTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
    case tp: CVA6TileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
  }
})

// Use SV48
class WithSV48 extends Config((site, here, up) => {
  case TilesLocated(loc) => up(TilesLocated(loc), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core =
      tp.tileParams.core.copy(pgLevels = 4)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core =
      tp.tileParams.core.copy(pgLevels = 4)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core =
      tp.tileParams.core.copy(pgLevels = 4)))
  }
})
