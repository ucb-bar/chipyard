package chipyard.config

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{Debug}
import freechips.rocketchip.groundtest.{GroundTestSubsystem}
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.util.{AsyncResetReg}

import icenet.IceNetConsts._
import testchipip._
import tracegen.{TraceGenSystem}

import hwacha.{Hwacha}

import boom.common.{BoomTileAttachParams}
import ariane.{ArianeTileAttachParams}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.inclusivecache.InclusiveCachePortParameters

import memblade.manager.{MemBladeKey, MemBladeParams, MemBladeQueueParams}
import memblade.client.{RemoteMemClientKey, RemoteMemClientConfig}
import memblade.cache._
import memblade.prefetcher._

import chipyard.{BuildTop, BuildSystem}

import scala.math.max

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

class WithSPIFlash(size: BigInt = 0x10000000) extends Config((site, here, up) => {
  // Note: the default size matches freedom with the addresses below
  case PeripherySPIFlashKey => Seq(
    SPIFlashParams(rAddress = 0x10040000, fAddress = 0x20000000, fSize = size))
})

class WithL2TLBs(entries: Int) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case other => other
  }
})

class WithTracegenSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => LazyModule(new TraceGenSystem()(p))
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
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        LazyModule(new Hwacha()(p)).suggestName("hwacha")
      }))
    }
  }
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
    nChannels: Int = 1,
    spanBytes: Option[Int] = None,
    buildCacheBank: BuildBankFunction.T = BuildBankFunction.scheduler)
      extends Config((site, here, up) => {
  case DRAMCacheKey => {
    val blockBytes = site(CacheBlockBytes)
    val realSpanBytes = spanBytes.getOrElse(blockBytes)
    val blocksPerSpan = realSpanBytes / blockBytes
    DRAMCacheConfig(
      nSets = 1 << 21,
      nWays = 7,
      nMetaCacheRows = 16384 / realSpanBytes,
      baseAddr = BigInt(1) << 37,
      nTrackersPerBank = nTrackersPerBank,
      nBanksPerChannel = nBanksPerChannel,
      nChannels = nChannels,
      nSecondaryRequests = blocksPerSpan,
      spanBytes = realSpanBytes,
      logAddrBits = 37,
      outIdBits = 4,
      nWritebackRemXacts = (nTrackersPerBank + 1) * nBanksPerChannel,
      nWritebackSpans = nTrackersPerBank,
      remAccessQueue = RemoteAccessDepths(1, 8, 1, 8),
      wbQueue = WritebackDepths(1, 1),
      memInQueue = MemoryQueueParams(0, 0, 2, 2, max(2, blocksPerSpan), 2),
      memOutQueue = MemoryQueueParams(2, 2, 2, 2, 2, 2),
      buildCacheBank = buildCacheBank,
      buildChannelOutNetwork = OutNetwork.multilevel(8),
      writebackArbTopology = NetworkTopology.Ring,
      remChannelArbTopology = NetworkTopology.Ring,
      zeroMetadata = false,
      lruReplacement = false)
  }
})

class WithDRAMCacheExtentTableInit(
    suffix: Int = 0x0300, extentOffset: Int = 0) extends Config(
  (site, here, up) => {
    case DRAMCacheKey => up(DRAMCacheKey).copy(
      extentTableInit = Seq.tabulate(16) { i => (suffix, i + extentOffset) })
  })

class WithMemBenchKey(nWorkers: Int = 1, nXacts: Int = 32)
    extends Config((site, here, up) => {
  case MemBenchKey => MemBenchParams(
    nWorkers = nWorkers,
    nXacts = nXacts)
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

class WithHwachaNVMTEntries(nVMT: Int) extends Config((site, here, up) => {
  case hwacha.HwachaNVMTEntries => nVMT
})

class WithHwachaConfPrec extends Config((site, here, up) => {
  case hwacha.HwachaConfPrec => true
  case hwacha.HwachaMaxVLen => up(hwacha.HwachaMaxVLen) * 4
})

class WithTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case tp: ArianeTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case other => other
  }
  case TracePortKey => Some(TracePortParams())
})

class WithNPerfCounters(n: Int = 29) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case other => other
  }
})
