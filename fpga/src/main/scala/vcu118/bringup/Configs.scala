package chipyard.fpga.vcu118.bringup

import math.min

import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet, ResourceBinding, Resource, ResourceAddress}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._

import sifive.blocks.devices.gpio.{PeripheryGPIOKey, GPIOParams}
import sifive.blocks.devices.i2c.{PeripheryI2CKey, I2CParams}
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VCU118ShellPMOD, VCU118DDRSize}

import testchipip.{PeripheryTSIHostKey, TSIHostParams, TSIHostSerdesParams}

import chipyard.{BuildSystem}

import chipyard.fpga.vcu118.{WithVCU118Tweaks, WithFPGAFrequency, VCU118DDR2Size}

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
  case TSIClockMaxFrequency => 100
  case PeripheryTSIHostKey => List(
    TSIHostParams(
      serialIfWidth = 4,
      mmioBaseAddress = BigInt(0x64006000),
      mmioSourceId = 1 << 13, // manager source
      serdesParams = TSIHostSerdesParams(
        clientPortParams = TLMasterPortParameters.v1(
          clients = Seq(TLMasterParameters.v1(
            name = "tl-tsi-host-serdes",
            sourceId = IdRange(0, (1 << 13))))),
        managerPortParams = TLSlavePortParameters.v1(
          managers = Seq(TLSlaveParameters.v1(
            address = Seq(AddressSet(0, site(VCU118DDR2Size) - 1)),
            regionType = RegionType.UNCACHED,
            executable = true,
            supportsGet        = TransferSizes(1, 64),
            supportsPutFull    = TransferSizes(1, 64),
            supportsPutPartial = TransferSizes(1, 64),
            supportsAcquireT   = TransferSizes(1, 64),
            supportsAcquireB   = TransferSizes(1, 64),
            supportsArithmetic = TransferSizes(1, 64),
            supportsLogical    = TransferSizes(1, 64))),
          endSinkId = 1 << 6, // manager sink
          beatBytes = 8))))
})

class WithBringupVCU118System extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new BringupVCU118DigitalTop()(p) // use the VCU118-extended bringup digital top
})

class WithBringupAdditions extends Config(
  new WithBringupUART ++
  new WithBringupSPI ++
  new WithBringupI2C ++
  new WithBringupGPIO ++
  new WithBringupTSIHost ++
  new WithTSITLIOPassthrough ++
  new WithI2CIOPassthrough ++
  new WithGPIOIOPassthrough ++
  new WithBringupPeripherals ++
  new WithBringupVCU118System)

class RocketBringupConfig extends Config(
  new WithBringupAdditions ++
  new WithVCU118Tweaks ++
  new chipyard.RocketConfig)

class BoomBringupConfig extends Config(
  new WithFPGAFrequency(75) ++
  new WithBringupAdditions ++
  new WithVCU118Tweaks ++
  new chipyard.MegaBoomConfig)
