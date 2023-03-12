package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.devices.tilelink.{BootROMLocated}
import freechips.rocketchip.devices.debug.{Debug, ExportDebug, DebugModuleKey, DMI, JtagDTMKey}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.stage.phases.TargetDirKey
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._

import testchipip._

import chipyard.{ExtTLMem}

// Set the bootrom to the Chipyard bootrom
class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site)
      .map(_.copy(contentFileName = s"${site(TargetDirKey)}/bootrom.rv${site(XLen)}.img"))
})

// DOC include start: gpio config fragment
class WithGPIO(address: BigInt = 0x10010000, width: Int = 4) extends Config ((site, here, up) => {
  case PeripheryGPIOKey => up(PeripheryGPIOKey) ++ Seq(
    GPIOParams(address = address, width = width, includeIOF = false))
})
// DOC include end: gpio config fragment

class WithUARTOverride(address: BigInt = 0x10020000, baudrate: BigInt = 115200) extends Config ((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = address, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})

class WithUART(address: BigInt = 0x10020000, baudrate: BigInt = 115200) extends Config ((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey) ++ Seq(
    UARTParams(address = address, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})

class WithNoUART extends Config((site, here, up) => {
  case PeripheryUARTKey => Nil
})

class WithUARTFIFOEntries(txEntries: Int, rxEntries: Int) extends Config((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey).map(_.copy(nTxEntries = txEntries, nRxEntries = rxEntries))
})

class WithSPIFlash(address: BigInt = 0x10030000, fAddress: BigInt = 0x20000000, size: BigInt = 0x10000000) extends Config((site, here, up) => {
  // Note: the default size matches freedom with the addresses below
  case PeripherySPIFlashKey => up(PeripherySPIFlashKey) ++ Seq(
    SPIFlashParams(rAddress = address, fAddress = fAddress, fSize = size))
})

class WithSPI(address: BigInt = 0x10031000) extends Config((site, here, up) => {
  case PeripherySPIKey => up(PeripherySPIKey) ++ Seq(
    SPIParams(rAddress = address))
})

class WithI2C(address: BigInt = 0x10040000) extends Config((site, here, up) => {
  case PeripheryI2CKey => up(PeripheryI2CKey) ++ Seq(
    I2CParams(address = address, controlXType = AsynchronousCrossing(), intXType = AsynchronousCrossing())
  )
})

class WithDMIDTM extends Config((site, here, up) => {
  case ExportDebug => up(ExportDebug, site).copy(protocols = Set(DMI))
})

class WithJTAGDTMKey(partNum: Int = 0x000, manufId: Int = 0x489) extends Config((site, here, up) => {
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = partNum,
    idcodeManufId = manufId,
    debugIdleCycles = 5)
})

class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})

class WithTLSerialLocation(masterWhere: TLBusWrapperLocation, slaveWhere: TLBusWrapperLocation) extends Config((site, here, up) => {
  case SerialTLAttachKey => up(SerialTLAttachKey, site).copy(masterWhere = masterWhere, slaveWhere = slaveWhere)
})

class WithNoSerialTL extends Config((site, here, up) => {
  case SerialTLKey => None // remove serialized tl port
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

class WithExtMemIdBits(n: Int) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(idBits = n)))
})

class WithNoPLIC extends Config((site, here, up) => {
  case PLICKey => None
})
