package chipyard

import chipyard.config.AbstractConfig
import chipyard.stage.phases.TargetDirKey
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.diplomacy.{AsynchronousCrossing, BigIntHexContext}
import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.Config
import radiance.memory._
import radiance.subsystem.WithRadianceSharedMem

class WithRadROMs(address: BigInt, size: Int, filename: String) extends Config((site, here, up) => {
  case RadianceROMsLocated() => Some(up(RadianceROMsLocated()).getOrElse(Seq()) ++
    Seq(RadianceROMParams(
      address = address,
      size = size,
      contentFileName = filename
    )))
})

class WithRadBootROM(address: BigInt = 0x10000, size: Int = 0x10000, hang: BigInt = 0x10100) extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x))
    .map(_.copy(
      address = address,
      size = size,
      hang = hang,
      contentFileName = s"${site(TargetDirKey)}/bootrom.radiance.rv32.img"
    ))
})

// ----------------
// Radiance Configs
// ----------------

class RadianceBaseConfig(argsBinFilename: String = "args.bin") extends Config(
  // NOTE: when changing these, remember to change +define+NUM_CORES/THREADS/WARPS in
  // radiance.mk as well!
  new radiance.subsystem.WithSimtConfig(nWarps = 8, nCoreLanes = 8, nMemLanes = 8, nSrcIds = 32) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new WithRadBootROM() ++
  // new WithRadROMs(0x7FFF0000L, 0x10000, s"sims/${argsBinFilename}") ++
  // new chipyard.harness.WithCeaseSuccess ++
  new chipyard.iobinders.WithCeasePunchThrough ++
  new radiance.subsystem.WithRadianceSimParams(true) ++
  new WithCacheBlockBytes(64) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new freechips.rocketchip.subsystem.WithEdgeDataBits(256) ++
  new AbstractConfig)

class RadianceClusterConfig extends Config(
  // important to keep gemmini tile before RadianceCores to ensure radiance tile id is 0-indexed
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 16, tileSize = 8) ++
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 16, tileSize = 8) ++
  new radiance.subsystem.WithRadianceCores(4, location = InCluster(0), useVxCache = false) ++
//  new radiance.subsystem.WithRadianceFrameBuffer(x"ff018000", 16, 0x8000, x"ff011000", "fb0") ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 64 << 10, numBanks = 4, numWords = 8, serializeUnaligned = true) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceClusterSmem16KConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 4, tileSize = 4) ++
  new radiance.subsystem.WithRadianceCores(2, location = InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 16 << 10, numBanks = 4, numWords = 8, serializeUnaligned = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceTwoClustersSmem16KConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 4, tileSize = 4) ++
  new radiance.subsystem.WithRadianceCores(2, location = InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(1), dim = 8, accSizeInKB = 4, tileSize = 4) ++
  new radiance.subsystem.WithRadianceCores(2, location = InCluster(1), useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 16 << 10, numBanks = 4, numWords = 8, serializeUnaligned = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new radiance.subsystem.WithRadianceCluster(1) ++
  new RadianceBaseConfig)

class RadianceClusterConfig0 extends Config(
  new radiance.subsystem.WithRadianceCores(2, location=InCluster(0), useVxCache = false) ++
  // new radiance.subsystem.WithCoalescer(nNewSrcIds = 8, enable = false) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 4)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceClusterConfig1 extends Config(
  new radiance.subsystem.WithRadianceCores(2, location=InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 8) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 4)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig("args.1.bin"))

class RadianceClusterConfig2 extends Config(
  new radiance.subsystem.WithRadianceCores(2, location=InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 8) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 4)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig("args.2.bin"))

class RadianceClusterSynConfig extends Config(
  new radiance.subsystem.WithRadianceSimParams(false) ++
  new RadianceClusterConfig)

class RadianceGemminiConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 8) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 1)++
  new RadianceBaseConfig)

class RadianceNoCacheConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 8) ++
  new RadianceBaseConfig)

class RadianceNoCoalConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 1)++
  new RadianceBaseConfig)

class RadianceNoCacheNoCoalConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new RadianceBaseConfig)

class RadianceLargeConfig extends Config(
  new radiance.subsystem.WithRadianceCores(4, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 1)++
  new RadianceBaseConfig)

class RadianceNoROMConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new WithRadBootROM() ++
  new testchipip.soc.WithMbusScratchpad(base=0x7FFF0000L, size=0x10000, banks=1) ++
  new AbstractConfig)

class RadianceFuzzerConfig extends Config(
  new radiance.subsystem.WithFuzzerCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 2) ++
  new radiance.subsystem.WithSimtConfig(nMemLanes = 4, nSrcIds = 2) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new chipyard.harness.WithCeaseSuccess ++
  new chipyard.iobinders.WithCeasePunchThrough ++
  new AbstractConfig)

class RadianceOldCacheConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = true) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new WithRadBootROM() ++
  new WithRadROMs(0x7FFF0000L, 0x10000, "sims/args.bin") ++
  new WithRadROMs(0x20000L, 0x8000, "sims/op_a.bin") ++
  new WithRadROMs(0x28000L, 0x8000, "sims/op_b.bin") ++
  new AbstractConfig
)

// --------------------
// Rocket-based Configs
// --------------------

class RocketDummyVortexConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithSimtConfig(nMemLanes = 4, nSrcIds = 16) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new testchipip.soc.WithMbusScratchpad(base=0x7FFF0000L, size=0x10000, banks=1) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new AbstractConfig)

class RocketGPUConfig extends Config(
  new radiance.subsystem.WithNCustomSmallRocketCores(2) ++
  new chipyard.config.AbstractConfig)
