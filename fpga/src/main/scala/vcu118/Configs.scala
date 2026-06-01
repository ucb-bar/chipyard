package chipyard.fpga.vcu118

import sys.process._

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem}
import freechips.rocketchip.devices.debug.{DebugModuleKey, ExportDebug, JTAG}
import freechips.rocketchip.devices.tilelink.{DevNullParams, BootROMLocated}
import freechips.rocketchip.diplomacy.{RegionType, AddressSet}
import freechips.rocketchip.resources.{DTSModel, DTSTimebase}
import freechips.rocketchip.util.{SystemFileName}

import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VCU118ShellPMOD, VCU118DDRSize}

import testchipip.serdes.{SerialTLKey}

import chipyard._
import chipyard.harness._

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(SPIParams(rAddress = BigInt(0x64001000L)))
  case VCU118ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val freqMHz = (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toLong
    val make = s"make -C fpga/src/main/resources/vcu118/sdboot PBUS_CLK=${freqMHz} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = SystemFileName(s"./fpga/src/main/resources/vcu118/sdboot/build/sdboot.bin"))
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VCU118DDRSize)))) // set extmem to DDR size
  case SerialTLKey => Nil // remove serialized tl port
})

// DOC include start: AbstractVCU118 and Rocket
class WithVCU118Tweaks extends Config(
  // clocking
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithUniformBusFrequencies(100) ++
  new WithFPGAFrequency(100) ++ // default 100MHz freq
  // harness binders
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  new WithJTAG ++
  // other configuration
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1)
)

class RocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.RocketConfig
)
// DOC include end: AbstractVCU118 and Rocket

class BoomVCU118Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithVCU118Tweaks ++
  new chipyard.MegaBoomV3Config
)

class WithFPGAFrequency(fMHz: Double) extends Config(
  new chipyard.harness.WithHarnessBinderClockFreqMHz(fMHz) ++
  new chipyard.config.WithSystemBusFrequency(fMHz) ++
  new chipyard.config.WithPeripheryBusFrequency(fMHz) ++
  new chipyard.config.WithControlBusFrequency(fMHz) ++
  new chipyard.config.WithFrontBusFrequency(fMHz) ++
  new chipyard.config.WithMemoryBusFrequency(fMHz)
)

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)

// ----------------------------------------------------------------------------
// Area-sweep VCU118 wrappers (ported 2026-05-16).  These wrap the various
// SoC configs (in chipyard/.../config/{RoCCAcceleratorConfigs,SaturnConfigs}.scala)
// into the VCU118 harness, so the `synth-only-report` Vivado flow can be run
// per-config and we can collect LUT/DSP/BRAM utilization side-by-side.
//
// FP-stripped Saturn variants (robotMpc / intOnly / FP32-only / FP16-only /
// noPermute) are NOT yet on the opu-fp8 saturn branch in FreshScheduler --
// they live as wrappers in chipyard-fsim but their Saturn-side parameter
// gating still needs to be ported (see freshscheduler_chipyard_port_plan.md,
// session E).  Once those land, add the corresponding *VCU118Config wrappers
// here.
// ----------------------------------------------------------------------------

// Saturn vanilla
class REFV128D128RocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128RocketConfig)

class REFV256D128RocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV256D128RocketConfig)

// Saturn + OPU (Outer Product Unit) -- opu-fp8 branch
class REFV128D128RocketOPUVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128RocketOPUConfig)

// FP-stripped Saturn variants (2026-05-16).  intOnly drops all FP; robotMpc
// is FP16-only.  See VectorParams.intOnlyParams / .robotMpcParams.
class REFV128D128RocketIntOnlyVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128RocketIntOnlyConfig)

class REFV256D128RocketIntOnlyVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV256D128RocketIntOnlyConfig)

class REFV128D128RocketRobotMpcVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128RocketRobotMpcConfig)

class REFV256D128RocketRobotMpcVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV256D128RocketRobotMpcConfig)

// FP-stripping waterfall (V128D128 only -- matches the prior waterfall plot):
//   vanilla -> NoFP64 -> RobotMpc (fp16-only) -> RobotMpcNoPermute.
class REFV128D128RocketNoFP64VCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128RocketNoFP64Config)

class REFV128D128RocketRobotMpcNoPermuteVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128RocketRobotMpcNoPermuteConfig)

// Shuttle frontend (in-order 2-issue) paired with the same V128D128 Saturn
// + the FP-stripped variants.  Used to measure frontend impact vs Rocket.
class REFV128D128ShuttleVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128ShuttleConfig)

class REFV128D128ShuttleIntOnlyVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128ShuttleIntOnlyConfig)

class REFV128D128ShuttleRobotMpcVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.REFV128D128ShuttleRobotMpcConfig)

// Gemmini variants (RoCC-side; uses chipyard's Rocket tile).
class GemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.GemminiRocketConfig)

class Q31GemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q31GemminiRocketConfig)

class Q31WsGemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q31WsGemminiRocketConfig)

class Q31Ws32x32GemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q31Ws32x32GemminiRocketConfig)

class Q31Ws32x32AccGemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q31Ws32x32AccGemminiRocketConfig)

// IntOnly-Rocket variants of the Q31Ws Gemmini configs (no scalar FPU).
// Pairs with the Saturn-IntOnly area-minimization sweep.
class Q31WsGemminiRocketNoFPUVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q31WsGemminiRocketNoFPUConfig)

class Q31Ws32x32AccGemminiRocketNoFPUVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q31Ws32x32AccGemminiRocketNoFPUConfig)

// 32x32 BOTH-dataflow Gemminis (baselines against Q31Ws32x32Acc).
class Default32x32GemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Default32x32GemminiRocketConfig)

class Q3132x32GemminiRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.Q3132x32GemminiRocketConfig)
