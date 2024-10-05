package chipyard.fpga.zcu106

import sys.process._

import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem}
import freechips.rocketchip.devices.debug.{DebugModuleKey, ExportDebug, JTAG}
import freechips.rocketchip.devices.tilelink.{DevNullParams, BootROMLocated}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet}
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{ZCU106ShellPMOD, ZCU106DDRSize}  //TODO:

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem, ExtTLMem, DefaultClockFrequencyKey}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L))) //TODO: Find these addresses
  case PeripherySPIKey => List(SPIParams(rAddress = BigInt(0x64001000L)))  //TODO: Find these addresses
  case ZCU106ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val freqMHz = (site(DefaultClockFrequencyKey) * 1e6).toLong
   val make = s"make -C fpga/src/main/resources/zcu106/sdboot PBUS_CLK=${freqMHz} bin"
   require (make.! == 0, "Failed to build bootrom")
   p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/zcu106/sdboot/build/sdboot.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(ZCU106DDRSize)))) // set extmem to DDR size
  case SerialTLKey => None // remove serialized tl port
})

// DOC include start: AbstractZCU106 and Rocket
class WithZCU106Tweaks extends Config(
  // harness binders
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  // io binders
  new WithUARTIOPassthrough ++
  new WithSPIIOPassthrough ++
  // other configuration
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  new chipyard.config.WithNoDebug ++ // remove debug module
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new WithFPGAFrequency(100) // default 100MHz freq
)

class RocketZCU106Config extends Config(
  new WithFPGAFrequency(25) ++
  new WithZCU106Tweaks ++
  new chipyard.RocketConfig)
// DOC include end: AbstractZCU106 and Rocket

class BoomZCU106Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithZCU106Tweaks ++
  new chipyard.SmallBoomConfig) //Changed to Small from Mega

class WithFPGAFrequency(fMHz: Double) extends Config(
  new chipyard.config.WithPeripheryBusFrequency(fMHz) ++ // assumes using PBUS as default freq.
  new chipyard.config.WithMemoryBusFrequency(fMHz)
)

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)
