package firesim.firesim

import java.io.File

import chisel3.util.{log2Up}
import example.{WithMultiRoCC, WithMultiRoCCHwacha}
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.groundtest.TraceGenParams
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{DebugModuleParams, DebugModuleKey}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import boom.common.BoomTilesKey
import testchipip.{BlockDeviceKey, BlockDeviceConfig, MemBenchKey, MemBenchParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.blocks.inclusivecache.InclusiveCachePortParameters
import memblade.manager.{MemBladeKey, MemBladeParams, MemBladeQueueParams}
import memblade.client.{RemoteMemClientKey, RemoteMemClientConfig}
import memblade.cache._
import memblade.prefetcher._
import scala.math.{min, max}
import tracegen.TraceGenKey
import icenet._
import icenet.IceNetConsts._
import scala.math.max
import testchipip.WithRingSystemBus

import firesim.bridges._
import firesim.util.{WithNumNodes}
import firesim.configs._

class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => {
    val chipyardBootROM = new File(s"./generators/testchipip/bootrom/bootrom.rv${site(XLen)}.img")
    val firesimBootROM = new File(s"./target-rtl/chipyard/generators/testchipip/bootrom/bootrom.rv${site(XLen)}.img")

    val bootROMPath = if (chipyardBootROM.exists()) {
      chipyardBootROM.getAbsolutePath()
    } else {
      firesimBootROM.getAbsolutePath()
    }
    BootROMParams(contentFileName = bootROMPath)
  }
})

class WithPeripheryBusFrequency(freq: BigInt) extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey).copy(frequency=freq)
})

class WithUARTKey extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(
     address = BigInt(0x54000000L),
     nTxEntries = 256,
     nRxEntries = 256))
})

class WithBlockDevice extends Config(new testchipip.WithBlockDevice)

class WithNICKey extends Config((site, here, up) => {
  case NICKey => NICConfig(
    inBufFlits = 8192,
    ctrlQueueDepth = 64,
    usePauser = true,
    checksumOffload = true)
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
    nRMemXacts = 32768 / spanBytes)
})

class WithDRAMCacheKey(
    nTrackersPerBank: Int,
    nBanksPerChannel: Int,
    nChannels: Int = 1) extends Config((site, here, up) => {
  case DRAMCacheKey => DRAMCacheConfig(
    nSets = 1 << 21,
    nWays = 7,
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
    zeroMetadata = false)
})

class WithDRAMCacheExtentTableInit(suffix: Int = 0x0300) extends Config(
  (site, here, up) => {
    case DRAMCacheKey => up(DRAMCacheKey).copy(
      extentTableInit = Seq.tabulate(16) { i => (suffix, i + 1) })
  })

class WithMemBenchKey(nXacts: Int = 64) extends Config((site, here, up) => {
  case MemBenchKey => MemBenchParams(nXacts = nXacts)
})

class WithRocketL2TLBs(entries: Int) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey) map (tile => tile.copy(
    core = tile.core.copy(
      nL2TLBEntries = entries
    )
  ))
})

class WithPerfCounters extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nPerfCounters = 29)
  ))
})

class WithBoomL2TLBs(entries: Int) extends Config((site, here, up) => {
  case BoomTilesKey => up(BoomTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nL2TLBEntries = entries)
  ))
})

// Disables clock-gating; doesn't play nice with our FAME-1 pass
class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
})

// Testing configurations
// This enables printfs used in testing
class WithScalaTestFeatures extends Config((site, here, up) => {
    case PrintTracePort => true
})

// FASED Config Aliases. This to enable config generation via "_" concatenation
// which requires that all config classes be defined in the same package
class DDR3FRFCFS extends FRFCFS16GBQuadRank
class DDR3FRFCFSLLC4MB extends FRFCFS16GBQuadRankLLC4MB

// L2 Config Aliases. For use with "_" concatenation
class L2SingleBank512K extends freechips.rocketchip.subsystem.WithInclusiveCache

/*******************************************************************************
* Full TARGET_CONFIG configurations. These set parameters of the target being
* simulated.
*
* In general, if you're adding or removing features from any of these, you
* should CREATE A NEW ONE, WITH A NEW NAME. This is because the manager
* will store this name as part of the tags for the AGFI, so that later you can
* reconstruct what is in a particular AGFI. These tags are also used to
* determine which driver to build.
*******************************************************************************/
class FireSimRocketChipConfig extends Config(
  new WithBootROM ++
  new WithPeripheryBusFrequency(BigInt(3200000000L)) ++
  new WithExtMemSize(0x400000000L) ++ // 16GB
  new WithoutTLMonitors ++
  new WithUARTKey ++
  new WithNICKey ++
  new WithBlockDevice ++
  new WithRocketL2TLBs(1024) ++
  new WithPerfCounters ++
  new WithoutClockGating ++
  new WithDefaultMemModel ++
  new WithDefaultFireSimBridges ++
  new freechips.rocketchip.system.DefaultConfig)

