package firesim.firesim

import java.io.File

import chisel3._
import chisel3.util.{log2Up}
import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.groundtest.TraceGenParams
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.DCacheParams
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.{BootROMLocated, BootROMParams}
import freechips.rocketchip.devices.debug.{DebugModuleParams, DebugModuleKey}
import freechips.rocketchip.diplomacy.{LazyModule, AsynchronousCrossing}
import testchipip.{BlockDeviceKey, BlockDeviceConfig, TracePortKey, TracePortParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import scala.math.{min, max}

import chipyard.clocking.{ChipyardPRCIControlKey}
import chipyard.harness.{HarnessClockInstantiatorKey}
import icenet._

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

// Disables clock-gating; doesn't play nice with our FAME-1 pass
class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey, site).copy(enableTileClockGating = false)
})

// Use the firesim clock bridge instantiator. this is required
class WithFireSimHarnessClockBridgeInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new FireSimClockBridgeInstantiator
})

// Testing configurations
// This enables printfs used in testing
class WithScalaTestFeatures extends Config((site, here, up) => {
  case TracePortKey => up(TracePortKey, site).map(_.copy(print = true))
})

// Multi-cycle regfile for rocket+boom
class WithFireSimMultiCycleRegfile extends Config((site, here, up) => {
  case FireSimMultiCycleRegFile => true
})

// Model multithreading optimization
class WithFireSimFAME5 extends Config((site, here, up) => {
  case FireSimFAME5 => true
})

// FASED Config Aliases. This to enable config generation via "_" concatenation
// which requires that all config classes be defined in the same package
class DDR3FCFS extends FCFS16GBQuadRank
class DDR3FRFCFS extends FRFCFS16GBQuadRank
class DDR3FRFCFSLLC4MB extends FRFCFS16GBQuadRankLLC4MB

class WithNIC extends icenet.WithIceNIC(inBufFlits = 8192, ctrlQueueDepth = 64)

// Adds a small/large NVDLA to the system
class WithNVDLALarge extends nvidia.blocks.dla.WithNVDLA("large")
class WithNVDLASmall extends nvidia.blocks.dla.WithNVDLA("small")

// Minimal set of FireSim-related design tweaks - notably discludes FASED, TraceIO, and the BlockDevice
class WithMinimalFireSimDesignTweaks extends Config(
  // Required*: Punch all clocks to FireSim's harness clock instantiator
  new WithFireSimHarnessClockBridgeInstantiator ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(1000.0) ++
  new chipyard.harness.WithClockFromHarness ++
  new chipyard.harness.WithResetFromHarness ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  // Required*: When using FireSim-as-top to provide a correct path to the target bootrom source
  new WithBootROM ++
  // Required: Existing FAME-1 transform cannot handle black-box clock gates
  new WithoutClockGating ++
  // Required*: Removes thousands of assertions that would be synthesized (* pending PriorityMux bugfix)
  new WithoutTLMonitors ++
  // Required: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
  new chipyard.config.WithNoDebug
)

// Non-frequency tweaks that are generally applied to all firesim configs
class WithFireSimDesignTweaks extends Config(
  new WithMinimalFireSimDesignTweaks ++
  // Required: Bake in the default FASED memory model
  new WithDefaultMemModel ++
  // Optional: reduce the width of the Serial TL interface
  new testchipip.WithSerialTLWidth(4) ++
  // Required*: Scale default baud rate with periphery bus frequency
  new chipyard.config.WithUARTInitBaudRate(BigInt(3686400L)) ++
  // Optional: Adds IO to attach tracerV bridges
  new chipyard.config.WithTraceIO ++
  // Optional: Request 16 GiB of target-DRAM by default (can safely request up to 32 GiB on F1)
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 16L) ++
  // Optional: Removing this will require using an initramfs under linux
  new testchipip.WithBlockDevice
)

// Tweaks to modify target clock frequencies / crossings to legacy firesim defaults
class WithFireSimHighPerfClocking extends Config(
  // Create clock group for uncore that does not include mbus
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("sbus", "pbus", "fbus", "cbus", "implicit"), Nil)) ++
  // Optional: This sets the default frequency for all buses in the system to 3.2 GHz
  // (since unspecified bus frequencies will use the pbus frequency)
  // This frequency selection matches FireSim's legacy selection and is required
  // to support 200Gb NIC performance. You may select a smaller value.
  new chipyard.config.WithPeripheryBusFrequency(3200.0) ++
  new chipyard.config.WithSystemBusFrequency(3200.0) ++
  new chipyard.config.WithFrontBusFrequency(3200.0) ++
  // Optional: These three configs put the DRAM memory system in it's own clock domain.
  // Removing the first config will result in the FASED timing model running
  // at the pbus freq (above, 3.2 GHz), which is outside the range of valid DDR3 speedgrades.
  // 1 GHz matches the FASED default, using some other frequency will require
  // runnings the FASED runtime configuration generator to generate faithful DDR3 timing values.
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new chipyard.config.WithAsynchrousMemoryBusCrossing ++
  new testchipip.WithAsynchronousSerialSlaveCrossing
)

