package chipyard.fpga.vc709

import sys.process._

import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem, WithJtagDTM}
import freechips.rocketchip.devices.debug.{DebugModuleKey, ExportDebug, JTAG, JtagDTMKey, JtagDTMConfig}
import freechips.rocketchip.devices.tilelink.{DevNullParams, BuiltInErrorDeviceParams, BootROMLocated}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet}
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.i2c.{PeripheryI2CKey, I2CParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.blocks.devices.gpio.{PeripheryGPIOKey, GPIOParams}

import sifive.fpgashells.shell.{DesignKey}

import testchipip.{SerialTLKey}

import chipyard.{BuildTop, BuildSystem, ExtTLMem}
import chipyard.fpga.vcu118.{WithUARTIOPassthrough, WithTLIOPassthrough, WithFPGAFrequency}
import chipyard.fpga.vcu118.bringup.{WithI2CIOPassthrough, WithGPIOIOPassthrough}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripheryGPIOKey => List(GPIOParams(address = BigInt(0x64002000L), width = 21))
  case PeripheryI2CKey => List(I2CParams(address = BigInt(0x64005000L)))
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
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VC709DDR3Size)))) // set extmem to DDR size
  case SerialTLKey => None // remove serialized tl port
})

// DOC include start: AbstractVC709 and Rocket
class WithVC709Tweaks extends Config(
  new WithUART ++
  new WithI2C ++
  new WithGPIO ++
  new WithDDRMem ++
  new WithUARTIOPassthrough ++
  new WithI2CIOPassthrough ++
  new WithGPIOIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use uart bootrom, setup ext. mem. size
  new chipyard.config.WithNoDebug ++ // remove debug module
  new chipyard.example.WithGCD(useAXI4=false, useBlackBox=false) ++          // Use GCD Chisel, connect Tilelink
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1))

class WithVC709System extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new VC709DigitalTop()(p)
})

class OctoRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(8) ++        // Octo-core (8 RocketTiles)
  new chipyard.config.AbstractConfig)

class RocketVC709Config extends Config(
  new WithVC709System ++
  new WithVC709Tweaks ++
  new OctoRocketConfig)
// DOC include end: AbstractVC709 and Rocket

class QuadSmallBoomConfig extends Config(
  new boom.common.WithNSmallBooms(4) ++                          // 4 boom cores
  new chipyard.config.AbstractConfig)

class BoomVC709Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithVC709System ++
  new WithVC709Tweaks ++
  new QuadSmallBoomConfig)
