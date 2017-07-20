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

class WithLoopbackNIC extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => {
    val top = Module(LazyModule(new ExampleTopWithSimpleNIC()(p)).module)
    top.connectNicLoopback()
    top
  }
})

class WithSimNetwork extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => {
    val top = Module(LazyModule(new ExampleTopWithSimpleNIC()(p)).module)
    top.connectSimNetwork()
    top
  }
})

class BaseExampleConfig extends Config(
  new WithSerialAdapter ++
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

class LoopbackNICConfig extends Config(
  new WithLoopbackNIC ++ new BaseExampleConfig)

class SimNetworkConfig extends Config(
  new WithSimNetwork ++ new BaseExampleConfig)

class WithTwoTrackers extends WithNBlockDeviceTrackers(2)
class WithFourTrackers extends WithNBlockDeviceTrackers(4)

class WithTwoMemChannels extends WithNMemoryChannels(2)
class WithFourMemChannels extends WithNMemoryChannels(4)
