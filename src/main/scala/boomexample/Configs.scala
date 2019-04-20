package boomexample

import chisel3._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem.{WithRoccExample, WithNMemoryChannels}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.XLen
import testchipip._

class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

class WithBoomExampleTop extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomExampleTop()(p)).module)
  }
})

class WithPWM extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomExampleTopWithPWMTL()(p)).module)
})

class WithPWMAXI4 extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new BoomExampleTopWithPWMAXI4()(p)).module)
})

class WithBlockDeviceModel extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomExampleTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

class WithSimBlockDevice extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomExampleTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

class BaseBoomExampleConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallBoomConfig)

class DefaultBoomExampleConfig extends Config(
  new WithBoomExampleTop ++
  new BaseBoomExampleConfig)

class RoccBoomExampleConfig extends Config(
  new WithRoccExample ++
  new DefaultBoomExampleConfig)

class PWMBoomExampleConfig extends Config(
  new WithPWM ++
  new BaseBoomExampleConfig)

class PWMAXI4BoomExampleConfig extends Config(
  new WithPWMAXI4 ++
  new BaseBoomExampleConfig)

class SimBlockDeviceBoomExampleConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDevice ++
  new BaseBoomExampleConfig)

class BlockDeviceModelBoomExampleConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModel ++
  new BaseBoomExampleConfig)

class DualCoreBoomExampleConfig extends Config(
  // Core gets tacked onto existing list
  new boom.system.WithNBoomCores(2) ++
  new DefaultBoomExampleConfig)

class RV32BoomExampleConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallRV32UnifiedBoomConfig)
