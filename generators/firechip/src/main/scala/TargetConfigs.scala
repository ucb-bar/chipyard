package firesim.firesim

import java.io.File

import chisel3._
import chisel3.util.{log2Up}
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.groundtest.TraceGenParams
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{DebugModuleParams, DebugModuleKey}
import freechips.rocketchip.diplomacy.LazyModule
import boom.common.BoomTilesKey
import testchipip.{BlockDeviceKey, BlockDeviceConfig, SerialKey}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import scala.math.{min, max}
import tracegen.TraceGenKey
import icenet._

import firesim.bridges._
import firesim.util.{WithNumNodes}
import firesim.configs._
import chipyard.{BuildTop}
import chipyard.config.ConfigValName._

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


class WithPerfCounters extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nPerfCounters = 29)
  ))
})


class WithBoomEnableTrace extends Config((site, here, up) => {
  case BoomTilesKey => up(BoomTilesKey) map (tile => tile.copy(trace = true))
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

class WithFireSimTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => Module(LazyModule(new FireSimDUT()(p)).suggestName("top").module)
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
  new chipyard.config.WithNoGPIO ++
  new WithBootROM ++
  new WithPeripheryBusFrequency(BigInt(3200000000L)) ++
  new WithExtMemSize(0x400000000L) ++ // 16GB
  new WithoutTLMonitors ++
  new chipyard.config.WithUART ++
  new icenet.WithIceNIC(inBufFlits = 8192, ctrlQueueDepth = 64) ++
  new testchipip.WithTSI ++
  new testchipip.WithBlockDevice ++
  new chipyard.config.WithL2TLBs(1024) ++
  new WithPerfCounters ++
  new WithoutClockGating ++
  new WithDefaultMemModel ++
  new WithDefaultFireSimBridges ++
  new WithFireSimTop ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
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

class FireSimBoomConfig extends Config(
  new chipyard.config.WithNoGPIO ++
  new WithBootROM ++
  new WithPeripheryBusFrequency(BigInt(3200000000L)) ++
  new WithExtMemSize(0x400000000L) ++ // 16GB
  new WithoutTLMonitors ++
  new WithBoomEnableTrace ++
  new chipyard.config.WithUART ++
  new icenet.WithIceNIC(inBufFlits = 8192, ctrlQueueDepth = 64) ++
  new testchipip.WithTSI ++
  new testchipip.WithBlockDevice ++
  new chipyard.config.WithL2TLBs(1024) ++
  new WithoutClockGating ++
  new WithDefaultMemModel ++
  new boom.common.WithLargeBooms ++
  new boom.common.WithNBoomCores(1) ++
  new WithDefaultFireSimBridges ++
  new WithFireSimTop ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
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

//**********************************************************************************
//* Heterogeneous Configurations
//*********************************************************************************/

// dual core config (rocket + small boom)
class FireSimRocketBoomConfig extends Config(
  new chipyard.config.WithL2TLBs(1024) ++ // reset l2 tlb amt ("WithSmallBooms" overrides it)
  new boom.common.WithRenumberHarts ++ // fix hart numbering
  new boom.common.WithSmallBooms ++ // change single BOOM to small
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++ // add a "big" rocket core
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
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
