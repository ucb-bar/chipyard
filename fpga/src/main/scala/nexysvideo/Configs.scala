// See LICENSE for license details.
package chipyard.fpga.nexysvideo

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._
import sifive.fpgashells.shell.{DesignKey}

import testchipip.serdes.{SerialTLKey}

import chipyard.{BuildSystem}

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

// DOC include start: WithNexysVideoTweaks and Rocket

class WithNexysVideoTweaks extends Config(
 new freechips.rocketchip.subsystem.WithRoccExample ++
  new fftgenerator.WithFFTGenerator(numPoints=8, width=16, decPt=8) ++ // add 8-point mmio fft at the default addr (0x2400) with 16bit fixed-point numbers.
  new cordic.WithCORDIC(useAXI4=false, useBlackBox=true) ++         
  new WithNexysVideoUARTTSI ++
  new WithNexysVideoDDRTL ++
  new WithNoDesignKey ++
  new testchipip.tsi.WithUARTTSIClient ++
  new chipyard.harness.WithSerialTLTiedOff ++

  new chipyard.harness.WithHarnessBinderClockFreqMHz(10) ++
  new chipyard.config.WithMemoryBusFrequency(10.0) ++
  new chipyard.config.WithFrontBusFrequency(10.0) ++
  new chipyard.config.WithSystemBusFrequency(10.0) ++
  new chipyard.config.WithPeripheryBusFrequency(10.0) ++
  new chipyard.config.WithControlBusFrequency(10.0) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithNoDebug ++ // no jtag
  new chipyard.config.WithNoUART ++ // use UART for the UART-TSI thing instad
  new chipyard.config.WithTLBackingMemory ++ // FPGA-shells converts the AXI to TL for us
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(512) << 20) ++ // 512mb on Nexys Video
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class RocketNexysVideoConfig extends Config(
  new WithNexysVideoTweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.RocketConfig)
// DOC include end: WithNexysVideoTweaks and Rocket

// DOC include start: WithTinyNexysVideoTweaks and Rocket
class WithTinyNexysVideoTweaks extends Config(
  new WithNexysVideoUARTTSI ++
  new WithNoDesignKey ++
  new sifive.fpgashells.shell.xilinx.WithNoNexysVideoShellDDR ++ // no DDR
  new testchipip.tsi.WithUARTTSIClient ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(50) ++
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithFrontBusFrequency(50.0) ++
  new chipyard.config.WithSystemBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++
  new chipyard.config.WithControlBusFrequency(50.0) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithNoDebug ++ // no jtag
  new chipyard.config.WithNoUART ++ // use UART for the UART-TSI thing instad
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class TinyRocketNexysVideoConfig extends Config(
  new WithTinyNexysVideoTweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.TinyRocketConfig)
  // DOC include end: WithTinyNexysVideoTweaks and Rocket

class BringupNexysVideoConfig extends Config(
  new WithNexysVideoSerialTLToGPIO ++
  new WithNexysVideoTweaks(freqMHz = 75) ++
  new chipyard.ChipBringupHostConfig)