class WithNDuplicatedRocketCores(n: Int) extends Config((site, here, up) => {
  case RocketTilesKey => List.tabulate(n)(i => up(RocketTilesKey).head.copy(hartId = i))
})

// single core config
class FireSimRocketChipSingleCoreConfig extends Config(new FireSimRocketChipConfig)

// dual core config
class FireSimRocketChipDualCoreConfig extends Config(
  new WithNDuplicatedRocketCores(2) ++
  new FireSimRocketChipSingleCoreConfig)

// quad core config
class FireSimRocketChipQuadCoreConfig extends Config(
  new WithNDuplicatedRocketCores(4) ++
  new FireSimRocketChipSingleCoreConfig)

// hexa core config
class FireSimRocketChipHexaCoreConfig extends Config(
  new WithNDuplicatedRocketCores(6) ++
  new FireSimRocketChipSingleCoreConfig)

// octa core config
class FireSimRocketChipOctaCoreConfig extends Config(
  new WithNDuplicatedRocketCores(8) ++
  new FireSimRocketChipSingleCoreConfig)

class FireSimMemBladeConfig extends Config(
  new WithMemBladeKey ++
  new WithNTrackersPerBank(4) ++
  new WithNBanks(4) ++
  new WithNMemoryChannels(4) ++
  new WithMemBladeBridge ++
  new FireSimRocketChipConfig)

class FireSimMemBlade1024Config extends Config(
  new WithMemBladeKey(Some(1024)) ++
  new WithMemBladeBridge ++
  new FireSimRocketChipConfig)

class WithL2InnerExteriorBuffer(aDepth: Int, dDepth: Int) extends Config(
  (site, here, up) => {
    case InclusiveCacheKey => up(InclusiveCacheKey).copy(
      bufInnerExterior = InclusiveCachePortParameters(
        aDepth, 0, 0, dDepth, 0))
  })

class WithStandardL2 extends Config(
  new WithL2InnerExteriorBuffer(4, 2) ++
  new WithInclusiveCache(
    nBanks = 4,
    capacityKB = 1024,
    outerLatencyCycles = 32))

class WithLargeL2 extends Config(
  new WithL2InnerExteriorBuffer(2, 2) ++
  new WithInclusiveCache(
    nBanks = 4,
    capacityKB = 1024,
    outerLatencyCycles = 16))

class WithPrefetchMiddleMan extends Config((site, here, up) => {
  case PrefetchMiddleManKey => SequentialPrefetchConfig(
    nWays = 4,
    nBlocks = 32,
    hitThreshold = 1,
    maxTimeout = (1 << 30) - 1,
    lookAhead = 4)
})

class FireSimRemoteMemClientConfig extends Config(
  new WithRemoteMemClientKey ++
  new WithStandardL2 ++
  new FireSimRocketChipConfig)

class FireSimRemoteMemClientSingleCoreConfig extends Config(
  new WithNBigCores(1) ++ new FireSimRemoteMemClientConfig)

class FireSimRemoteMemClientDualCoreConfig extends Config(
  new WithNBigCores(2) ++ new FireSimRemoteMemClientConfig)

class FireSimRemoteMemClientQuadCoreConfig extends Config(
  new WithNBigCores(4) ++ new FireSimRemoteMemClientConfig)

class FireSimDRAMCacheConfig extends Config(
  new WithMemBenchKey ++
  new WithDRAMCacheKey(4, 8) ++
  new WithExtMemSize(15L << 30) ++
  new WithPrefetchMiddleMan ++
  new WithStandardL2 ++
  new FireSimRocketChipConfig ++
  new WithDRAMCacheBridge)

class FireSimDRAMCacheSingleCoreConfig extends Config(
  new WithNBigCores(1) ++ new FireSimDRAMCacheConfig)

class FireSimDRAMCacheDualCoreConfig extends Config(
  new WithNBigCores(2) ++ new FireSimDRAMCacheConfig)

class FireSimDRAMCacheQuadCoreConfig extends Config(
  new WithNBigCores(4) ++ new FireSimDRAMCacheConfig)

