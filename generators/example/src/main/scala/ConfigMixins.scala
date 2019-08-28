package example

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{RocketTilesKey, CacheBlockBytes}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.{XLen, BuildRoCC, TileKey, LazyRoCC, OpcodeSet}

import boom.system.{BoomTilesKey}

import testchipip._

import hwacha.{Hwacha}

import sifive.blocks.devices.gpio._

import icenet.{NICKey, NICConfig}

import memblade.cache.{DRAMCacheKey, DRAMCacheConfig}
import memblade.client.{RemoteMemClientKey, RemoteMemClientConfig}
import memblade.manager.{MemBladeKey, MemBladeParams, MemBladeQueueParams}
import memblade.prefetcher.{PrefetchRoCC, SoftPrefetchConfig, AutoPrefetchConfig}

import scala.math.max

/**
 * TODO: Why do we need this?
 */
object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

// -----------------------
// Common Parameter Mixins
// -----------------------

/**
 * Class to specify where the BootRom file is (from `rebar` top)
 */
class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

/**
 * Class to add in GPIO
 */
class WithGPIO extends Config((site, here, up) => {
  case PeripheryGPIOKey => List(
    GPIOParams(address = 0x10012000, width = 4, includeIOF = false))
})

// -----------------------------------------------
// BOOM and/or Rocket Top Level System Parameter Mixins
// -----------------------------------------------

/**
 * Class to specify a "plain" top level BOOM and/or Rocket system
 */
class WithNormalBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomRocketTop()(p)).module)
  }
})

/**
 * Class to specify a top level BOOM and/or Rocket system with DTM
 */
class WithDTMBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTopWithDTM => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomRocketTopWithDTM()(p)).module)
  }
})

/**
 * Class to specify a top level BOOM and/or Rocket system with PWM
 */
class WithPWMBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomRocketTopWithPWMTL()(p)).module)
})

/**
 * Class to specify a top level BOOM and/or Rocket system with a PWM AXI4
 */
class WithPWMAXI4BoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomRocketTopWithPWMAXI4()(p)).module)
})

/**
 * Class to specify a top level BOOM and/or Rocket system with a block device
 */
class WithBlockDeviceModelBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomRocketTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

/**
 * Class to specify a top level BOOM and/or Rocket system with a simulator block device
 */
class WithSimBlockDeviceBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomRocketTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

/**
 * Class to specify a top level BOOM and/or Rocket system with GPIO
 */
class WithGPIOBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomRocketTopWithGPIO()(p)).module)
    for (gpio <- top.gpio) {
      for (pin <- gpio.pins) {
        pin.i.ival := false.B
      }
    }
    top
  }
})

// ------------------
// Multi-RoCC Support
// ------------------

/**
 * Map from a hartId to a particular RoCC accelerator
 */
case object MultiRoCCKey extends Field[Map[Int, Seq[Parameters => LazyRoCC]]](Map.empty[Int, Seq[Parameters => LazyRoCC]])

/**
 * Mixin to enable different RoCCs based on the hartId
 */
class WithMultiRoCC extends Config((site, here, up) => {
  case BuildRoCC => site(MultiRoCCKey).getOrElse(site(TileKey).hartId, Nil)
})

/**
 * Mixin to add Hwachas to cores based on hart
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
    require(harts.max <= ((up(RocketTilesKey, site).length + up(BoomTilesKey, site).length) - 1))
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        LazyModule(new Hwacha()(p)).suggestName("hwacha")
      }))
    }
  }
})

class WithIceNIC(inBufFlits: Int = 1800, usePauser: Boolean = false)
    extends Config((site, here, up) => {
  case NICKey => NICConfig(
    inBufFlits = inBufFlits,
    usePauser = usePauser)
})

class WithLoopbackNICBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomRocketTopWithIceNIC()(p)).module)
    top.connectNicLoopback()
    top
  }
})

class WithRemoteMemClient(spanBytes: Int = 1024)
    extends Config((site, here, up) => {
  case RemoteMemClientKey => RemoteMemClientConfig(
    spanBytes = spanBytes,
    nRMemXacts = 32768 / spanBytes)
})

class WithMemBlade(spanBytes: Option[Int] = None)
    extends Config((site, here, up) => {
  case MemBladeKey => {
    val spanBytesVal = spanBytes.getOrElse(site(CacheBlockBytes))
    MemBladeParams(
      spanBytes = spanBytesVal,
      nSpanTrackers = max(384 / spanBytesVal, 2),
      spanQueue = MemBladeQueueParams(reqHeadDepth = 32, respHeadDepth = 32),
      wordQueue = MemBladeQueueParams(reqHeadDepth = 32, respHeadDepth = 32))
  }
})

class WithRemoteMemClientBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomRocketTopWithRemoteMemClient()(p)).module)
    top.connectTestMemBlade()
    top
  }
})

class WithDRAMCache(
    sizeKB: Int,
    nWays: Int = 7,
    nTrackersPerBank: Int = 1,
    nBanksPerChannel: Int = 1) extends Config((site, here, up) => {
  case DRAMCacheKey => {
    val spanBytes = site(CacheBlockBytes)
    val nSets = (sizeKB * 1024) / (nWays * spanBytes)
    DRAMCacheConfig(
      nSets = nSets,
      nWays = nWays,
      spanBytes = spanBytes,
      baseAddr = 1L << 32,
      extentBytes = 1 << 20,
      logAddrBits = 28,
      nTrackersPerBank = nTrackersPerBank,
      nBanksPerChannel = nBanksPerChannel,
      zeroMetadata = true)
  }
})

class WithDRAMCacheBoomRocketTop extends Config((site, here, up) => {
  case BuildBoomRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomRocketTopWithDRAMCache()(p)).module)
    top.connectTestMemBlade(100)
    top.connectSimAXICacheMem()
    top
  }
})

class WithPrefetchRoCC extends Config((site, here, up) => {
  case BuildRoCC => Seq((p: Parameters) =>
    LazyModule(new PrefetchRoCC(
      opcodes = OpcodeSet.custom2,
      soft = Some(new SoftPrefetchConfig(nMemXacts = 32)),
      auto = Some(new AutoPrefetchConfig(
        nWays = 4, nBlocks = 8, timeoutPeriod = 750)))(p)))
})
