package boomexample

import boom._
import boom.system.{BoomConfig, BoomTilesKey}
import chisel3._
import example.WithBootROM
import example.ConfigValName._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.diplomacy.LazyModule
import testchipip._

object Lab3Settings {
  val N_ROB_ENTRIES = 16
  val PHYS_REG_COUNT = 64
  val USE_BRANCH_PREDICTOR = true
  val FETCH_BUF_SIZE = 8
  val N_ISSUE_SLOTS = 4
  val N_LSU_ENTRIES = 4
  val N_BHT_ENTRIES = 128
  val MAX_BR_COUNT = 4
  val N_MSHRS = 2
  val DCACHE_WAYS = 4
  val DCACHE_LINES = 256

  val CUSTOM_BR_PRED_EN = false
  val CUSTOM_BR_PRED_HISTORY_LEN = 1
  val CUSTOM_BR_PRED_INFO_SIZE = 0
}

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

import Lab3Settings._

class WithLab3Settings extends Config((site, here, up) => {
  case Lab3Key => Lab3Parameters(
    enabled = CUSTOM_BR_PRED_EN,
    history_length = CUSTOM_BR_PRED_HISTORY_LEN,
    info_size = CUSTOM_BR_PRED_INFO_SIZE)
  case BHTKey => BHTParameters(num_entries = N_BHT_ENTRIES)
  case BoomTilesKey => up(BoomTilesKey, site).map { r => r.copy(
    core = r.core.copy(
      fetchWidth = 1,
      decodeWidth = 1,
      fetchBufferSz = FETCH_BUF_SIZE,
      numRobEntries = N_ROB_ENTRIES,
      issueParams = Seq(
            IssueParams(issueWidth=1, numEntries=N_ISSUE_SLOTS, iqType=IQT_MEM.litValue),
            IssueParams(issueWidth=1, numEntries=N_ISSUE_SLOTS, iqType=IQT_INT.litValue),
            IssueParams(issueWidth=1, numEntries=N_ISSUE_SLOTS, iqType=IQT_FP.litValue)),
      enableBranchPredictor = USE_BRANCH_PREDICTOR,
      enableBTBContainsBranches = false,
      gshare = None,
      numIntPhysRegisters = PHYS_REG_COUNT,
      numFpPhysRegisters = PHYS_REG_COUNT,
      numLsuEntries = N_LSU_ENTRIES,
      maxBrCount = MAX_BR_COUNT,
      nPerfCounters = 2),
    btb = None,
    dcache = Some(r.dcache.get.copy(
      nSets = DCACHE_LINES / DCACHE_WAYS,
      nWays = DCACHE_WAYS,
      nMSHRs = N_MSHRS)),
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
