// See LICENSE for license details.

package firechip.chip

import java.io.File

import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.{BootROMLocated}
import freechips.rocketchip.devices.debug.{DebugModuleKey}
import freechips.rocketchip.prci.{AsynchronousCrossing}
import testchipip.cosim.{TracePortKey}
import icenet._

import chipyard.clocking.{ChipyardPRCIControlKey}
import chipyard.harness.{HarnessClockInstantiatorKey}

// Disables clock-gating; doesn't play nice with our FAME-1 pass
class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map(_.copy(clockGate = false))
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey).copy(enableTileClockGating = false)
})

// Use the firesim clock bridge instantiator. this is required
class WithFireSimHarnessClockBridgeInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new FireSimClockBridgeInstantiator
})

// Testing configurations
// This enables printfs used in testing
class WithScalaTestFeatures extends Config((site, here, up) => {
  case TracePortKey => up(TracePortKey).map(_.copy(print = true))
})

// Multi-cycle regfile for rocket+boom
class WithFireSimMultiCycleRegfile extends Config((site, here, up) => {
  case FireSimMultiCycleRegFile => true
})

// Model multithreading optimization
class WithFireSimFAME5 extends Config((site, here, up) => {
  case FireSimFAME5 => true
})

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
  new chipyard.config.WithNoClockTap ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  // Required: Existing FAME-1 transform cannot handle black-box clock gates
  new WithoutClockGating ++
  // Optional: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
  new chipyard.config.WithNoDebug ++
  // Required*: Removes thousands of assertions that would be synthesized (* pending PriorityMux bugfix)
  new WithoutTLMonitors
)

// Non-frequency tweaks that are generally applied to all firesim configs
class WithFireSimDesignTweaks extends Config(
  new WithMinimalFireSimDesignTweaks ++
  // Required: Remove the debug clock tap, this breaks compilation of target-level sim in FireSim
  new chipyard.config.WithNoClockTap ++
  // Optional: reduce the width of the Serial TL interface
  new testchipip.serdes.WithSerialTLWidth(4) ++
  // Required*: Scale default baud rate with periphery bus frequency
  new chipyard.config.WithUART(
    baudrate=BigInt(3686400L),
    txEntries=256, rxEntries=256) ++        // FireSim requires a larger UART FIFO buffer,
  new chipyard.config.WithNoUART() ++       // so we overwrite the default one
  // Optional: Adds IO to attach tracerV bridges
  new chipyard.config.WithTraceIO ++
  // Optional: Request 16 GiB of target-DRAM by default (can safely request up to 64 GiB on F1)
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 16L) ++
  // Optional: Removing this will require using an initramfs under linux
  new testchipip.iceblk.WithBlockDevice
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
  new chipyard.config.WithControlBusFrequency(3200.0) ++
  new chipyard.config.WithSystemBusFrequency(3200.0) ++
  new chipyard.config.WithFrontBusFrequency(3200.0) ++
  new chipyard.config.WithControlBusFrequency(3200.0) ++
  // Optional: These three configs put the DRAM memory system in it's own clock domain.
  // Removing the first config will result in the FASED timing model running
  // at the pbus freq (above, 3.2 GHz), which is outside the range of valid DDR3 speedgrades.
  // 1 GHz matches the FASED default, using some other frequency will require
  // runnings the FASED runtime configuration generator to generate faithful DDR3 timing values.
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new chipyard.config.WithAsynchrousMemoryBusCrossing
)

// Tweaks that are generally applied to all firesim configs setting a single clock domain at 1000 MHz
class WithFireSimConfigTweaks extends Config(
  // 1 GHz matches the FASED default (DRAM modeli realistically configured for that frequency)
  // Using some other frequency will require runnings the FASED runtime configuration generator
  // to generate faithful DDR3 timing values.
  new chipyard.config.WithSystemBusFrequency(1000.0) ++
  new chipyard.config.WithControlBusFrequency(1000.0) ++
  new chipyard.config.WithPeripheryBusFrequency(1000.0) ++
  new chipyard.config.WithControlBusFrequency(1000.0) ++
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++
  new chipyard.config.WithFrontBusFrequency(1000.0) ++
  new WithFireSimDesignTweaks
)

// Tweaks to use minimal design tweaks
// Need to use initramfs to use linux (no block device)
class WithMinimalFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new testchipip.soc.WithMbusScratchpad ++
  new WithMinimalFireSimDesignTweaks
)

/**
  * Adds BlockDevice to WithMinimalFireSimHighPerfConfigTweaks
  */
class WithMinimalAndBlockDeviceFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++ // removes mem port for FASEDBridge to match against
  new testchipip.soc.WithMbusScratchpad ++ // adds backing scratchpad for memory to replace FASED model
  new testchipip.iceblk.WithBlockDevice(true) ++ // add in block device
  new WithMinimalFireSimDesignTweaks
)

/**
  *  Adds Block device to WithMinimalFireSimHighPerfConfigTweaks
  */
