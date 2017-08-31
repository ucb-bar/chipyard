package example

import chisel3._
import freechips.rocketchip.chip._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.coreplex.WithRoccExample
import freechips.rocketchip.diplomacy.LazyModule
import testchipip._

class WithExampleTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) =>
    Module(LazyModule(new ExampleTop()(p)).module)
})

class WithPWM extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) =>
    Module(LazyModule(new ExampleTopWithPWM()(p)).module)
})

class WithBlockDeviceModel extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => {
    val top = Module(LazyModule(new ExampleTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

class WithSimBlockDevice extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => {
    val top = Module(LazyModule(new ExampleTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice()
    top
  }
})

class BaseExampleConfig extends Config(
  new freechips.rocketchip.chip.DefaultConfig)

class DefaultExampleConfig extends Config(
  new WithExampleTop ++ new BaseExampleConfig)

class RoccExampleConfig extends Config(
  new WithRoccExample ++ new DefaultExampleConfig)

class PWMConfig extends Config(new WithPWM ++ new BaseExampleConfig)

class SimBlockDeviceConfig extends Config(
  new WithBlockDevice ++ new WithSimBlockDevice ++ new BaseExampleConfig)

class BlockDeviceModelConfig extends Config(
  new WithBlockDevice ++ new WithBlockDeviceModel ++ new BaseExampleConfig)

class WithTwoTrackers extends WithNBlockDeviceTrackers(2)
class WithFourTrackers extends WithNBlockDeviceTrackers(4)

class WithTwoMemChannels extends WithNMemoryChannels(2)
class WithFourMemChannels extends WithNMemoryChannels(4)
