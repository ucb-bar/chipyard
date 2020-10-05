// See LICENSE for license details.
package chipyard.fpga.arty.e300

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pwm._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._

import chipyard.{BuildSystem}

class E300DevKitExtra extends Config((site, here, up) => {
  case PeripheryGPIOKey => List(
    GPIOParams(address = 0x10012000, width = 32, includeIOF = true))
  case PeripheryPWMKey => List(
    PWMParams(address = 0x10015000, cmpWidth = 8),
    PWMParams(address = 0x10025000, cmpWidth = 16),
    PWMParams(address = 0x10035000, cmpWidth = 16))
  case PeripherySPIKey => List(
    SPIParams(csWidth = 4, rAddress = 0x10024000, defaultSampleDel = 3),
    SPIParams(csWidth = 1, rAddress = 0x10034000, defaultSampleDel = 3))
  case PeripherySPIFlashKey => List(
    SPIFlashParams(
      fAddress = 0x20000000,
      rAddress = 0x10014000,
      defaultSampleDel = 3))
  case PeripheryUARTKey => List(
    UARTParams(address = 0x10013000),
    UARTParams(address = 0x10023000))
  case PeripheryI2CKey => List(
    I2CParams(address = 0x10016000))
  case DTSTimebase => BigInt(32768)
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = 0x000,
    idcodeManufId = 0x489,
    debugIdleCycles = 5)
})

class WithE300System extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new E300DigitalTop()(p)
})

class E300ArtyDevKitConfig extends Config(
  new WithE300System ++
  new WithE300Connections ++
  new E300DevKitExtra ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithL2TLBs(1024) ++
  new freechips.rocketchip.subsystem.With1TinyCore ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBreakpoints(2) ++
  new freechips.rocketchip.subsystem.WithJtagDTM ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)
