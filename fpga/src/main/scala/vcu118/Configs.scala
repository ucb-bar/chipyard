package chipyard.fpga.vcu118

import sys.process._

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem}
import freechips.rocketchip.devices.debug.{DebugModuleKey, ExportDebug, JTAG}
import freechips.rocketchip.devices.tilelink.{DevNullParams, BootROMLocated}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet}
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VCU118ShellPMOD, VCU118DDRSize}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(SPIParams(rAddress = BigInt(0x64001000L)))
  case VCU118ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DebugModuleKey => None // disable debug module
  case ExportDebug => up(ExportDebug).copy(protocols = Set(JTAG)) // don't generate HTIF DTS
  case SystemBusKey => up(SystemBusKey).copy(
    errorDevice = Some(DevNullParams(
      Seq(AddressSet(0x3000, 0xfff)),
      maxAtomic=site(XLen)/8,
      maxTransfer=128,
      region = RegionType.TRACKED)))
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency =
    Some(BigDecimal(site(FPGAFrequencyKey)*1000000).setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt))
  case ControlBusKey => up(ControlBusKey, site).copy(
    errorDevice = None)
  case DTSTimebase => BigInt(1000000)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val freqMHz = site(FPGAFrequencyKey).toInt * 1000000
    val make = s"make -C fpga/src/main/resources/vcu118/sdboot PBUS_CLK=${freqMHz} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./fpga/src/main/resources/vcu118/sdboot/build/sdboot.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VCU118DDRSize))))
})

class AbstractVCU118Config extends Config(
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  new WithUARTIOPassthrough ++
  new WithSPIIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithDefaultPeripherals ++
  new WithSystemModifications ++ // remove debug module, setup busses, use sdboot bootrom, setup ext. mem. size
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++
  new chipyard.config.WithL2TLBs(1024) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new chipyard.WithMulticlockCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class RocketVCU118Config extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new AbstractVCU118Config)

class BoomVCU118Config extends Config(
  new WithFPGAFrequency(75) ++
  new boom.common.WithNLargeBooms(1) ++
  new AbstractVCU118Config)

class WithFPGAFrequency(MHz: Double) extends Config((site, here, up) => {
  case FPGAFrequencyKey => MHz
})

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)
