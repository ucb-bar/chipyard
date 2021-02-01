package chipyard.fpga.vc709

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
import sifive.fpgashells.shell.xilinx.{VC709ShellPMOD, VC709DDRSize}

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem, ExtTLMem}
import chipyard.fpga.vcu118.{WithUARTIOPassthrough, WithSPIIOPassthrough, WithTLIOPassthrough}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
})

class WithSystemModifications extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(site(FPGAFrequencyKey).toInt*1000000))
  case DTSTimebase => BigInt(1000000)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for uart boot
    val freqMHz = site(FPGAFrequencyKey).toInt * 1000000
    val make = s"make -C fpga/src/main/resources/vc709/uartboot PBUS_CLK=${freqMHz} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/vc709/uartboot/build/bootrom.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VC709DDRSize)))) // set extmem to DDR size
  case SerialTLKey => None // remove serialized tl port
})

// DOC include start: AbstractVC709 and Rocket
class WithVC709Tweaks extends Config(
  new WithUART ++
  new WithDDRMem ++
  new WithUARTIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use uart bootrom, setup ext. mem. size
  new chipyard.config.WithNoDebug ++ // remove debug module
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1))

class RocketVC709Config extends Config(
  new WithVC709Tweaks ++
  new chipyard.RocketConfig)
// DOC include end: AbstractVC709 and Rocket

class BoomVC709Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithVC709Tweaks ++
  new chipyard.MegaBoomConfig)

class WithFPGAFrequency(MHz: Double) extends Config((site, here, up) => {
  case FPGAFrequencyKey => MHz
})

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)
class WithFPGAFreq25MHz extends WithDevKitFrequency(125)
class WithFPGAFreq150MHz extends WithDevKitFrequency(150)
class WithFPGAFreq200MHz extends WithDevKitFrequency(200)
