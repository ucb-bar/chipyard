package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.devices.tilelink.{BootROMLocated, PLICKey, CLINTKey}
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
  case BootROMLocated(x) => {
    require(site(BootAddrRegKey).isDefined)
    up(BootROMLocated(x), site)
      .map(_.copy(contentFileName = s"${site(TargetDirKey)}/bootrom.rv${site(XLen)}.img"))
  }
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

class WithNoUART extends Config((site, here, up) => {
  case PeripheryUARTKey => Nil
})

class WithUARTFIFOEntries(txEntries: Int, rxEntries: Int) extends Config((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey).map(_.copy(nTxEntries = txEntries, nRxEntries = rxEntries))
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


class WithTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None // disable AXI backing memory
  case ExtTLMem => up(ExtMem, site) // enable TL backing memory
})

class WithSerialTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None
  case SerialTLKey => {
    val memPortParams = up(ExtMem).get
    require(memPortParams.nMemoryChannels == 1)
    val memParams = memPortParams.master
    up(SerialTLKey, site).map { k => k.copy(
      serialManagerParams = Some(k.serialManagerParams.getOrElse(SerialTLManagerParams(memParams)).copy(
        memParams = memParams,
        isMemoryDevice = true
      ))
    )}
  }
})

class WithExtMemIdBits(n: Int) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(idBits = n)))
})

class WithNoPLIC extends Config((site, here, up) => {
  case PLICKey => None
})

class WithNoCLINT extends Config((site, here, up) => {
  case CLINTKey => None
})

class WithNoBootROM extends Config((site, here, up) => {
  case BootROMLocated(_) => None
})

class WithNoBusErrorDevices extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey).copy(errorDevice = None)
  case ControlBusKey => up(ControlBusKey).copy(errorDevice = None)
  case PeripheryBusKey => up(PeripheryBusKey).copy(errorDevice = None)
  case MemoryBusKey => up(MemoryBusKey).copy(errorDevice = None)
  case FrontBusKey => up(FrontBusKey).copy(errorDevice = None)
})

class WithDebugModuleAbstractDataWords(words: Int = 16) extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map(_.copy(nAbstractDataWords=words))
})
