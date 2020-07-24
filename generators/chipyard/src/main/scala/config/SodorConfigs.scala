package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

class SodorConfig extends Config(
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
  new sodor.common.WithNSodorCores(1) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)