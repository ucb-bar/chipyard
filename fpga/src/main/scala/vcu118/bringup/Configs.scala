package chipyard.fpga.vcu118.bringup

import math.min

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
  case SystemBusKey => up(SystemBusKey).copy(
    errorDevice = Some(DevNullParams(
      Seq(AddressSet(0x3000, 0xfff)),
      maxAtomic=site(XLen)/8,
      maxTransfer=128,
      region = RegionType.TRACKED)))
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency =
    Some(BigDecimal(site(DUTFrequencyKey)*1000000).setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt),
    errorDevice = None)
  case DTSTimebase => BigInt(1000000)
  case JtagDTMKey => new JtagDTMConfig(
    idcodeVersion = 2,      // 1 was legacy (FE310-G000, Acai).
    idcodePartNum = 0x000,  // Decided to simplify.
    idcodeManufId = 0x489,  // As Assigned by JEDEC to SiFive. Only used in wrappers / test harnesses.
    debugIdleCycles = 5)    // Reasonable guess for synchronization
})


class FakeBringupConfig extends Config(
  new WithBringupUART ++
  new WithBringupSPI ++
  new WithBringupI2C ++
  new WithBringupGPIO ++
  new WithBringupDDR ++
  new WithUARTIOPassthrough ++
  new WithSPIIOPassthrough ++
  //new WithMMCSPIDTS ++
  new WithI2CIOPassthrough ++
  new WithGPIOIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithBringupPeripherals ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithL2TLBs(1024) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
