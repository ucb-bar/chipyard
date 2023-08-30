package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.devices.tilelink.{BootROMLocated, PLICKey, CLINTKey}
import freechips.rocketchip.devices.debug.{Debug, ExportDebug, DebugModuleKey, DMI, JtagDTMKey, JtagDTMConfig}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import chipyard.stage.phases.TargetDirKey
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._

import testchipip._

import chipyard.{ExtTLMem}

/**
  * Config fragment for adding a BootROM to the SoC
  *
  * @param address the address of the BootROM device
  * @param size the size of the BootROM
  * @param hang the power-on reset vector, i.e. the program counter will be set to this value on reset
  * @param contentFileName the path to the BootROM image
  */
class WithBootROM(address: BigInt = 0x10000, size: Int = 0x10000, hang: BigInt = 0x10040) extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site)
      .map(_.copy(
        address = address,
        size = size,
        hang = hang,
        contentFileName = s"${site(TargetDirKey)}/bootrom.rv${site(XLen)}.img"
      ))
})

// DOC include start: gpio config fragment
/**
 * Config fragment for adding a GPIO peripheral device to the SoC
 *
 * @param address the address of the GPIO device
 * @param width the number of pins of the GPIO device
 */
class WithGPIO(address: BigInt = 0x10010000, width: Int = 4) extends Config ((site, here, up) => {
  case PeripheryGPIOKey => up(PeripheryGPIOKey) ++ Seq(
    GPIOParams(address = address, width = width, includeIOF = false))
})
// DOC include end: gpio config fragment

/**
 * Config fragment for removing all UART peripheral devices from the SoC
 */
class WithNoUART extends Config((site, here, up) => {
  case PeripheryUARTKey => Nil
})

/**
  * Config fragment for adding a UART peripheral device to the SoC
  *
  * @param address the address of the UART device
  * @param baudrate the baudrate of the UART device
  */
class WithUART(baudrate: BigInt = 115200, address: BigInt = 0x10020000) extends Config ((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey) ++ Seq(
    UARTParams(address = address, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})

class WithUARTFIFOEntries(txEntries: Int, rxEntries: Int) extends Config((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey).map(_.copy(nTxEntries = txEntries, nRxEntries = rxEntries))
})

class WithUARTInitBaudRate(baudrate: BigInt = 115200) extends Config ((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey).map(_.copy(initBaudRate=baudrate))
})

/**
  * Config fragment for adding a SPI peripheral device with Execute-in-Place capability to the SoC
  *
  * @param address the address of the SPI controller
  * @param fAddress the address of the Execute-in-Place (XIP) region of the SPI flash memory
  * @param size the size of the Execute-in-Place (XIP) region of the SPI flash memory
  */
class WithSPIFlash(size: BigInt = 0x10000000, address: BigInt = 0x10030000, fAddress: BigInt = 0x20000000) extends Config((site, here, up) => {
  // Note: the default size matches freedom with the addresses below
  case PeripherySPIFlashKey => up(PeripherySPIFlashKey) ++ Seq(
    SPIFlashParams(rAddress = address, fAddress = fAddress, fSize = size))
})

/**
  * Config fragment for adding a SPI peripheral device to the SoC
  *
  * @param address the address of the SPI controller
  */
class WithSPI(address: BigInt = 0x10031000) extends Config((site, here, up) => {
  case PeripherySPIKey => up(PeripherySPIKey) ++ Seq(
    SPIParams(rAddress = address))
})

/**
  * Config fragment for adding a I2C peripheral device to the SoC
  *
  * @param address the address of the I2C controller
  */
class WithI2C(address: BigInt = 0x10040000) extends Config((site, here, up) => {
  case PeripheryI2CKey => up(PeripheryI2CKey) ++ Seq(
    I2CParams(address = address, controlXType = AsynchronousCrossing(), intXType = AsynchronousCrossing())
  )
})

class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})

class WithDMIDTM extends Config((site, here, up) => {
  case ExportDebug => up(ExportDebug, site).copy(protocols = Set(DMI))
})

/**
  * Config fragment for adding a JTAG Debug Module to the SoC
  *
  * @param idcodeVersion the version of the JTAG protocol the Debug Module supports
  * @param partNum the part number of the Debug Module
  * @param manufId the 11-bit JEDEC Designer ID of the chip manufacturer
  * @param debugIdleCycles the number of cycles the Debug Module waits before responding to a request
  */
class WithJTAGDTMKey(idcodeVersion: Int = 2, partNum: Int = 0x000, manufId: Int = 0x489, debugIdleCycles: Int = 5) extends Config((site, here, up) => {
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = idcodeVersion,
    idcodePartNum = partNum,
    idcodeManufId = manufId,
    debugIdleCycles = debugIdleCycles)
})

class WithTLBackingMemory extends Config((site, here, up) => {
  case ExtMem => None // disable AXI backing memory
  case ExtTLMem => up(ExtMem, site) // enable TL backing memory
})

class WithExtMemIdBits(n: Int) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(idBits = n)))
})

class WithNoPLIC extends Config((site, here, up) => {
  case PLICKey => None
})

class WithDebugModuleAbstractDataWords(words: Int = 16) extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map(_.copy(nAbstractDataWords=words))
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
