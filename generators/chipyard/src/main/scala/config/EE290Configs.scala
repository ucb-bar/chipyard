package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

// --------------------
// EE290 Rocket Configs
// --------------------

// -----
// Lab 2
// -----

// Rocket-based

class GemminiEE290Lab2RocketConfig extends Config(
  new gemmini.GemminiEE290Lab2Config ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiEE290Lab2BigSPRocketConfig extends Config(
  new gemmini.GemminiEE290Lab2LargeSPConfig ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// BOOM-based

class GemminiEE290Lab2BoomConfig extends Config(
  new gemmini.GemminiEE290Lab2Config ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiEE290Lab2BigSPBoomConfig extends Config(
  new gemmini.GemminiEE290Lab2LargeSPConfig ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

// -----
// Lab 3
// -----

// Rocket-based

class GemminiEE290Lab3RocketConfig extends Config(
  new gemmini.GemminiEE290Lab3Config ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiEE290Lab3SmallSPRocketConfig extends Config(
  new gemmini.GemminiEE290Lab3SmallSPConfig ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// BOOM-based

class GemminiEE290Lab3BoomConfig extends Config(
  new gemmini.GemminiEE290Lab3Config ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiEE290Lab3SmallSPBoomConfig extends Config(
  new gemmini.GemminiEE290Lab3SmallSPConfig ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)
