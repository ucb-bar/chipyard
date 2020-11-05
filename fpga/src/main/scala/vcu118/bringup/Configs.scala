package chipyard.fpga.vcu118.bringup

import math.min

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet, ResourceBinding, Resource, ResourceAddress}

import sifive.blocks.devices.gpio.{PeripheryGPIOKey, GPIOParams}
import sifive.blocks.devices.i2c.{PeripheryI2CKey, I2CParams}
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VCU118ShellPMOD, VCU118DDRSize}

import chipyard.fpga.vcu118.{RocketVCU118Config, BoomVCU118Config}

class WithBringupPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey, site) ++ List(UARTParams(address = BigInt(0x64003000L)))
  case PeripherySPIKey => up(PeripherySPIKey, site) ++ List(SPIParams(rAddress = BigInt(0x64004000L)))
  case PeripheryI2CKey => List(I2CParams(address = BigInt(0x64005000L)))
  case PeripheryGPIOKey => {
    if (BringupGPIOs.width > 0) {
      require(BringupGPIOs.width <= 64) // currently only support 64 GPIOs (change addrs to get more)
      val gpioAddrs = Seq(BigInt(0x64002000), BigInt(0x64007000))
      val maxGPIOSupport = 32 // max gpios supported by SiFive driver (split by 32)
      List.tabulate(((BringupGPIOs.width - 1)/maxGPIOSupport) + 1)(n => {
        GPIOParams(address = gpioAddrs(n), width = min(BringupGPIOs.width - maxGPIOSupport*n, maxGPIOSupport))
      })
    }
    else {
      List.empty[GPIOParams]
    }
  }
})

class WithBringupAdditions extends Config(
  new WithBringupUART ++
  new WithBringupSPI ++
  new WithBringupI2C ++
  new WithBringupGPIO ++
  new WithI2CIOPassthrough ++
  new WithGPIOIOPassthrough ++
  new WithBringupPeripherals)

class RocketBringupConfig extends Config(
  new WithBringupPeripherals ++
  new RocketVCU118Config)

class BoomBringupConfig extends Config(
  new WithBringupPeripherals ++
  new BoomVCU118Config)
