package chipyard

import freechips.rocketchip.config.{Config}

// ---------------------
// Heterogenous Configs
// ---------------------

class LargeBoomAndRocketConfig extends Config(
  new boom.common.WithNLargeBooms(1) ++                          // single-core boom
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

// DOC include start: BoomAndRocketWithHwacha
class HwachaLargeBoomAndHwachaRocketConfig extends Config(
  new hwacha.DefaultHwachaConfig ++                          // add hwacha to all harts
  new boom.common.WithNLargeBooms(1) ++                      // add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++     // add 1 rocket core
  new chipyard.config.AbstractConfig)
// DOC include end: BoomAndRocketWithHwacha

// DOC include start: DualBoomAndRocketOneHwacha
class LargeBoomAndHwachaRocketConfig extends Config(
  new chipyard.config.WithMultiRoCC ++                                  // support heterogeneous rocc
  new chipyard.config.WithMultiRoCCHwacha(1) ++                         // put hwacha on hart-1 (rocket)
  new hwacha.DefaultHwachaConfig ++                                     // set default hwacha config keys
  new boom.common.WithNLargeBooms(1) ++                                 // add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                // add 1 rocket core
  new chipyard.config.AbstractConfig)
// DOC include end: DualBoomAndRocketOneHwacha


// DOC include start: DualBoomAndRocket
class DualLargeBoomAndDualRocketConfig extends Config(
  new boom.common.WithNLargeBooms(2) ++                   // add 2 boom cores
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++  // add 2 rocket cores
  new chipyard.config.AbstractConfig)
// DOC include end: DualBoomAndRocket

class LargeBoomAndRocketWithControlCoreConfig extends Config(
  new freechips.rocketchip.subsystem.WithNSmallCores(1) ++ // Add a small "control" core
  new boom.common.WithNLargeBooms(1) ++                    // Add 1 boom core
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++   // add 1 rocket core
  new chipyard.config.AbstractConfig)
