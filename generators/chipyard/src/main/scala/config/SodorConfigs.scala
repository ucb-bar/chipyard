package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

class Sodor1StageConfig extends Config(
  // Create a Sodor 1-stage core
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage1Factory) ++
  new testchipip.WithSerialTLWidth(32) ++
  new testchipip.WithSerialPBusMem ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use sodor tile-internal scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor2StageConfig extends Config(
  // Create a Sodor 2-stage core
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage2Factory) ++
  new testchipip.WithSerialTLWidth(32) ++
  new testchipip.WithSerialPBusMem ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use sodor tile-internal scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor3StageConfig extends Config(
  // Create a Sodor 1-stage core with two ports
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage3Factory(ports = 2)) ++
  new testchipip.WithSerialTLWidth(32) ++
  new testchipip.WithSerialPBusMem ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use sodor tile-internal scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor3StageSinglePortConfig extends Config(
  // Create a Sodor 3-stage core with one ports (instruction and data memory access controlled by arbiter)
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage3Factory(ports = 1)) ++
  new testchipip.WithSerialTLWidth(32) ++
  new testchipip.WithSerialPBusMem ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use sodor tile-internal scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class Sodor5StageConfig extends Config(
  // Create a Sodor 5-stage core
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.Stage5Factory) ++
  new testchipip.WithSerialTLWidth(32) ++
  new testchipip.WithSerialPBusMem ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use sodor tile-internal scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)

class SodorUCodeConfig extends Config(
  // Construct a Sodor microcode-based single-bus core
  new sodor.common.WithNSodorCores(1, internalTile = sodor.common.UCodeFactory) ++
  new testchipip.WithSerialTLWidth(32) ++
  new testchipip.WithSerialPBusMem ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use sodor tile-internal scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig)