// Tweaks that are generally applied to all firesim configs setting a single clock domain at 1000 MHz
class WithFireSimConfigTweaks extends Config(
  // 1 GHz matches the FASED default (DRAM modeli realistically configured for that frequency)
  // Using some other frequency will require runnings the FASED runtime configuration generator
  // to generate faithful DDR3 timing values.
  new chipyard.config.WithSystemBusFrequency(1000.0) ++
  new chipyard.config.WithPeripheryBusFrequency(1000.0) ++
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new WithFireSimDesignTweaks
)

// Tweaks to use minimal design tweaks
// Need to use initramfs to use linux (no block device)
class WithMinimalFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new testchipip.WithMbusScratchpad ++
  new WithMinimalFireSimDesignTweaks
)

/**
  * Adds BlockDevice to WithMinimalFireSimHighPerfConfigTweaks
  */
class WithMinimalAndBlockDeviceFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++ // removes mem port for FASEDBridge to match against
  new testchipip.WithMbusScratchpad ++ // adds backing scratchpad for memory to replace FASED model
  new testchipip.WithBlockDevice(true) ++ // add in block device
  new WithMinimalFireSimDesignTweaks
)

/**
  *  Adds Block device to WithMinimalFireSimHighPerfConfigTweaks
  */
class WithMinimalAndFASEDFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new WithDefaultMemModel ++ // add default FASED memory model
  new WithMinimalFireSimDesignTweaks
)

// Tweaks for legacy FireSim configs.
class WithFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new WithFireSimDesignTweaks
)

// Tweak more representative of testchip configs
class WithFireSimTestChipConfigTweaks extends Config(
  // Frequency specifications
  new chipyard.config.WithTileFrequency(1000.0) ++       // Realistic tile frequency for a test chip
  new chipyard.config.WithSystemBusFrequency(500.0) ++   // Realistic system bus frequency
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++  // Needs to be 1000 MHz to model DDR performance accurately
  new chipyard.config.WithPeripheryBusFrequency(500.0) ++  // Match the sbus and pbus frequency
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("sbus", "pbus", "fbus", "cbus", "implicit"), Seq("tile"))) ++
  //  Crossing specifications
  new chipyard.config.WithCbusToPbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossing between PBUS and CBUS
  new chipyard.config.WithSbusToMbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossings between backside of L2 and MBUS
  new freechips.rocketchip.subsystem.WithRationalRocketTiles ++   // Add rational crossings between RocketTile and uncore
  new boom.common.WithRationalBoomTiles ++ // Add rational crossings between BoomTile and uncore
  new testchipip.WithAsynchronousSerialSlaveCrossing ++ // Add Async crossing between serial and MBUS. Its master-side is tied to the FBUS
  new WithFireSimDesignTweaks
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

class FireSimRocket1GiBDRAMConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 1L) ++
  new FireSimRocketConfig)

class FireSimRocketMMIOOnly1GiBDRAMConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 1L) ++
  new FireSimRocketMMIOOnlyConfig)

class FireSimRocket4GiBDRAMConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++
  new FireSimRocketConfig)

class FireSimRocketMMIOOnly4GiBDRAMConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++
  new FireSimRocketMMIOOnlyConfig)

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
  new chipyard.config.WithPeripheryBusFrequency(3200.0) ++
  new WithoutClockGating ++
  new WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithExtMemSize(1 << 28) ++
  new testchipip.WithDefaultSerialTL ++
  new testchipip.WithBlockDevice ++
  new chipyard.config.WithUARTInitBaudRate(BigInt(3686400L)) ++
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

class FireSimLeanGemminiRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiRocketConfig)

class FireSimLeanGemminiPrintfRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiPrintfRocketConfig)

//**********************************************************************************
// Supernode Configurations, base off chipyard's RocketConfig
//**********************************************************************************
class SupernodeFireSimRocketConfig extends Config(
  new WithFireSimHarnessClockBridgeInstantiator ++
  new WithDefaultMemModel ++ // this is a global for all the multi-chip configs
  new chipyard.harness.WithHomogeneousMultiChip(n=4, new Config(
    new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 8L) ++ // 8GB DRAM per node
    new FireSimRocketConfig)))

//**********************************************************************************
//* CVA6 Configurations
//*********************************************************************************/
class FireSimCVA6Config extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.CVA6Config)

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

class FireSimNoMemPortConfig extends Config(
  new WithDefaultFireSimBridges ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new testchipip.WithMbusScratchpad ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)

class FireSimRocketMMIOOnlyConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)

class FireSimLeanGemminiRocketMMIOOnlyConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiRocketConfig)
