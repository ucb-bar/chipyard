package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

class Sodor1StageConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage1Factory) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class Sodor2StageConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage2Factory) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class Sodor3StageConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage3Factory(ports = 2)) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class Sodor3StageSinglePortConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage3Factory(ports = 1)) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class Sodor5StageConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage5Factory) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class SodorUCodeConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.UCodeFactory) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)
