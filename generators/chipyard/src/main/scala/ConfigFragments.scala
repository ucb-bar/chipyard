package chipyard.config

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, 
  WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32,
  CacheBlockBytes, BroadcastKey, BankedL2Key,
  WithInclusiveCache, InclusiveCacheKey}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{Debug}
import freechips.rocketchip.tile.{XLen, BuildRoCC, TileKey, LazyRoCC, RocketTileParams, MaxHartIdBits}
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.util.{AsyncResetReg}

import boom.common.{BoomTilesKey}

import ariane.{ArianeTilesKey}

import icenet.IceNetConsts._
import testchipip._

import hwacha.{Hwacha}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.inclusivecache.InclusiveCachePortParameters

import memblade.manager.{MemBladeKey, MemBladeParams, MemBladeQueueParams}
import memblade.client.{RemoteMemClientKey, RemoteMemClientConfig}
import memblade.cache._
import memblade.prefetcher._

import chipyard.{BuildTop, BuildSystem}

/**
 * TODO: Why do we need this?
 */
object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

// -----------------------
// Common Config Fragments
// -----------------------

class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

// DOC include start: gpio config fragment
class WithGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(
    GPIOParams(address = 0x10012000, width = 4, includeIOF = false))
})
// DOC include end: gpio config fragment

class WithUART extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256))
})

class WithNoGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq()
})

class WithL2TLBs(entries: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nL2TLBEntries = entries)
  ))
  case BoomTilesKey => up(BoomTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nL2TLBEntries = entries)
  ))
})

class WithTracegenSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => LazyModule(new tracegen.TraceGenSystem()(p))
})

class WithMemBladeSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) =>
    LazyModule(new chipyard.MemBladeTop()(p))
})

class WithRemoteMemClientSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) =>
    LazyModule(new chipyard.RemoteMemClientTop()(p))
})

class WithDRAMCacheSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) =>
    LazyModule(new chipyard.DRAMCacheTop()(p))
})

class WithRenumberHarts(rocketFirst: Boolean = false) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site).zipWithIndex map { case (r, i) =>
    r.copy(hartId = i + (if(rocketFirst) 0 else up(BoomTilesKey, site).length))
  }
  case BoomTilesKey => up(BoomTilesKey, site).zipWithIndex map { case (b, i) =>
    b.copy(hartId = i + (if(rocketFirst) up(RocketTilesKey, site).length else 0))
  }
  case MaxHartIdBits => log2Up(up(BoomTilesKey, site).size + up(RocketTilesKey, site).size)
})



// ------------------
// Multi-RoCC Support
// ------------------

/**
 * Map from a hartId to a particular RoCC accelerator
 */
case object MultiRoCCKey extends Field[Map[Int, Seq[Parameters => LazyRoCC]]](Map.empty[Int, Seq[Parameters => LazyRoCC]])

/**
 * Config fragment to enable different RoCCs based on the hartId
 */
class WithMultiRoCC extends Config((site, here, up) => {
  case BuildRoCC => site(MultiRoCCKey).getOrElse(site(TileKey).hartId, Nil)
})

/**
 * Config fragment to add Hwachas to cores based on hart
 *
 * For ex:
 *   Core 0, 1, 2, 3 have been defined earlier
 *     with hartIds of 0, 1, 2, 3 respectively
 *   And you call WithMultiRoCCHwacha(0,1)
 *   Then Core 0 and 1 will get a Hwacha
 *
 * @param harts harts to specify which will get a Hwacha
 */
class WithMultiRoCCHwacha(harts: Int*) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    require(harts.max <= ((up(RocketTilesKey, site).length + up(BoomTilesKey, site).length) - 1))
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        LazyModule(new Hwacha()(p)).suggestName("hwacha")
      }))
    }
  }
})


/**
 * Config fragment to add a small Rocket core to the system as a "control" core.
 * Used as an example of a PMU core.
 */
class WithControlCore extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) :+
    RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        fpu = None,
        mulDiv = Some(MulDivParams(mulUnroll = 8))),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,
        nWays = 1,
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 64,
        nWays = 1,
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes))),
      hartId = up(RocketTilesKey, site).size + up(BoomTilesKey, site).size
    )
  case MaxHartIdBits => log2Up(up(RocketTilesKey, site).size + up(BoomTilesKey, site).size + 1)
})

class WithMemBladeKey(spanBytes: Option[Int] = None) extends Config(
  (site, here, up) => {
    case MemBladeKey => {
      val spanBytesVal = spanBytes.getOrElse(site(CacheBlockBytes))
      val nSpanBeats = spanBytesVal / NET_IF_BYTES
      val commonQueueParams = MemBladeQueueParams(
        reqHeadDepth = 2,
        reqDataDepth = nSpanBeats,
        respHeadDepth = 2,
        respDataDepth = nSpanBeats)
      val nTrackers = site(BroadcastKey).nTrackers
      val nBanks = site(BankedL2Key).nBanks

      MemBladeParams(
        spanBytes = spanBytesVal,
        nSpanTrackers = nTrackers,
        nWordTrackers = 4,
        nBanks = nBanks,
        spanQueue = commonQueueParams,
        wordQueue = commonQueueParams,
        bankQueue = commonQueueParams)
    }
  }
)

