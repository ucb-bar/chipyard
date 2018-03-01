package boomexample

import boom._
import boom.system.{BoomConfig, BoomTilesKey}
import chisel3._
import example.WithBootROM
import example.ConfigValName._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.diplomacy.LazyModule
import testchipip._

class WithBoomExampleTop extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BoomExampleTop()(p)).module)
  }
})

class WithBlockDeviceModel extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomExampleTopWithBlockDevice()(p)).module)
    top.connectBlockDeviceModel()
    top
  }
})

class WithSimBlockDevice extends Config((site, here, up) => {
  case BuildBoomTop => (clock: Clock, reset: Bool, p: Parameters) => {
    val top = Module(LazyModule(new BoomExampleTopWithBlockDevice()(p)).module)
    top.connectSimBlockDevice(clock, reset)
    top
  }
})

class WithLab3Settings extends Config((site, here, up) => {
  case BHTKey => BHTParameters()
  case BoomTilesKey => up(BoomTilesKey, site).map { r => r.copy(
    core = r.core.copy(
      fetchWidth = 1,
      decodeWidth = 1,
      numRobEntries = 16,
      issueParams = Seq(
            IssueParams(issueWidth=1, numEntries=4, iqType=IQT_MEM.litValue),
            IssueParams(issueWidth=1, numEntries=4, iqType=IQT_INT.litValue),
            IssueParams(issueWidth=1, numEntries=4, iqType=IQT_FP.litValue)),
      enableBranchPredictor = true,
      enableBTBContainsBranches = false,
      gshare = None,
      numIntPhysRegisters = 64,
      numFpPhysRegisters = 64,
      numLsuEntries = 4,
      maxBrCount = 4,
      nPerfCounters = 2),
    btb = None,
    icache = Some(r.icache.get.copy(fetchBytes=1*4)))
  }
})

class BaseExampleConfig extends Config(
  new WithBootROM ++ new BoomConfig)

class DefaultExampleConfig extends Config(
  new WithBoomExampleTop ++ new BaseExampleConfig)

class Lab3Config extends Config(
  new WithLab3Settings ++ new DefaultExampleConfig)

class SimBlockDeviceConfig extends Config(
  new WithBlockDevice ++ new WithSimBlockDevice ++ new BaseExampleConfig)

class BlockDeviceModelConfig extends Config(
  new WithBlockDevice ++ new WithBlockDeviceModel ++ new BaseExampleConfig)

class WithSmallBooms extends boom.WithSmallBooms
