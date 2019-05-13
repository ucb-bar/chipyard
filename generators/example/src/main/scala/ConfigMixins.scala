package example

import chisel3._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem.{SystemBusKey, RocketTilesKey, CacheBlockBytes, WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.{XLen, RocketTileParams}
import freechips.rocketchip.rocket.{RocketCoreParams, DCacheParams, ICacheParams, MulDivParams}
import testchipip._
import sifive.blocks.devices.gpio._

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

// ----------------------------------------
// Rocket Top Level System Parameter Mixins
// ----------------------------------------

/**
 * Class to specify a "plain" top level rocket-chip system
 */
class WithNormalRocketTop extends Config((site, here, up) => {
  case BuildRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new RocketTop()(p)).module)
  }
})

/**
 * Class to specify a top level rocket-chip system with PWM
 */
class WithPWMRocketTop extends Config((site, here, up) => {
  case BuildRocketTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new RocketTopWithPWMTL()(p)).module)
})

/**
 * Class to specify a top level rocket-chip system with a PWM AXI4
 */
class WithPWMAXI4RocketTop extends Config((site, here, up) => {
  case BuildRocketTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new RocketTopWithPWMAXI4()(p)).module)
})

/**
 * Class to specify a top level rocket-chip system with a block device
 */
class WithBlockDeviceModelRocketTop extends Config((site, here, up) => {
  case BuildRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new RocketTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

/**
 * Class to specify a top level rocket-chip system with a simulator block device
 */
class WithSimBlockDeviceRocketTop extends Config((site, here, up) => {
  case BuildRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new RocketTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

/**
 * Class to specify a top level rocket-chip system with GPIO
 */
class WithGPIORocketTop extends Config((site, here, up) => {
  case BuildRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new RocketTopWithGPIO()(p)).module)
    for (gpio <- top.gpio) {
      for (pin <- gpio.pins) {
        pin.i.ival := false.B
      }
    }
    top
  }
})

// --------------------------------------
// BOOM Top Level System Parameter Mixins
// --------------------------------------

/**
 * Class to specify a "plain" top level BOOM system
 */
class WithNormalBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomTop()(p)).module)
  }
})

/**
 * Class to specify a top level BOOM system with PWM
 */
class WithPWMBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomTopWithPWMTL()(p)).module)
})

/**
 * Class to specify a top level BOOM system with a PWM AXI4
 */
class WithPWMAXI4BoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomTopWithPWMAXI4()(p)).module)
})

/**
 * Class to specify a top level BOOM system with a block device
 */
class WithBlockDeviceModelBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

/**
 * Class to specify a top level BOOM system with a simulator block device
 */
class WithSimBlockDeviceBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

/**
 * Class to specify a top level BOOM system with GPIO
 */
class WithGPIOBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomTopWithGPIO()(p)).module)
    for (gpio <- top.gpio) {
      for (pin <- gpio.pins) {
        pin.i.ival := false.B
      }
    }
    top
  }
})

// -------------
// Mixins for CI
// -------------

/**
 * Class to specify a smaller Rocket core for Hwacha CI
 */
class WithNHwachaSmallCores(n: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val small = RocketTileParams(
      core   = RocketCoreParams(mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 32,
        nWays = 1,
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 32,
        nWays = 1,
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => small.copy(hartId = i))
  }
})
