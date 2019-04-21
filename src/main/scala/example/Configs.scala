package example

import chisel3._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem.{WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.XLen
import testchipip._

/**
 * Class to specify where the BootRom file is (from `rebar` top)
 */
class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

/**
 * TODO: Why do we need this?
 */
object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

// -------------------------------
// Rocket Top Level System Configs
// -------------------------------

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

// --------------
// Rocket Configs
// --------------

class BaseRocketConfig extends Config(
  new WithBootROM ++
  new freechips.rocketchip.system.DefaultConfig)

class DefaultRocketConfig extends Config(
  new WithNormalRocketTop ++
  new BaseRocketConfig)

class RoccRocketConfig extends Config(
  new WithRoccExample ++
  new DefaultRocketConfig)

class PWMRocketConfig extends Config(
  new WithPWMRocketTop ++
  new BaseRocketConfig)

class PWMAXI4RocketConfig extends Config(
  new WithPWMAXI4RocketTop ++
  new BaseRocketConfig)

class SimBlockDeviceRocketConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceRocketTop ++
  new BaseRocketConfig)

class BlockDeviceModelRocketConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelRocketTop ++
  new BaseRocketConfig)

class DualCoreRocketConfig extends Config(
  new WithNBigCores(2) ++
  new DefaultRocketConfig)

class RV32RocketConfig extends Config(
  new WithRV32 ++
  new DefaultRocketConfig)

// -----------------------------
// BOOM Top Level System Configs
// -----------------------------

class WithNormalBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomTop()(p)).module)
  }
})

class WithPWMBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomTopWithPWMTL()(p)).module)
})

class WithPWMAXI4BoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomTopWithPWMAXI4()(p)).module)
})

class WithBlockDeviceModelBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

class WithSimBlockDeviceBoomTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

// ------------
// BOOM Configs
// ------------

class BaseBoomConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallBoomConfig)

class DefaultBoomConfig extends Config(
  new WithNormalBoomTop ++
  new BaseBoomConfig)

class RoccBoomConfig extends Config(
  new WithRoccExample ++
  new DefaultBoomConfig)

class PWMBoomConfig extends Config(
  new WithPWMBoomTop ++
  new BaseBoomConfig)

class PWMAXI4BoomConfig extends Config(
  new WithPWMAXI4BoomTop ++
  new BaseBoomConfig)

class SimBlockDeviceBoomConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceBoomTop ++
  new BaseBoomConfig)

class BlockDeviceModelBoomConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelBoomTop ++
  new BaseBoomConfig)

class DualCoreBoomConfig extends Config(
  // Core gets tacked onto existing list
  new boom.system.WithNBoomCores(2) ++
  new DefaultBoomConfig)

class RV32BoomConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallRV32UnifiedBoomConfig)
