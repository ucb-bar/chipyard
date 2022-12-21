package chipyard

import freechips.rocketchip.config.{Config}

// ---------------------
// Heterogenous Configs
// ---------------------

class LargeBoomAndRocketConfig extends Config(
  new boom.common.WithNLargeBooms(1) ++                          // single-core boom
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

// DOC include start: BoomAndRocketWithHwacha
class HwachaLargeBoomAndHwachaRocketConfig extends Config(
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                          // add hwacha to all harts
  new boom.common.WithNLargeBooms(1) ++                      // add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++     // add 1 rocket core
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)
// DOC include end: BoomAndRocketWithHwacha

class LargeBoomAndHwachaRocketConfig extends Config(
  new chipyard.config.WithMultiRoCC ++                                  // support heterogeneous rocc
  new chipyard.config.WithMultiRoCCHwacha(0) ++                         // put hwacha on hart-0 (rocket)
  new hwacha.DefaultHwachaConfig ++                                     // set default hwacha config keys
  new boom.common.WithNLargeBooms(1) ++                                 // add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                // add 1 rocket core
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

// DOC include start: DualBoomAndRocketOneHwacha
class DualLargeBoomAndHwachaRocketConfig extends Config(
  new chipyard.config.WithMultiRoCC ++                                  // support heterogeneous rocc
  new chipyard.config.WithMultiRoCCHwacha(0) ++                         // put hwacha on hart-0 (rocket)
  new hwacha.DefaultHwachaConfig ++                                     // set default hwacha config keys
  new boom.common.WithNLargeBooms(2) ++                                 // add 2 boom cores
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                // add 1 rocket core
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)
// DOC include end: DualBoomAndRocketOneHwacha

class DualLargeBoomAndDualRocketConfig extends Config(
  new boom.common.WithNLargeBooms(2) ++                   // add 2 boom cores
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++  // add 2 rocket cores
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

// DOC include start: DualBoomAndSingleRocket
class DualLargeBoomAndSingleRocketConfig extends Config(
  new boom.common.WithNLargeBooms(2) ++                   // add 2 boom cores
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++  // add 1 rocket core
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)
// DOC include end: DualBoomAndSingleRocket

class LargeBoomAndRocketWithControlCoreConfig extends Config(
  new freechips.rocketchip.subsystem.WithNSmallCores(1) ++ // Add a small "control" core
  new boom.common.WithNLargeBooms(1) ++                    // Add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++   // add 1 rocket core
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)
