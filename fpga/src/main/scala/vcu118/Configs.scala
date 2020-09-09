// See LICENSE for license details.
package chipyard.fpga.vcu118

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.mockaon._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pwm._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._

import sifive.fpgashells.shell.{DesignKey}

import chipyard.{BuildTop}

class WithChipyardBuildTop extends Config((site, here, up) => {
  //case DesignKey => { (p:Parameters) => p(BuildTop)(p) }
  case DesignKey => {(p: Parameters) => new chipyard.ChipTop()(p) }
})

class WithBringupUARTs extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)),
    UARTParams(address = BigInt(0x64003000L)))
})

class FakeBringupConfig extends Config(
  new WithUARTConnection1 ++
  new WithBringupUARTs ++
  new WithChipyardBuildTop ++
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
