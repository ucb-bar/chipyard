package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

class Sodor1StageConfig extends Config(
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage1Factory) ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor2StageConfig extends Config(
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage2Factory) ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor3StageConfig extends Config(
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage3Factory(ports = 2)) ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor3StageSinglePortConfig extends Config(
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage3Factory(ports = 1)) ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor5StageConfig extends Config(
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage5Factory) ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class SodorUCodeConfig extends Config(
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.UCodeFactory) ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 scratchpad
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)
