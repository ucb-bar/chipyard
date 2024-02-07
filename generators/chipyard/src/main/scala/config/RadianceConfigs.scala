package chipyard

import chipyard.config.AbstractConfig
import chipyard.stage.phases.TargetDirKey
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.diplomacy.AsynchronousCrossing
import freechips.rocketchip.subsystem.WithExtMemSize
import freechips.rocketchip.tile.XLen
import org.chipsalliance.cde.config.Config
import radiance.memory._

class WithRadROMs(address: BigInt, size: Int, filename: String) extends Config((site, here, up) => {
  case RadianceROMsLocated() => Some(up(RadianceROMsLocated()).getOrElse(Seq()) ++
    Seq(RadianceROMParams(
      address = address,
      size = size,
      contentFileName = filename
    )))
})

class WithRadBootROM(address: BigInt = 0x10000, size: Int = 0x10000, hang: BigInt = 0x10100) extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site)
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

class RadianceBaseConfig extends Config(
  new radiance.subsystem.WithSimtLanes(nLanes = 4, nSrcIds = 4) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new WithRadBootROM() ++
  new WithRadROMs(0x7FFF0000L, 0x10000, "sims/args.bin") ++
  new WithRadROMs(0x20000L, 0x8000, "sims/op_a.bin") ++
  new WithRadROMs(0x28000L, 0x8000, "sims/op_b.bin") ++
  new chipyard.harness.WithCeaseSuccess ++
  new chipyard.iobinders.WithCeasePunchThrough ++
  new AbstractConfig)

class RadianceConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 4) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 1)++
  new RadianceBaseConfig)

class RadianceGemminiConfig extends Config(
  new gemmini.DefaultGemminiConfig ++
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 4) ++
  new radiance.subsystem.WithVortexL1Banks(nBanks = 1)++
  new RadianceBaseConfig)

class RadianceNoCacheConfig extends Config(
  new radiance.subsystem.WithRadianceCores(1, useVxCache = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
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
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 16) ++
  new radiance.subsystem.WithSimtLanes(nLanes = 4, nSrcIds = 4) ++
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
  new radiance.subsystem.WithSimtLanes(nLanes = 4, nSrcIds = 16) ++
  new chipyard.config.WithSystemBusWidth(bitWidth = 256) ++
  new WithExtMemSize(BigInt("80000000", 16)) ++
  new testchipip.soc.WithMbusScratchpad(base=0x7FFF0000L, size=0x10000, banks=1) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new AbstractConfig)

class RocketGPUConfig extends Config(
  new radiance.subsystem.WithNCustomSmallRocketCores(2) ++
  new chipyard.config.AbstractConfig)