class WithRemoteMemClientKey(spanBytes: Int = 1024) extends Config((site, here, up) => {
  case RemoteMemClientKey => RemoteMemClientConfig(
    spanBytes = spanBytes,
    nMemXacts = 12,
    nRMemXacts = 32768 / spanBytes)
})

class WithDRAMCacheKey(
    nTrackersPerBank: Int,
    nBanksPerChannel: Int,
    nChannels: Int = 1) extends Config((site, here, up) => {
  case DRAMCacheKey => DRAMCacheConfig(
    nSets = 1 << 21,
    nWays = 7,
    nMetaCacheRows = 256,
    baseAddr = BigInt(1) << 37,
    nTrackersPerBank = nTrackersPerBank,
    nBanksPerChannel = nBanksPerChannel,
    nChannels = nChannels,
    nSecondaryRequests = 1,
    spanBytes = site(CacheBlockBytes),
    logAddrBits = 37,
    outIdBits = 4,
    nWritebackRemXacts = (nTrackersPerBank + 1) * nBanksPerChannel,
    nWritebackSpans = nTrackersPerBank,
    remAccessQueue = RemoteAccessDepths(1, 8, 1, 8),
    wbQueue = WritebackDepths(1, 1),
    memInQueue = MemoryQueueParams(0, 0, 2, 2, 2, 2),
    memOutQueue = MemoryQueueParams(2, 2, 2, 2, 2, 2),
    buildChannelOutNetwork = OutNetwork.ring,
    writebackArbTopology = NetworkTopology.Ring,
    remChannelArbTopology = NetworkTopology.Ring,
    zeroMetadata = false,
    lruReplacement = false)
})

class WithDRAMCacheExtentTableInit(suffix: Int = 0x0300) extends Config(
  (site, here, up) => {
    case DRAMCacheKey => up(DRAMCacheKey).copy(
      extentTableInit = Seq.tabulate(16) { i => (suffix, i + 1) })
  })

class WithMemBenchKey(nWorkers: Int = 1, nXacts: Int = 64)
    extends Config((site, here, up) => {
  case MemBenchKey => MemBenchParams(
    nWorkers = nWorkers,
    nXacts = nXacts)
})

class WithRocketL2TLBs(entries: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey) map (tile => tile.copy(
    core = tile.core.copy(
      nL2TLBEntries = entries
    )
  ))
})

class WithL2InnerExteriorBuffer(aDepth: Int, dDepth: Int) extends Config(
  (site, here, up) => {
    case InclusiveCacheKey => up(InclusiveCacheKey).copy(
      bufInnerExterior = InclusiveCachePortParameters(
        aDepth, 0, 0, dDepth, 0))
  })

class WithStandardL2(
    nBeatsPerBlock: Int = 8, nTrackersPerBank: Int = 3) extends Config(
  new WithL2InnerExteriorBuffer(nBeatsPerBlock, 2) ++
  new WithInclusiveCache(
    nBanks = 4,
    nWays = 4,
    capacityKB = 256,
    outerLatencyCycles = nBeatsPerBlock * nTrackersPerBank))

class WithPrefetchMiddleMan extends Config((site, here, up) => {
  case PrefetchMiddleManKey => {
    val l2key = site(BankedL2Key)
    val ickey = site(InclusiveCacheKey)
    val dckey = site(DRAMCacheKey)
    val blockBeats = site(CacheBlockBytes) / site(SystemBusKey).beatBytes
    val nMSHRs = (ickey.memCycles - 1) / blockBeats + 1
    val nTrackers = dckey.nChannels * dckey.nBanksPerChannel * dckey.nTrackersPerBank
    SequentialPrefetchConfig(
      nWays = 4,
      nBlocks = nTrackers / l2key.nBanks,
      hitThreshold = 1,
      maxTimeout = (1 << 30) - 1,
      lookAhead = nMSHRs-1)
  }
  case MiddleManLatency => 30
})

class WithHwachaNVMTEntries(nVMT: Int) extends Config((site, here, up) => {
  case hwacha.HwachaNVMTEntries => nVMT
})

class WithHwachaConfPrec extends Config((site, here, up) => {
  case hwacha.HwachaConfPrec => true
  case hwacha.HwachaMaxVLen => up(hwacha.HwachaMaxVLen) * 4
})

class WithTraceIO extends Config((site, here, up) => {
  case BoomTilesKey => up(BoomTilesKey) map (tile => tile.copy(trace = true))
  case ArianeTilesKey => up(ArianeTilesKey) map (tile => tile.copy(trace = true))
  case TracePortKey => Some(TracePortParams())
})