class WithMinimalAndFASEDFireSimHighPerfConfigTweaks extends Config(
  new WithFireSimHighPerfClocking ++
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
  new chipyard.config.WithFrontBusFrequency(500.0) ++      // Match the sbus and fbus frequency
  new chipyard.config.WithControlBusFrequency(500.0) ++    // Match the sbus and cbus frequency
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("sbus", "pbus", "fbus", "cbus", "implicit"), Seq("tile"))) ++
  //  Crossing specifications
  new chipyard.config.WithCbusToPbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossing between PBUS and CBUS
  new chipyard.config.WithSbusToMbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossings between backside of L2 and MBUS
  new freechips.rocketchip.rocket.WithRationalCDCs ++   // Add rational crossings between RocketTile and uncore
  new boom.v3.common.WithRationalBoomTiles ++ // Add rational crossings between BoomTile and uncore
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
  new WithFireSimConfigTweaks ++
  new chipyard.QuadRocketConfig)

// A stripped down configuration that should fit on all supported hosts.
// Flat to avoid having to reorganize the config class hierarchy to remove certain features
class FireSimSmallSystemConfig extends Config(
  new WithDefaultFireSimBridges ++
  new chipyard.config.WithPeripheryBusFrequency(3200.0) ++
  new chipyard.config.WithControlBusFrequency(3200.0) ++
  new chipyard.config.WithSystemBusFrequency(3200.0) ++
  new chipyard.config.WithFrontBusFrequency(3200.0) ++
  new chipyard.config.WithMemoryBusFrequency(3200.0) ++
  new WithoutClockGating ++
  new WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithExtMemSize(1 << 28) ++
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(
    client = Some(testchipip.serdes.SerialTLClientParams(totalIdBits = 4)),
    phyParams = testchipip.serdes.DecoupledExternalSyncSerialPhyParams(phitWidth=32, flitWidth=32)
  ))) ++
  new testchipip.iceblk.WithBlockDevice ++
  new chipyard.config.WithUARTInitBaudRate(BigInt(3686400L)) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays = 2, capacityKB = 64) ++
  new chipyard.RocketConfig)

class FireSimDmiRocketConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++ // (must be at top) tieoff any bridges that connect to serialTL so only DMI port is connected
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.dmiRocketConfig)

class FireSimDmiCheckpointingRocketConfig  extends Config(
  new chipyard.config.WithNoUART ++           // (must be at top) only use htif prints w/ checkpointing
  new chipyard.harness.WithSerialTLTiedOff ++ // (must be at top) tieoff any bridges that connect to serialTL so only DMI port is connected
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.dmiCheckpointingRocketConfig)

//*****************************************************************
// Boom config, base off chipyard's LargeBoomV3Config
//*****************************************************************
class FireSimLargeBoomConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.LargeBoomV3Config)

//********************************************************************
// Heterogeneous config, base off chipyard's LargeBoomAndRocketConfig
//********************************************************************
class FireSimLargeBoomAndRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.LargeBoomAndRocketConfig)

//******************************************************************
// Gemmini NN accel config, base off chipyard's GemminiRocketConfig
//******************************************************************
class FireSimGemminiRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.GemminiRocketConfig)

class FireSimLeanGemminiRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiRocketConfig)

class FireSimLeanGemminiPrintfRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiPrintfRocketConfig)

//**********************************************************************************
// Supernode Configurations, base off chipyard's RocketConfig
//**********************************************************************************
class SupernodeFireSimRocketConfig extends Config(
  new WithFireSimHarnessClockBridgeInstantiator ++
  new chipyard.harness.WithHomogeneousMultiChip(n=4, new Config(
    new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 8L) ++ // 8GB DRAM per node
    new FireSimRocketConfig)))

//**********************************************************************************
//* CVA6 Configurations
//*********************************************************************************/
class FireSimCVA6Config extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.CVA6Config)

//**********************************************************************************
// System with 16 LargeBOOMs that can be simulated with Golden Gate optimizations
// - Requires MTModels and MCRams mixins as prefixes to the platform config
// - May require larger build instances or JVM memory footprints
//*********************************************************************************/
class FireSim16LargeBoomV3Config extends Config(
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new boom.v3.common.WithNLargeBooms(16) ++
  new chipyard.config.AbstractConfig)

class FireSimNoMemPortConfig extends Config(
  new WithDefaultFireSimBridges ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new testchipip.soc.WithMbusScratchpad ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)

class FireSimRocketMMIOOnlyConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.RocketConfig)

class FireSimLeanGemminiRocketMMIOOnlyConfig extends Config(
  new WithDefaultMMIOOnlyFireSimBridges ++
  new WithFireSimConfigTweaks ++
  new chipyard.LeanGemminiRocketConfig)

class FireSimRadianceClusterSynConfig extends Config(
  new chipyard.harness.WithHarnessBinderClockFreqMHz(500.0) ++
  new chipyard.config.WithNoTraceIO ++
  new WithDefaultFireSimBridges ++
  new chipyard.config.WithRadBootROM ++
  new WithFireSimConfigTweaks ++
  new chipyard.RadianceClusterSynConfig)

class FireSimLargeBoomCospikeConfig extends Config(
  new WithCospikeBridge ++
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks++
  new chipyard.LargeBoomV3Config)

class FireSimQuadRocketSbusRingNoCConfig extends Config(
  new chipyard.config.WithNoTraceIO ++
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks++
  new chipyard.QuadRocketSbusRingNoCConfig)

class FireSimLargeBoomSV39CospikeConfig extends Config(
  new WithCospikeBridge ++
  new WithDefaultFireSimBridges ++
  new WithFireSimConfigTweaks++
  new freechips.rocketchip.rocket.WithSV39 ++
  new chipyard.LargeBoomV3Config)
