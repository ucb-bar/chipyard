package example

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem.{WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.{XLen}

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

// -----------------------------------------------
// BOOM + Rocket Top Level System Parameter Mixins
// -----------------------------------------------

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
