package chipyard

import chipyard.config.AbstractConfig
import chipyard.stage.phases.TargetDirKey
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.resources.BigIntHexContext
import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.Config
import radiance.subsystem.RadianceGemminiDataType

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

// aliases for virgo
class VirgoConfig extends RadianceClusterConfig
class VirgoFP16Config extends RadianceFP16ClusterConfig
class Virgo4CFP16Config extends Radiance4CFP16ClusterConfig
class VirgoSynConfig extends RadianceClusterSynConfig
class VirgoFP16SynConfig extends RadianceFP16ClusterSynConfig

class RadianceBaseConfig extends Config(
  // NOTE: when changing these, remember to change NUM_CORES/THREADS/WARPS in
  // the verilog source as well!
  new radiance.subsystem.WithSimtConfig(nWarps = 8, nCoreLanes = 8, nMemLanes = 8, nSrcIds = 32) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new WithRadBootROM() ++
  new chipyard.iobinders.WithCeasePunchThrough ++
  new radiance.subsystem.WithRadianceSimParams(true) ++
  new WithCacheBlockBytes(64) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new freechips.rocketchip.subsystem.WithEdgeDataBits(256) ++
  new AbstractConfig)

class RadianceFP16ClusterConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 16, accSizeInKB = 64, tileSize = (8, 4, 8), dataType = RadianceGemminiDataType.FP16) ++
  new radiance.subsystem.WithRadianceCores(8, location = InCluster(0), tensorCoreFP16 = true, useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 128 << 10, numBanks = 4, numWords = 16) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class Radiance4CFP16ClusterConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 16, accSizeInKB = 64, tileSize = (8, 4, 8), dataType = RadianceGemminiDataType.FP16) ++
  new radiance.subsystem.WithRadianceCores(4, location = InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 128 << 10, numBanks = 4, numWords = 8) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceClusterConfig extends Config(
  // important to keep gemmini tile before RadianceCores to ensure radiance tile id is 0-indexed
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 16, tileSize = 8) ++
  // new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 16, tileSize = 8) ++
  new radiance.subsystem.WithRadianceCores(4, location = InCluster(0), tensorCoreFP16 = false, useVxCache = false) ++
  // new radiance.subsystem.WithRadianceFrameBuffer(x"ff018000", 16, 0x8000, x"ff011000", "fb0") ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 256 << 10/*KBytes*/, numBanks = 8, numWords = 8) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceClusterSmem16KConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 4, tileSize = 4) ++
  new radiance.subsystem.WithRadianceCores(4, location = InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 16 << 10/*KBytes*/, numBanks = 4, numWords = 8) ++ // serializeUnaligned: false
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceTwoClustersSmem16KConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 4, tileSize = 4) ++
  new radiance.subsystem.WithRadianceCores(2, location = InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(1), dim = 8, accSizeInKB = 4, tileSize = 4) ++
  new radiance.subsystem.WithRadianceCores(2, location = InCluster(1), useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 16 << 10, numBanks = 4, numWords = 8) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new radiance.subsystem.WithRadianceCluster(1) ++
  new RadianceBaseConfig)

class RadianceBigLittleClusterConfig extends Config(
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 4, accSizeInKB = 16, tileSize = 16) ++
  new radiance.subsystem.WithRadianceGemmini(location = InCluster(0), dim = 8, accSizeInKB = 16, tileSize = 8) ++
  new radiance.subsystem.WithRadianceCores(2, location = InCluster(0), useVxCache = false) ++
  new radiance.subsystem.WithRadianceSharedMem(address = x"ff000000", size = 64 << 10, numBanks = 4, numWords = 8) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 8)++
  new radiance.subsystem.WithRadianceCluster(0) ++
  new RadianceBaseConfig)

class RadianceClusterSynConfig extends Config(
  new radiance.subsystem.WithRadianceSimParams(false) ++
  new RadianceClusterConfig)

class RadianceFP16ClusterSynConfig extends Config(
  new radiance.subsystem.WithRadianceSimParams(false) ++
  new RadianceFP16ClusterConfig)

class RadianceBigLittleClusterSynConfig extends Config(
  new radiance.subsystem.WithRadianceSimParams(false) ++
    new RadianceBigLittleClusterConfig)

class RadianceNoCacheConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 8) ++
  new RadianceBaseConfig)

class RadianceNoCoalConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 1)++
  new RadianceBaseConfig)

class RadianceFuzzerConfig extends Config(
  new radiance.subsystem.WithFuzzerCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 2) ++
  new radiance.subsystem.WithSimtConfig(nMemLanes = 4, nSrcIds = 2) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new chipyard.harness.WithCeaseSuccess ++
  new chipyard.iobinders.WithCeasePunchThrough ++
  new AbstractConfig)
