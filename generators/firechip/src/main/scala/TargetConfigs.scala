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
import testchipip.{BlockDeviceKey, BlockDeviceConfig, SerialKey, TracePortKey, TracePortParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import scala.math.{min, max}

import icenet._
import testchipip.WithRingSystemBus

import firesim.bridges._
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
  // Optional*: Removing this will require target-software changes to properly capture UART output
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
  new testchipip.WithTSI ++
  // Optional: Removing this will require using an initramfs under linux
  new testchipip.WithBlockDevice ++
  // Required*:
  new chipyard.config.WithUART
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


class FireSimMulticlockRocketConfig extends Config(
  new WithFireSimRationalTileDomain(2, 1) ++
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.MultiClockRocketConfig)

