package chipyard

import chisel3._

import org.chipsalliance.cde.config.{Config, Parameters}

// --------------------
// EE290 Rocket Configs
// --------------------

// -----
// Lab 3
// -----

// Rocket-based

class GemminiEE290Lab3RocketConfig extends Config(
  new gemmini.GemminiEE290Lab3Config ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiEE290Lab3BigSPRocketConfig extends Config(
  new gemmini.GemminiEE290Lab3LargeSPConfig ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// BOOM-based

class GemminiEE290Lab3BoomConfig extends Config(
  new gemmini.GemminiEE290Lab3Config ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiEE290Lab3BigSPBoomConfig extends Config(
  new gemmini.GemminiEE290Lab3LargeSPConfig ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)