class FireSimDRAMCacheTraceGenConfig extends Config(
  new WithDRAMCacheTraceGen ++
  new WithTraceGen(
    List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new WithDRAMCacheExtentTableInit ++
  new FireSimDRAMCacheConfig)

// SHA-3 accelerator config
class FireSimRocketChipSha3L2Config extends Config(
  new WithInclusiveCache ++
  new sha3.WithSha3Accel ++
  new WithNBigCores(1) ++
  new FireSimRocketChipConfig)

// SHA-3 accelerator config with synth printfs enabled
class FireSimRocketChipSha3L2PrintfConfig extends Config(
  new WithInclusiveCache ++
  new sha3.WithSha3Printf ++ 
  new sha3.WithSha3Accel ++
  new WithNBigCores(1) ++
  new FireSimRocketChipConfig)

class WithHwachaNVMTEntries(nVMT: Int) extends Config((site, here, up) => {
  case hwacha.HwachaNVMTEntries => nVMT
})

class FireSimHwachaDRAMCacheConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new hwacha.WithNLanes(4) ++
  new hwacha.DefaultHwachaConfig ++
  new WithMemBenchKey ++
  new WithDRAMCacheKey(8, 8, 2) ++
  new WithExtMemSize(15L << 30) ++
  new WithPrefetchMiddleMan ++
  new WithLargeL2 ++
  new FireSimRocketChipConfig ++
  new WithDRAMCacheBridge)

class FireSimHwachaDRAMCacheDualCoreConfig extends Config(
  new WithNBigCores(2) ++ new FireSimHwachaDRAMCacheConfig)

class FireSimBoomConfig extends Config(
  new WithBootROM ++
  new WithPeripheryBusFrequency(BigInt(3200000000L)) ++
  new WithExtMemSize(0x400000000L) ++ // 16GB
  new WithoutTLMonitors ++
  new WithUARTKey ++
  new WithNICKey ++
  new WithBlockDevice ++
  new WithBoomL2TLBs(1024) ++
  new WithoutClockGating ++
  new boom.common.WithTrace ++
  new WithDefaultMemModel ++
  new boom.common.WithLargeBooms ++
  new boom.common.WithNBoomCores(1) ++
  new WithDefaultFireSimBridges ++
  new freechips.rocketchip.system.BaseConfig
)

// A safer implementation than the one in BOOM in that it
// duplicates whatever BOOMTileKey.head is present N times. This prevents
// accidentally (and silently) blowing away configurations that may change the
// tile in the "up" view
class WithNDuplicatedBoomCores(n: Int) extends Config((site, here, up) => {
  case BoomTilesKey => List.tabulate(n)(i => up(BoomTilesKey).head.copy(hartId = i))
  case MaxHartIdBits => log2Up(site(BoomTilesKey).size)
})

class FireSimBoomDualCoreConfig extends Config(
  new WithNDuplicatedBoomCores(2) ++
  new FireSimBoomConfig)

class FireSimBoomQuadCoreConfig extends Config(
  new WithNDuplicatedBoomCores(4) ++
  new FireSimBoomConfig)

class FireSimBoomL2Config extends Config(
  new WithStandardL2 ++ 
  new FireSimBoomConfig)

class FireSimBoomDualCoreL2Config extends Config(
  new WithNDuplicatedBoomCores(2) ++
  new FireSimBoomL2Config)

class FireSimBoomDRAMCacheConfig extends Config(
  new WithMemBenchKey ++
  new WithDRAMCacheKey(8, 8, 2) ++
  new WithExtMemSize(14L << 30) ++
  new WithPrefetchMiddleMan ++
  new WithLargeL2 ++
  new FireSimBoomConfig ++
  new WithDRAMCacheBridge)

class FireSimBoomDRAMCacheDualCoreConfig extends Config(
  new WithNDuplicatedBoomCores(2) ++
  new FireSimBoomDRAMCacheConfig)

class FireSimBoomRocketDRAMCacheConfig extends Config(
  new boom.common.WithRenumberHarts ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new FireSimBoomDRAMCacheConfig)

class FireSimBoomHwachaDRAMCacheConfig extends Config(
  new WithHwachaNVMTEntries(64) ++
  new WithMultiRoCC ++
  new WithMultiRoCCHwacha(0) ++
  new hwacha.DefaultHwachaConfig ++
  new WithMemBenchKey ++
  new WithDRAMCacheKey(6, 8, 2) ++
  new WithExtMemSize(15L << 30) ++
  new WithPrefetchMiddleMan ++
  new WithLargeL2 ++
  new FireSimBoomConfig ++
  new WithDRAMCacheBridge)

class FireSimBoomHwachaRocketDRAMCacheConfig extends Config(
  new boom.common.WithRenumberHarts ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new FireSimBoomHwachaDRAMCacheConfig)

//**********************************************************************************
//* Heterogeneous Configurations
//*********************************************************************************/

// dual core config (rocket + small boom)
class FireSimRocketBoomConfig extends Config(
  new WithBoomL2TLBs(1024) ++ // reset l2 tlb amt ("WithSmallBooms" overrides it)
  new boom.common.WithRenumberHarts ++ // fix hart numbering
  new boom.common.WithSmallBooms ++ // change single BOOM to small
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++ // add a "big" rocket core
  new FireSimBoomConfig
)

