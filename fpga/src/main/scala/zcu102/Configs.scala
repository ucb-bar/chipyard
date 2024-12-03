package chipyard.fpga.zcu102

import sys.process._

import org.chipsalliance.cde.config.{Config, Parameters}

import freechips.rocketchip.subsystem.{SystemBusKey, ExtMem}
import freechips.rocketchip.devices.debug.{JTAG}
import freechips.rocketchip.devices.tilelink.{BootROMLocated}
import freechips.rocketchip.resources.{DTSTimebase}


import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.xilinx.{ZCU102ShellPMOD, ZCU102DDRSize}

import testchipip.serdes.{SerialTLKey}

import chipyard._
import chipyard.harness._

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(SPIParams(rAddress = BigInt(0x64001000L)))
  case ZCU102ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    val freqMHz = (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toLong
    val make = s"make -C fpga/src/main/resources/zcu102/sdboot PBUS_CLK=${freqMHz} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/zcu102/sdboot/build/sdboot.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(ZCU102DDRSize))))
  case SerialTLKey => Nil
})

// DOC include start: AbstractZCU102 and Rocket
class WithZCU102Tweaks extends Config(
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
  // io binders
  new chipyard.iobinders.WithUARTTSIPunchthrough ++ // Is it correctly?
  new chipyard.iobinders.WithSPIIOPunchthrough ++  // Is it correctly?
  // other configuration
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++
  new WithSystemModifications ++
  // new chipyard.config.WithNoDebug ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1)
)

class RocketZCU102Config extends Config(
  new WithFPGAFrequency(25) ++
  new WithZCU102Tweaks ++
  new chipyard.RocketConfig)
// DOC include end: AbstractZCU102 and Rocket

class RocketZCU102ConfigWithHyp extends Config(
  new freechips.rocketchip.system.HypervisorConfig ++ // Would like to support H-ext
  new WithFPGAFrequency(25) ++
  new WithZCU102Tweaks ++
  new chipyard.RocketConfig)

class BoomZCU102Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithZCU102Tweaks ++
  new chipyard.MegaBoomV3Config)

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
