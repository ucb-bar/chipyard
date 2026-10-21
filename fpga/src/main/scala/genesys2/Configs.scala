// See LICENSE for license details.
package chipyard.fpga.genesys2

import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.lazymodule._
import sifive.fpgashells.shell.DesignKey

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

// DOC include start: WithGenesys2Tweaks and Rocket
class WithGenesys2Tweaks(freqMHz: Double = 50) extends Config(
  new WithGenesys2UARTTSI ++
  new WithGenesys2DDRTL ++
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
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(1024) << 20) ++ // 1gb on Genesys2
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class RocketGenesys2Config extends Config(
  new WithGenesys2Tweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.RocketConfig)
// DOC include end: WithGenesys2Tweaks and Rocket

// DOC include start: WithTinyGenesys2Tweaks and Rocket
class WithTinyGenesys2Tweaks extends Config(
  new WithGenesys2UARTTSI ++
  new WithNoDesignKey ++
  new sifive.fpgashells.shell.xilinx.WithNoGenesys2ShellDDR ++ // no DDR
  new testchipip.tsi.WithUARTTSIClient ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(100) ++
  new chipyard.config.WithUniformBusFrequencies(100) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithNoDebug ++ // no jtag
  new chipyard.config.WithNoUART ++ // use UART for the UART-TSI thing instad
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class TinyRocketGenesys2Config extends Config(
  new WithTinyGenesys2Tweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.TinyRocketConfig)
  // DOC include end: WithTinyGenesys2Tweaks and Rocket

class BringupGenesys2Config extends Config(
  new WithGenesys2SerialTLToGPIO ++
  new WithGenesys2Tweaks(freqMHz = 50) ++
  new testchipip.serdes.WithSerialTLPHYParams(
    testchipip.serdes.DecoupledInternalSyncSerialPhyParams(freqMHz = 50)) ++
  new chipyard.ChipBringupHostConfig)
