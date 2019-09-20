package firesim.firesim

import java.io.File

import chisel3.util.{log2Up}
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.groundtest.TraceGenParams
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.DebugModuleParams
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import boom.common.BoomTilesKey
import testchipip.{WithBlockDevice, BlockDeviceKey, BlockDeviceConfig, MemBenchKey, MemBenchParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.blocks.inclusivecache.InclusiveCachePortParameters
import memblade.manager.{MemBladeKey, MemBladeParams, MemBladeQueueParams}
import memblade.client.{RemoteMemClientKey, RemoteMemClientConfig}
import memblade.cache.{DRAMCacheKey, DRAMCacheConfig, RemoteAccessDepths, WritebackDepths, MemoryQueueParams}
import memblade.prefetcher.{PrefetchRoCC, SoftPrefetchConfig, AutoPrefetchConfig, StreamBufferConfig}
import scala.math.{min, max}
import tracegen.TraceGenKey
import icenet._
import scala.math.max
import testchipip.WithRingSystemBus

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

class WithNICKey extends Config((site, here, up) => {
  case NICKey => NICConfig(
    inBufFlits = 8192,
    ctrlQueueDepth = 64,
    usePauser = true)
})

class WithMemBladeKey(spanBytes: Option[Int] = None) extends Config(
  (site, here, up) => {
    case MemBladeKey => {
      val spanBytesVal = spanBytes.getOrElse(site(CacheBlockBytes))
      MemBladeParams(
        spanBytes = spanBytesVal,
        nSpanTrackers = max(384 / spanBytesVal, 2),
        nWordTrackers = 4,
        spanQueue = MemBladeQueueParams(reqHeadDepth = 32, respHeadDepth = 32),
        wordQueue = MemBladeQueueParams(reqHeadDepth = 32, respHeadDepth = 32))
    }
  }
)

class WithRemoteMemClientKey(spanBytes: Int = 1024) extends Config((site, here, up) => {
  case RemoteMemClientKey => RemoteMemClientConfig(
    spanBytes = spanBytes,
    nRMemXacts = 32768 / spanBytes)
})

class WithDRAMCacheKey extends Config((site, here, up) => {
  case DRAMCacheKey => DRAMCacheConfig(
    nSets = 1 << 21,
    nWays = 7,
    baseAddr = BigInt(1) << 37,
    nTrackersPerBank = 8,
    nBanksPerChannel = 8,
    nChannels = 1,
    nSecondaryRequests = 1,
    spanBytes = site(CacheBlockBytes),
    logAddrBits = 37,
    outIdBits = 4,
    nWritebackRemXacts = 64,
    remAccessQueue = RemoteAccessDepths(1, 8, 1, 8),
    wbQueue = WritebackDepths(1, 1),
    memInQueue = MemoryQueueParams(0, 0, 2, 2, 8, 2),
    memOutQueue = MemoryQueueParams(0, 0, 2, 2, 2, 2),
    zeroMetadata = false)
})

class WithDRAMCacheExtentTableInit(suffix: Int = 0x0300) extends Config(
  (site, here, up) => {
    case DRAMCacheKey => up(DRAMCacheKey).copy(
      extentTableInit = Seq.tabulate(16) { i => (suffix, i + 1) })
  })

class WithPrefetchRoCC extends Config((site, here, up) => {
  case BuildRoCC => Seq((q: Parameters) => {
    implicit val p = q
    implicit val valName = ValName("FireSim")
    LazyModule(new PrefetchRoCC(
      opcodes = OpcodeSet.custom2,
      soft = Some(SoftPrefetchConfig(nMemXacts = 32, nBackends = 2)),
      auto = Some(AutoPrefetchConfig(
        nWays = 4,
        nBlocks = 28,
        hitThreshold = 1,
        timeoutPeriod = 8192))))
  })
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
  case DebugModuleParams => up(DebugModuleParams, site).copy(clockGate = false)
})

// Testing configurations
// This enables printfs used in testing
class WithScalaTestFeatures extends Config((site, here, up) => {
    case PrintTracePort => true
})

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
  new WithMemBladeKey ++ new FireSimRocketChipConfig)

class FireSimMemBlade1024Config extends Config(
  new WithMemBladeKey(Some(1024)) ++ new FireSimRocketChipConfig)

class WithL2InnerExteriorBuffer(aDepth: Int, dDepth: Int) extends Config(
  (site, here, up) => {
    case InclusiveCacheKey => up(InclusiveCacheKey).copy(
      bufInnerExterior = InclusiveCachePortParameters(
        aDepth, 0, 0, dDepth, 0))
  })

class WithStandardL2 extends Config(
  new WithL2InnerExteriorBuffer(2, 2) ++
  new WithInclusiveCache(
    nBanks = 8,
    capacityKB = 1024,
    outerLatencyCycles = 50))

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

class FireSimPrefetcherConfig extends Config(
  new WithPrefetchRoCC ++
  new WithStandardL2 ++
  new FireSimRocketChipConfig)

class FireSimPrefetcherSingleCoreConfig extends Config(
  new WithNBigCores(1) ++ new FireSimPrefetcherConfig)

class FireSimPrefetcherDualCoreConfig extends Config(
  new WithNBigCores(2) ++ new FireSimPrefetcherConfig)

class FireSimPrefetcherQuadCoreConfig extends Config(
  new WithNBigCores(4) ++ new FireSimPrefetcherConfig)

class FireSimDRAMCacheConfig extends Config(
  new WithPrefetchRoCC ++
  new WithMemBenchKey ++
  new WithDRAMCacheKey ++
  new WithExtMemSize(15L << 30) ++
  new WithRingSystemBus ++
  new WithStandardL2 ++
  new FireSimRocketChipConfig)

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
  new boom.common.WithLargeBooms ++
  new boom.common.WithNBoomCores(1) ++
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

class FireSimBoomPrefetcherConfig extends Config(
  new WithPrefetchRoCC ++
  new WithStandardL2 ++
  new FireSimBoomConfig)

class FireSimBoomPrefetcherDualCoreConfig extends Config(
  new WithNDuplicatedBoomCores(2) ++
  new FireSimBoomPrefetcherConfig)

class FireSimBoomDRAMCacheConfig extends Config(
  new WithPrefetchRoCC ++
  new WithMemBenchKey ++
  new WithDRAMCacheKey ++
  new WithExtMemSize(15L << 30) ++
  new WithRingSystemBus ++
  new WithStandardL2 ++
  new FireSimBoomConfig)

class FireSimBoomDRAMCacheDualCoreConfig extends Config(
  new WithNDuplicatedBoomCores(2) ++
  new FireSimBoomDRAMCacheConfig)

//**********************************************************************************
//* Supernode Configurations
//*********************************************************************************/
class WithNumNodes(n: Int) extends Config((pname, site, here) => {
  case NumNodes => n
})

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
  new WithStandardL2 ++
  new FireSimRocketChipConfig)

class FireSimBoomRingL2Config extends Config(
  new WithRingSystemBus ++
  new FireSimBoomL2Config)
