package example

import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{RocketTilesKey, WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.{XLen, BuildRoCC, TileKey, LazyRoCC}

import boom.system.{BoomTilesKey}

import testchipip._

import hwacha.{Hwacha}
import systolic.{SystolicArray}

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

/*
class WithMultiRoCCSystolic(harts: Int*) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    require(harts.max <= ((up(RocketTilesKey, site).length + up(BoomTilesKey, site).length) - 1))
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        LazyModule(new SystolicArray(SInt(8.W), SInt(16.W), SInt(32.W), freechips.rocketchip.tile.OpcodeSet.custom3)).suggestName("systolic")
      }))
    }
  }
})
*/
