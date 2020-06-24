package chipyard.config

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.devices.debug.{Debug}
import freechips.rocketchip.groundtest.{GroundTestSubsystem}
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.util.{AsyncResetReg}

import testchipip._
import tracegen.{TraceGenSystem}

import hwacha.{Hwacha}

import boom.common.{BoomTileAttachParams}
import ariane.{ArianeTileAttachParams}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import chipyard.{BuildTop, BuildSystem}
import chipyard.{GenericTilesKey, GenericTileConfig}

/**
 * TODO: Why do we need this?
 */
object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

// -----------------------
// Common Config Fragments
// -----------------------

class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

// DOC include start: gpio config fragment
class WithGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(
    GPIOParams(address = 0x10012000, width = 4, includeIOF = false))
})
// DOC include end: gpio config fragment

class WithUART extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256))
})

class WithSPIFlash(size: BigInt = 0x10000000) extends Config((site, here, up) => {
  // Note: the default size matches freedom with the addresses below
  case PeripherySPIFlashKey => Seq(
    SPIFlashParams(rAddress = 0x10040000, fAddress = 0x20000000, fSize = size))
})

class WithL2TLBs(entries: Int) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case other => other
  }
})

class WithTracegenSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => LazyModule(new TraceGenSystem()(p))
})

/**
 * Map from a hartId to a particular RoCC accelerator
 */
case object MultiRoCCKey extends Field[Map[Int, Seq[Parameters => LazyRoCC]]](Map.empty[Int, Seq[Parameters => LazyRoCC]])

/**
 * Config fragment to enable different RoCCs based on the hartId
 */
class WithMultiRoCC extends Config((site, here, up) => {
  case BuildRoCC => site(MultiRoCCKey).getOrElse(site(TileKey).hartId, Nil)
})

/**
 * Config fragment to add Hwachas to cores based on hart
 *
 * For ex:
 *   Core 0, 1, 2, 3 have been defined earlier
 *     with hartIds of 0, 1, 2, 3 respectively
 *   And you call WithMultiRoCCHwacha(0,1)
 *   Then Core 0 and 1 will get a Hwacha
 *
 * @param harts harts to specify which will get a Hwacha
 */
class WithMultiRoCCHwacha(harts: Int*) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        LazyModule(new Hwacha()(p)).suggestName("hwacha")
      }))
    }
  }
})


class WithTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case tp: ArianeTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      trace = true))
    case other => other
  }
  case TracePortKey => Some(TracePortParams())
})

class WithNPerfCounters(n: Int = 29) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case other => other
  }
})
