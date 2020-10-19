package chipyard.fpga.vcu118.bringup

import math.min
import sys.process._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet, ResourceBinding, Resource, ResourceAddress}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pwm._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VCU118ShellPMOD}

import chipyard.{BuildTop}

import chipyard.harness._

class WithBringupPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)),
    UARTParams(address = BigInt(0x64003000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L),
              injectFunc = Some((spi: TLSPI) => {
                ResourceBinding {
                  Resource(new MMCDevice(spi.device, 1), "reg").bind(ResourceAddress(0))
                }
              })),
    SPIParams(rAddress = BigInt(0x64004000L)))
  case VCU118ShellPMOD => "SDIO"
  case PeripheryI2CKey => List(
    I2CParams(address = BigInt(0x64005000L)))
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

class SmallModifications extends Config((site, here, up) => {
  case DebugModuleKey => None // disable debug module
  case SystemBusKey => up(SystemBusKey).copy(
    errorDevice = Some(DevNullParams(
      Seq(AddressSet(0x3000, 0xfff)),
      maxAtomic=site(XLen)/8,
      maxTransfer=128,
      region = RegionType.TRACKED)))
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency =
    Some(BigDecimal(site(DUTFrequencyKey)*1000000).setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt))
  case ControlBusKey => up(ControlBusKey, site).copy(
    errorDevice = None)
  case DTSTimebase => BigInt(1000000)
})

class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val freqMHz = site(DUTFrequencyKey).toInt * 1000000
    val make = s"make -C fpga/src/main/resources/vcu118/sdboot PBUS_CLK=${freqMHz} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/vcu118/sdboot/build/sdboot.bin")
  }
})

class FakeBringupConfig extends Config(
  new SmallModifications ++
  new WithBringupUART ++
  new WithBringupSPI ++
  new WithBringupI2C ++
  new WithBringupGPIO ++
  new WithBringupDDR ++
  new WithUARTIOPassthrough ++
  new WithSPIIOPassthrough ++
  new WithI2CIOPassthrough ++
  new WithGPIOIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithBringupPeripherals ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++
  new WithBootROM ++ // use local bootrom
  new chipyard.config.WithL2TLBs(1024) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
