package tracegen

import chisel3._
import chisel3.util.log2Ceil
import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.groundtest.{TraceGenParams, TraceGenTileAttachParams}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.BaseConfig
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.tile.{MaxHartIdBits}
import scala.math.{max, min}

class WithTraceGen(
  n: Int = 2,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nSets = 16, nWays = 1) },
  nReqs: Int = 8192,
  wordBits: Int = 64
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = up(NumTiles)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)
    params.zipWithIndex.map { case (dcp, i) =>
      TraceGenTileAttachParams(
        tileParams = TraceGenParams(
          tileId = i + idOffset,
          dcache = Some(dcp),
          wordBits = wordBits,
          addrBits = 48,
          addrBag = {
            val nSets = dcp.nSets
            val nWays = dcp.nWays
            val blockOffset = site(SystemBusKey).blockOffset
            val nBeats = min(2, site(SystemBusKey).blockBeats)
            val beatBytes = site(SystemBusKey).beatBytes
            List.tabulate(2 * nWays) { i =>
              Seq.tabulate(nBeats) { j =>
                BigInt((j * beatBytes) + ((i * nSets) << blockOffset))
              }
            }.flatten
          },
          maxRequests = nReqs,
          memStart = memOffset,
          numGens = params.size),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  case NumTiles => up(NumTiles) + n
})

class WithBoomV3TraceGen(
  n: Int = 2,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nMSHRs = 4, nSets = 16, nWays = 2) },
  nReqs: Int = 8192,
  wordBits: Int = 64
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = up(NumTiles)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)
    params.zipWithIndex.map { case (dcp, i) =>
      boom.v3.lsu.BoomTraceGenTileAttachParams(
        tileParams = boom.v3.lsu.BoomTraceGenParams(
          tileId = i + idOffset,
          dcache = Some(dcp),
          wordBits = wordBits,
          addrBits = 48,
          addrBag = {
            val nSets = dcp.nSets
            val nWays = dcp.nWays
            val blockOffset = site(SystemBusKey).blockOffset
            val nBeats = site(SystemBusKey).blockBeats
            List.tabulate(nWays) { i =>
              Seq.tabulate(nBeats) { j => BigInt((j * 8) + ((i * nSets) << blockOffset)) }
            }.flatten
          },
          maxRequests = nReqs,
          memStart = memOffset,
          numGens = params.size),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  case NumTiles => up(NumTiles) + n
})

class WithBoomV4TraceGen(
  n: Int = 2,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nMSHRs = 4, nSets = 16, nWays = 2) },
  nReqs: Int = 8192,
  wordBits: Int = 64
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = up(NumTiles)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)
    params.zipWithIndex.map { case (dcp, i) =>
      boom.v4.lsu.BoomTraceGenTileAttachParams(
        tileParams = boom.v4.lsu.BoomTraceGenParams(
          tileId = i + idOffset,
          dcache = Some(dcp),
          wordBits = wordBits,
          addrBits = 48,
          addrBag = {
            val nSets = dcp.nSets
            val nWays = dcp.nWays
            val blockOffset = site(SystemBusKey).blockOffset
            val nBeats = site(SystemBusKey).blockBeats
            List.tabulate(nWays) { i =>
              Seq.tabulate(nBeats) { j => BigInt((j * 8) + ((i * nSets) << blockOffset)) }
            }.flatten
          },
          maxRequests = nReqs,
          memStart = memOffset,
          numGens = params.size),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  case NumTiles => up(NumTiles) + n
})

class WithL2TraceGen(
  n: Int = 2,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nSets = 16, nWays = 1) },
  nReqs: Int = 8192,
  wordBits: Int = 64
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = up(NumTiles)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)

    params.zipWithIndex.map { case (dcp, i) =>
      TraceGenTileAttachParams(
        tileParams = TraceGenParams(
          tileId = i + idOffset,
          dcache = Some(dcp),
          wordBits = wordBits,
          addrBits = 48,
          addrBag = {
            val sbp = site(SystemBusKey)
            val l2p = site(InclusiveCacheKey)
            val nSets = max(l2p.sets, dcp.nSets)
            val nWays = max(l2p.ways, dcp.nWays)
            val blockOffset = sbp.blockOffset
            val nBeats = min(2, sbp.blockBeats)
            val beatBytes = sbp.beatBytes
            List.tabulate(2 * nWays) { i =>
              Seq.tabulate(nBeats) { j =>
                BigInt((j * beatBytes) + ((i * nSets) << blockOffset))
              }
            }.flatten
          },
          maxRequests = nReqs,
          memStart = memOffset,
          numGens = params.size),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  case NumTiles => up(NumTiles) + n
})
