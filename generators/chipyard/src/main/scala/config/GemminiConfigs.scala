
package chipyard

import chisel3._
import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet}


object ContentionConfigs{
  import gemmini.GemminiConfigs.{defaultConfig => base}
  val baseConfig = base.copy(headerFileName = "gemmini_params_contention_base.h")
  val baseSPConfig = base.copy(sp_capacity = gemmini.CapacityInKilobytes(256), acc_capacity = gemmini.CapacityInKilobytes(256), headerFileName = "gemmini_params_contention_base_sp.h")
  val smallerSPConfig = base.copy(sp_capacity = gemmini.CapacityInKilobytes(128), headerFileName = "gemmini_params_contention_smaller_sp.h")
  val biggerSPConfig = base.copy(sp_capacity = gemmini.CapacityInKilobytes(512), acc_capacity = gemmini.CapacityInKilobytes(512), headerFileName = "gemmini_params_contention_bigger_sp.h")
}

class GemminiContentionBaseConfig extends Config((site, here, up) => {
  case BuildRoCC => Seq(
      (p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new gemmini.Gemmini(OpcodeSet.custom3, ContentionConfigs.baseSPConfig))
    }
  )
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
})

class GemminiContentionSmallerSPConfig extends Config((site, here, up) => {
  case BuildRoCC => Seq(
      (p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new gemmini.Gemmini(OpcodeSet.custom3, ContentionConfigs.smallerSPConfig))
    }
  )
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
})

class GemminiContentionBiggerSPConfig extends Config((site, here, up) => {
  case BuildRoCC => Seq(
      (p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new gemmini.Gemmini(OpcodeSet.custom3, ContentionConfigs.biggerSPConfig))
    }
  )
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
})


// --------------
// Gemmini Configs
// --------------

class GemminiContentionSingleBaseConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBaseConfig ++                        // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=1024) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)


class GemminiContentionDualBaseConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBaseConfig ++                        // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=1024) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class GemminiContentionDualBaseBankedL2Config extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBaseConfig ++                        // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, capacityKB=1024) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class GemminiContentionDualBiggerL2SmallerSPConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionSmallerSPConfig ++                 // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=2048) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class GemminiContentionDualSmallerL2BiggerSPConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBiggerSPConfig ++                 // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class GemminiContentionDualBaseL2BiggerSPConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBiggerSPConfig ++                 // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=1024) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class GemminiContentionDualBiggerL2BiggerSPConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBiggerSPConfig ++                 // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=2048) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class GemminiContentionDualBiggerL2BaseSPConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new chipyard.config.WithL2TLBs(1024) ++
  new GemminiContentionBaseConfig ++                 // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB=2048) ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)
