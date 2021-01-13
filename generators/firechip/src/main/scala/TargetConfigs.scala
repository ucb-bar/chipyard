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
import freechips.rocketchip.devices.tilelink.{BootROMLocated, BootROMParams}
import freechips.rocketchip.devices.debug.{DebugModuleParams, DebugModuleKey}
import freechips.rocketchip.diplomacy.LazyModule
import testchipip.{BlockDeviceKey, BlockDeviceConfig, TracePortKey, TracePortParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import scala.math.{min, max}

import icenet._
import testchipip.WithRingSystemBus

import firesim.bridges._
import firesim.configs._

class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => {
    val chipyardBootROM = new File(s"./generators/testchipip/bootrom/bootrom.rv${site(XLen)}.img")
    val firesimBootROM = new File(s"./target-rtl/chipyard/generators/testchipip/bootrom/bootrom.rv${site(XLen)}.img")

    val bootROMPath = if (chipyardBootROM.exists()) {
      chipyardBootROM.getAbsolutePath()
    } else {
      firesimBootROM.getAbsolutePath()
    }
    up(BootROMLocated(x), site).map(_.copy(contentFileName = bootROMPath))
  }
})

class WithPeripheryBusFrequency(freq: BigInt) extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey).copy(dtsFrequency = Some(freq))
})

// Disables clock-gating; doesn't play nice with our FAME-1 pass
class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
})

// Testing configurations
// This enables printfs used in testing
class WithScalaTestFeatures extends Config((site, here, up) => {
  case TracePortKey => up(TracePortKey, site).map(_.copy(print = true))
})

// FASED Config Aliases. This to enable config generation via "_" concatenation
// which requires that all config classes be defined in the same package
class DDR3FRFCFS extends FRFCFS16GBQuadRank
class DDR3FRFCFSLLC4MB extends FRFCFS16GBQuadRankLLC4MB

class WithNIC extends icenet.WithIceNIC(inBufFlits = 8192, ctrlQueueDepth = 64)

// Adds a small/large NVDLA to the system
class WithNVDLALarge extends nvidia.blocks.dla.WithNVDLA("large")
class WithNVDLASmall extends nvidia.blocks.dla.WithNVDLA("small")


// Tweaks that are generally applied to all firesim configs
class WithFireSimConfigTweaks extends Config(
  // Required*: Uses FireSim ClockBridge and PeekPokeBridge to drive the system with a single clock/reset
  new WithFireSimSimpleClocks ++
  // Required*: When using FireSim-as-top to provide a correct path to the target bootrom source
  new WithBootROM ++
  // Optional*: Removing this will require adjusting the UART baud rate and
  // potential target-software changes to properly capture UART output
  new WithPeripheryBusFrequency(BigInt(3200000000L)) ++
  // Required: Existing FAME-1 transform cannot handle black-box clock gates
  new WithoutClockGating ++
  // Required*: Removes thousands of assertions that would be synthesized (* pending PriorityMux bugfix)
  new WithoutTLMonitors ++
  // Optional: Adds IO to attach tracerV bridges
  new chipyard.config.WithTraceIO ++
  // Optional: Request 16 GiB of target-DRAM by default (can safely request up to 32 GiB on F1)
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 16L) ++
  // Required: Adds IO to attach SerialBridge. The SerialBridges is responsible
  // for signalling simulation termination under simulation success. This fragment can
  // be removed if you supply an auxiliary bridge that signals simulation termination
  new testchipip.WithDefaultSerialTL ++
  // Optional: Removing this will require using an initramfs under linux
  new testchipip.WithBlockDevice ++
  // Required*: Scale default baud rate with periphery bus frequency
  new chipyard.config.WithUART(BigInt(3686400L)) ++
  // Required: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
  new chipyard.config.WithNoDebug
)

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

//*****************************************************************
// Rocket configs, base off chipyard's RocketConfig
//*****************************************************************
// DOC include start: firesimconfig
class FireSimRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)
// DOC include end: firesimconfig

class FireSimQuadRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.QuadRocketConfig)

// A stripped down configuration that should fit on all supported hosts.
// Flat to avoid having to reorganize the config class hierarchy to remove certain features 
class FireSimSmallSystemConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithBootROM ++
  new WithPeripheryBusFrequency(BigInt(3200000000L)) ++
  new WithoutClockGating ++
  new WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithExtMemSize(1 << 28) ++
  new testchipip.WithDefaultSerialTL ++
  new testchipip.WithBlockDevice ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays = 2, capacityKB = 64) ++
  new chipyard.RocketConfig)

//*****************************************************************
// Boom config, base off chipyard's LargeBoomConfig
//*****************************************************************
class FireSimLargeBoomConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LargeBoomConfig)

//********************************************************************
// Heterogeneous config, base off chipyard's LargeBoomAndRocketConfig
//********************************************************************
class FireSimLargeBoomAndRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LargeBoomAndRocketConfig)

//******************************************************************
// Gemmini NN accel config, base off chipyard's GemminiRocketConfig
//******************************************************************
class FireSimGemminiRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.GemminiRocketConfig)

//******************************************************************
// Configuration with Ring topology SystemBus
//******************************************************************
class FireSimRingSystemBusRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.RingSystemBusRocketConfig)

//**********************************************************************************
// Supernode Configurations, base off chipyard's RocketConfig
//**********************************************************************************
class SupernodeFireSimRocketConfig extends Config(
  new WithNumNodes(4) ++
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 8L) ++ // 8 GB
  new FireSimRocketConfig)

//**********************************************************************************
//* Ariane Configurations
//*********************************************************************************/
class FireSimArianeConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.ArianeConfig)

//**********************************************************************************
//* Multiclock Configurations
//*********************************************************************************/
class FireSimMulticlockRocketConfig extends Config(
  new chipyard.config.WithTileFrequency(6400.0) ++ //lol
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.DividedClockRocketConfig)

//**********************************************************************************
// System with 16 LargeBOOMs that can be simulated with Golden Gate optimizations
// - Requires MTModels and MCRams mixins as prefixes to the platform config
// - May require larger build instances or JVM memory footprints
//*********************************************************************************/
class FireSim16LargeBoomConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new boom.common.WithNLargeBooms(16) ++
  new chipyard.config.AbstractConfig)
