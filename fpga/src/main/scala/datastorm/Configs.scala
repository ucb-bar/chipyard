// See LICENSE for license details.
package chipyard.fpga.datastorm

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._
import sifive.fpgashells.shell.{DesignKey}

import testchipip.serdes.{SerialTLKey}

import chipyard.{BuildSystem}
import testchipip.soc.WithNoScratchpads
import sifive.blocks.devices.spi.SPIProtocol.width
import chipyard.iobinders.WithGPIOPunchthrough

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

class WithDatastormTweaks(freqMHz: Double = 40) extends Config(
  new WithDatastormPMODUART ++
  new WithDatastormUARTTSI ++
  new WithDatastormDDRTL ++
  new WithDatastormJTAG ++
  new WithNoDesignKey ++
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(freqMHz) ++
  new chipyard.config.WithUniformBusFrequencies(freqMHz) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithTLBackingMemory ++ // FPGA-shells converts the AXI to TL for us
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(1) << 30) ++ // 1GB on Datastorm
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class RocketDatastormConfig extends Config(
  new WithDatastormTweaks ++
  new WithNoScratchpads ++
  new testchipip.serdes.WithNoSerialTL ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new freechips.rocketchip.rocket.WithNBigCores(1) ++ // Use bigrocket instead of huge due to space constraints
  new chipyard.config.AbstractConfig)

class NoCoresDatastormConfig extends Config(
  new WithDatastormTweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.NoCoresConfig)

class BringupDatastormConfig extends Config(
  new WithDatastormSerialTLToFMC ++
  new WithDatastormTweaks ++
  new testchipip.serdes.WithSerialTLPHYParams(testchipip.serdes.DecoupledInternalSyncSerialPhyParams(freqMHz=40)) ++
  new chipyard.ChipBringupHostConfig)
