package shuttle.common

import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.{BootROMParams}
import freechips.rocketchip.prci.{SynchronousCrossing, AsynchronousCrossing, RationalCrossing}
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import shuttle.dmem.{ShuttleSGTCMParams}

class WithNShuttleCores(
  n: Int,
  retireWidth: Int,
  location: HierarchicalLocation,
  crossing: ShuttleCrossingParams,
) extends Config((site, here, up) => {
  case TilesLocated(`location`) => {
    val prev = up(TilesLocated(location), site)
    val idOffset = up(NumTiles)
      (0 until n).map { i =>
        ShuttleTileAttachParams(
          tileParams = ShuttleTileParams(
            core = ShuttleCoreParams(retireWidth = retireWidth),
            btb = Some(BTBParams(nEntries=32, bhtParams = Some(BHTParams(counterLength=2)))),
            icache = Some(
              ICacheParams(rowBits = -1, nSets=64, nWays=8, fetchBytes=2*4)
            ),
            tileId = i + idOffset
          ),
          crossingParams = crossing
        )
      } ++ prev
    }
  case NumTiles => up(NumTiles) + n
}) {
  def this(n: Int = 1, retireWidth: Int = 2, location: HierarchicalLocation = InSubsystem) = this(n, retireWidth, location, ShuttleCrossingParams(
    master = HierarchicalElementMasterPortParams.locationDefault(location),
    slave = location match {
      case InSubsystem => HierarchicalElementSlavePortParams(where=SBUS)
      case InCluster(clusterId) => HierarchicalElementSlavePortParams(where=CSBUS(clusterId), blockerCtrlWhere=CCBUS(clusterId))
    },
    mmioBaseAddressPrefixWhere = location match {
      case InSubsystem => CBUS
      case InCluster(clusterId) => CCBUS(clusterId)
    }
  ))
}

class WithShuttleRetireWidth(w: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(retireWidth = w)
    ))
    case other => other
  }

})

class WithShuttleFetchWidth(bytes: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(fetchWidth = bytes / 2),
      icache = tp.tileParams.icache.map(_.copy(fetchBytes = bytes))
    ))
    case other => other
  }
})

class WithShuttleDebugROB extends Config((site, here, up) => {
  case TilesLocated(loc) => up(TilesLocated(loc), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(debugROB = true)
    ))
    case other => other
  }

})

class WithL1ICacheSets(sets: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      icache = tp.tileParams.icache.map(_.copy(nSets = sets))
    ))
    case other => other
  }
})

class WithL1DCacheSets(sets: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcacheParams = tp.tileParams.dcacheParams.copy(nSets = sets)
    ))
    case other => other
  }
})

class WithL1ICacheWays(ways: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      icache = tp.tileParams.icache.map(_.copy(nWays = ways))
    ))
    case other => other
  }
})

class WithL1DCacheWays(ways: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcacheParams = tp.tileParams.dcacheParams.copy(nWays = ways)
    ))
    case other => other
  }
})

class WithL1DCacheMSHRs(n: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcacheParams = tp.tileParams.dcacheParams.copy(nMSHRs = n)
    ))
    case other => other
  }
})

class WithL1DCacheIOMSHRs(n: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcacheParams = tp.tileParams.dcacheParams.copy(nMMIOs = n)
    ))
    case other => other
  }
})

class WithL1DCacheBanks(n: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcacheParams = tp.tileParams.dcacheParams.copy(nBanks = n)
    ))
    case other => other
  }
})

class WithL1DCacheTagBanks(n: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcacheParams = tp.tileParams.dcacheParams.copy(nTagBanks = n)
    ))
    case other => other
  }
})

class WithTCM(address: BigInt = 0x70000000L, size: BigInt = 64L << 10, banks: Int = 4, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      tcm = Some(ShuttleTCMParams(address, size, banks))
    ))
    case other => other
  }
})

class WithSGTCM(address: BigInt = 0x78000000L, size: BigInt = 8L << 10, banks: Int = 32, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      sgtcm = Some(ShuttleSGTCMParams(address, size, banks))
    ))
    case other => other
  }
})

class WithShuttleTileBeatBytes(beatBytes: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      tileBeatBytes = beatBytes
    ))
    case other => other
  }
})

class WithAsynchronousShuttleTiles(depth: Int, sync: Int, location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      crossingType = AsynchronousCrossing()))
    case t => t
  }
})

class WithShuttleTileBoundaryBuffers(location: HierarchicalLocation = InSubsystem) extends Config((site, here, up) => {
  case TilesLocated(`location`) => up(TilesLocated(location), site) map {
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      boundaryBuffers = true
    ))
    case other => other
  }
})
