package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.devices.tilelink.{BootROMLocated}
import freechips.rocketchip.devices.debug.{Debug, ExportDebug, DebugModuleKey, DMI}
import freechips.rocketchip.stage.phases.TargetDirKey
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import testchipip._

import chipyard.{ExtTLMem}

// Set the bootrom to the Chipyard bootrom
class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site)
      .map(_.copy(contentFileName = s"${site(TargetDirKey)}/bootrom.rv${site(XLen)}.img"))
})

// DOC include start: gpio config fragment
class WithGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(
    GPIOParams(address = 0x10012000, width = 4, includeIOF = false))
})
// DOC include end: gpio config fragment

class WithUART(baudrate: BigInt = 115200) extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})

class WithSPIFlash(size: BigInt = 0x10000000) extends Config((site, here, up) => {
  // Note: the default size matches freedom with the addresses below
  case PeripherySPIFlashKey => Seq(
    SPIFlashParams(rAddress = 0x10040000, fAddress = 0x20000000, fSize = size))
})

class WithDMIDTM extends Config((site, here, up) => {
  case ExportDebug => up(ExportDebug, site).copy(protocols = Set(DMI))
})

class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})

class WithTLSerialLocation(masterWhere: TLBusWrapperLocation, slaveWhere: TLBusWrapperLocation) extends Config((site, here, up) => {
  case SerialTLAttachKey => up(SerialTLAttachKey, site).copy(masterWhere = masterWhere, slaveWhere = slaveWhere)
})

class WithTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None // disable AXI backing memory
  case ExtTLMem => up(ExtMem, site) // enable TL backing memory
})

class WithSerialTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None
  case SerialTLKey => up(SerialTLKey, site).map { k => k.copy(
    memParams = {
      val memPortParams = up(ExtMem, site).get
      require(memPortParams.nMemoryChannels == 1)
      memPortParams.master
    },
    isMemoryDevice = true
  )}
})

