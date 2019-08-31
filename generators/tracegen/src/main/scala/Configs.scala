package tracegen

import chisel3._
import chisel3.util.log2Ceil
import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.groundtest.{TraceGenParams}
import freechips.rocketchip.subsystem.{ExtMem, SystemBusKey, WithInclusiveCache, InclusiveCacheKey}
import freechips.rocketchip.system.BaseConfig
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.tile.{MaxHartIdBits, XLen}
import scala.math.{max, min}
import testchipip.WithRingSystemBus

class WithTraceGen(params: Seq[DCacheParams], nReqs: Int = 8192)
    extends Config((site, here, up) => {
  case TraceGenKey => params.map { dcp => TraceGenParams(
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
    memStart = site(ExtMem).get.master.base,
    numGens = params.size)
  }
  case MaxHartIdBits => if (params.size == 1) 1 else log2Ceil(params.size)
})

class TraceGenConfig extends Config(
  new WithTraceGen(List.fill(2) { DCacheParams(nMSHRs = 0, nSets = 16, nWays = 2) }) ++
  new BaseConfig)

class NonBlockingTraceGenConfig extends Config(
  new WithTraceGen(List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new BaseConfig)

class WithL2TraceGen(params: Seq[DCacheParams], nReqs: Int = 8192)
    extends Config((site, here, up) => {
  case TraceGenKey => params.map { dcp => TraceGenParams(
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
    memStart = site(ExtMem).get.master.base,
    numGens = params.size)
  }
  case MaxHartIdBits => if (params.size == 1) 1 else log2Ceil(params.size)
})

class NonBlockingTraceGenL2Config extends Config(
  new WithL2TraceGen(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new WithInclusiveCache ++
  new BaseConfig)

class NonBlockingTraceGenL2RingConfig extends Config(
  new WithRingSystemBus ++ new NonBlockingTraceGenL2Config)
