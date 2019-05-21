package example

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem.{RocketTilesKey, WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.{XLen, MaxHartIdBits}

import testchipip._

import sifive.blocks.devices.gpio._

import boom.system.{BoomTilesKey}

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

// --------------------------------------
// BOOM + Rocket Top Level System Parameter Mixins
// --------------------------------------

/**
 * Class to specify a "plain" top level BOOM + Rocket system
 */
class WithNormalBoomAndRocketTop extends Config((site, here, up) => {
  case BuildBoomAndRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomAndRocketTop()(p)).module)
  }
})

/**
 * Class to specify a top level BOOM + Rocket system with PWM
 */
class WithPWMBoomAndRocketTop extends Config((site, here, up) => {
  case BuildBoomAndRocketTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomAndRocketTopWithPWMTL()(p)).module)
})

/**
 * Class to specify a top level BOOM + Rocket system with a PWM AXI4
 */
class WithPWMAXI4BoomAndRocketTop extends Config((site, here, up) => {
  case BuildBoomAndRocketTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomAndRocketTopWithPWMAXI4()(p)).module)
})

/**
 * Class to specify a top level BOOM + Rocket system with a block device
 */
class WithBlockDeviceModelBoomAndRocketTop extends Config((site, here, up) => {
  case BuildBoomAndRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomAndRocketTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

/**
 * Class to specify a top level BOOM + Rocket system with a simulator block device
 */
class WithSimBlockDeviceBoomAndRocketTop extends Config((site, here, up) => {
  case BuildBoomAndRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomAndRocketTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

/**
 * Class to specify a top level BOOM + Rocket system with GPIO
 */
class WithGPIOBoomAndRocketTop extends Config((site, here, up) => {
  case BuildBoomAndRocketTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomAndRocketTopWithGPIO()(p)).module)
    for (gpio <- top.gpio) {
      for (pin <- gpio.pins) {
        pin.i.ival := false.B
      }
    }
    top
  }
})

/**
 * Class to renumber BOOM + Rocket harts so that there are no overlapped harts
 * This mixin assumes Rocket tiles are numbered before BOOM tiles
 * Also makes support for multiple harts depend on Rocket + BOOM
 * Note: Must come after all harts are assigned for it to apply
 */
class WithRenumberHarts extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site).zipWithIndex map { case (r, i) =>
    r.copy(hartId = i)
  }
  case BoomTilesKey => up(BoomTilesKey, site).zipWithIndex map { case (b, i) =>
    b.copy(hartId = i + up(RocketTilesKey, site).length)
  }
  case MaxHartIdBits => log2Up(up(BoomTilesKey, site).size + up(RocketTilesKey, site).size)
})
