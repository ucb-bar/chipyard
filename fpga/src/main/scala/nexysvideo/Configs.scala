// See LICENSE for license details.
package chipyard.fpga.nexysvideo

import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.lazymodule._
import sifive.fpgashells.shell.DesignKey

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

// DOC include start: WithNexysVideoTweaks and Rocket
class WithNexysVideoTweaks(freqMHz: Double = 50) extends Config(
  new WithNexysVideoUARTTSI ++
  new WithNexysVideoDDRTL ++
  new WithNoDesignKey ++
  new testchipip.tsi.WithUARTTSIClient ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(freqMHz) ++
  new chipyard.config.WithUniformBusFrequencies(freqMHz) ++
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

class WithNexysVideoEthernetTweaks extends Config(
  new WithNexysVideoEthernet ++
  new rivet.mdio.WithEthernetMDIO ++
  new WithNexysVideoMDIO)

class WithNexysVideoVerilogEthTweaks(freqMHz: Double = 50) extends Config(
  new rivet.wrapper.WithEthernetRGMII ++
  new WithNexysVideoEthernetTweaks ++
  new WithNexysVideoTweaks(freqMHz))

class RocketNexysVideoVerilogEthConfig extends Config(
  new WithNexysVideoVerilogEthTweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new chipyard.RocketConfig)

class WithNexysVideoChiselEthTweaks(freqMHz: Double = 50) extends Config(
  new rivet.WithEthernetRGMII ++
  new WithNexysVideoEthernetTweaks ++
  new WithNexysVideoTweaks(freqMHz))

class RocketNexysVideoChiselEthConfig extends Config(
  new WithNexysVideoChiselEthTweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new chipyard.RocketConfig)

/** Nexys Video RGMII design with the Verilog UDP/IPv4/ARP offload stack. */
class RocketNexysVideoVerilogUdpEthConfig extends Config(
  new rivet.udp.WithEthernetRGMIIUdpOffload(rivet.udp.VerilogUdpStack) ++
  new WithNexysVideoVerilogEthTweaks ++
  new chipyard.config.WithBroadcastManager ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new chipyard.RocketConfig)

/** Nexys Video RGMII design with the native Chisel UDP/IPv4/ARP stack. */
class RocketNexysVideoChiselUdpEthConfig extends Config(
  new rivet.udp.WithEthernetRGMIIUdpOffload(rivet.udp.ChiselUdpStack) ++
  new WithNexysVideoChiselEthTweaks ++
  new chipyard.config.WithBroadcastManager ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new chipyard.RocketConfig)

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
  new WithNexysVideoTweaks(freqMHz = 50) ++
  new testchipip.serdes.WithSerialTLPHYParams(
    testchipip.serdes.DecoupledInternalSyncSerialPhyParams(freqMHz = 50)) ++
  new chipyard.ChipBringupHostConfig)