//**********************************************************************************
//* Gemmini Configurations
//*********************************************************************************/

// Gemmini systolic accelerator default config
class FireSimRocketChipGemminiL2Config extends Config(
  new WithInclusiveCache ++
  new gemmini.DefaultGemminiConfig ++
  new WithNBigCores(1) ++
  new FireSimRocketChipConfig)


//**********************************************************************************
//* Supernode Configurations
//*********************************************************************************/

class SupernodeFireSimRocketChipConfig extends Config(
  new WithNumNodes(4) ++
  new WithExtMemSize(0x200000000L) ++ // 8GB
  new FireSimRocketChipConfig)

class SupernodeFireSimRocketChipSingleCoreConfig extends Config(
  new WithNumNodes(4) ++
  new WithExtMemSize(0x200000000L) ++ // 8GB
  new FireSimRocketChipSingleCoreConfig)

class SupernodeSixNodeFireSimRocketChipSingleCoreConfig extends Config(
  new WithNumNodes(6) ++
  new WithExtMemSize(0x40000000L) ++ // 1GB
  new FireSimRocketChipSingleCoreConfig)

class SupernodeEightNodeFireSimRocketChipSingleCoreConfig extends Config(
  new WithNumNodes(8) ++
  new WithExtMemSize(0x40000000L) ++ // 1GB
  new FireSimRocketChipSingleCoreConfig)

class SupernodeFireSimRocketChipDualCoreConfig extends Config(
  new WithNumNodes(4) ++
  new WithExtMemSize(0x200000000L) ++ // 8GB
  new FireSimRocketChipDualCoreConfig)

class SupernodeFireSimRocketChipQuadCoreConfig extends Config(
  new WithNumNodes(4) ++
  new WithExtMemSize(0x200000000L) ++ // 8GB
  new FireSimRocketChipQuadCoreConfig)

class SupernodeFireSimRocketChipHexaCoreConfig extends Config(
  new WithNumNodes(4) ++
  new WithExtMemSize(0x200000000L) ++ // 8GB
  new FireSimRocketChipHexaCoreConfig)

class SupernodeFireSimRocketChipOctaCoreConfig extends Config(
  new WithNumNodes(4) ++
  new WithExtMemSize(0x200000000L) ++ // 8GB
  new FireSimRocketChipOctaCoreConfig)

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
  case MaxHartIdBits => log2Up(params.size)
})

class WithDRAMCacheTraceGen extends Config((site, here, up) => {
  case TraceGenKey => up(TraceGenKey).map { tg =>
    val cacheKey = site(DRAMCacheKey)
    tg.copy(
      addrBag = {
        val nSets = cacheKey.nSets
        val nWays = cacheKey.nWays
        val spanBytes = cacheKey.spanBytes
        val nChannels = cacheKey.nChannels
        val nBanks = cacheKey.nBanksPerChannel * nChannels
        val mcRows = cacheKey.nMetaCacheRows
        List.tabulate(nWays + 1) { i =>
          Seq.tabulate(nBanks) { j =>
            val base = BigInt((j + i * nSets) * spanBytes)
            Seq(base, base + (mcRows * nBanks * spanBytes))
          }.flatten
        }.flatten
      },
      memStart = cacheKey.baseAddr)
  }
})

class FireSimTraceGenConfig extends Config(
  new WithTraceGen(
    List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new WithTraceGenBridge ++
  new FireSimRocketChipConfig)

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
      val nBanks = site(BankedL2Key).nBanks
      val blockOffset = sbp.blockOffset
      val nBeats = min(2, sbp.blockBeats)
      val beatBytes = sbp.beatBytes
      List.tabulate(2 * nWays) { i =>
        Seq.tabulate(nBeats) { j =>
          BigInt((j * beatBytes) + ((i * nSets * nBanks) << blockOffset))
        }
      }.flatten
    },
    maxRequests = nReqs,
    memStart = site(ExtMem).get.master.base,
    numGens = params.size)
  }
  case MaxHartIdBits => log2Up(params.size)
})

class FireSimTraceGenL2Config extends Config(
  new WithL2TraceGen(
    List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new WithInclusiveCache(
    nBanks = 4,
    capacityKB = 1024,
    outerLatencyCycles = 50) ++
  new WithTraceGenBridge ++
  new FireSimRocketChipConfig)

class FireSimBoomRingL2Config extends Config(
  new WithRingSystemBus ++
  new FireSimBoomL2Config)

class DualChannel extends WithNMemoryChannels(2)
class QuadChannel extends WithNMemoryChannels(4)
