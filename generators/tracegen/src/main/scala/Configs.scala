package tracegen

import chisel3._
import chisel3.util.log2Ceil
import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.groundtest.{TraceGenParams, TraceGenTileAttachParams}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.BaseConfig
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.tile.{MaxHartIdBits, XLen}
import scala.math.{max, min}

class WithTraceGen(
  n: Int = 2,
  overrideIdOffset: Option[Int] = None,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nSets = 16, nWays = 1) },
  nReqs: Int = 8192
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)
    params.zipWithIndex.map { case (dcp, i) =>
      TraceGenTileAttachParams(
        tileParams = TraceGenParams(
          hartId = i + idOffset,
          dcache = Some(dcp),
          wordBits = site(XLen),
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
})

class WithBoomTraceGen(
  n: Int = 2,
  overrideIdOffset: Option[Int] = None,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nMSHRs = 4, nSets = 16, nWays = 2) },
  nReqs: Int = 8192
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)
    params.zipWithIndex.map { case (dcp, i) =>
      BoomTraceGenTileAttachParams(
        tileParams = BoomTraceGenParams(
          hartId = i + idOffset,
          dcache = Some(dcp),
          wordBits = site(XLen),
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
})

class WithL2TraceGen(
  n: Int = 2,
  overrideIdOffset: Option[Int] = None,
  overrideMemOffset: Option[BigInt] = None)(
  params: Seq[DCacheParams] = List.fill(n){ DCacheParams(nSets = 16, nWays = 1) },
  nReqs: Int = 8192
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    val memOffset: BigInt = overrideMemOffset.orElse(site(ExtMem).map(_.master.base)).getOrElse(0x0L)

    params.zipWithIndex.map { case (dcp, i) =>
      TraceGenTileAttachParams(
        tileParams = TraceGenParams(
          hartId = i + idOffset,
          dcache = Some(dcp),
          wordBits = site(XLen),
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
})
