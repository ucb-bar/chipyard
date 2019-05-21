package example

import chisel3._
import freechips.rocketchip.config.{Config}
import freechips.rocketchip.subsystem.{WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32, WithExtMemSize, WithNBanks}
import testchipip._

// --------------
// Rocket Configs
// --------------

class BaseRocketConfig extends Config(
  new WithBootROM ++
  new freechips.rocketchip.system.DefaultConfig)

class DefaultRocketConfig extends Config(
  new WithNormalRocketTop ++
  new BaseRocketConfig)

class HwachaConfig extends Config(
  new hwacha.DefaultHwachaConfig ++
  new DefaultRocketConfig)

class RoccRocketConfig extends Config(
  new WithRoccExample ++
  new DefaultRocketConfig)

class PWMRocketConfig extends Config(
  new WithPWMRocketTop ++
  new BaseRocketConfig)

class PWMAXI4RocketConfig extends Config(
  new WithPWMAXI4RocketTop ++
  new BaseRocketConfig)

class SimBlockDeviceRocketConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceRocketTop ++
  new BaseRocketConfig)

class BlockDeviceModelRocketConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelRocketTop ++
  new BaseRocketConfig)

class DualCoreRocketConfig extends Config(
  new WithNBigCores(2) ++
  new DefaultRocketConfig)

class RV32RocketConfig extends Config(
  new WithRV32 ++
  new DefaultRocketConfig)

class GPIORocketConfig extends Config(
  new WithGPIO ++
  new WithGPIORocketTop ++
  new BaseRocketConfig)

class GB1MemoryConfig extends Config(
  new WithExtMemSize((1<<30) * 1L) ++
  new DefaultRocketConfig)

// ------------
// BOOM Configs
// ------------

class BaseBoomConfig extends Config(
  new WithBootROM ++
  new boom.system.BoomConfig)

class SmallBaseBoomConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallBoomConfig)

class DefaultBoomConfig extends Config(
  new WithNormalBoomTop ++
  new BaseBoomConfig)

class SmallDefaultBoomConfig extends Config(
  new WithNormalBoomTop ++
  new SmallBaseBoomConfig)

class HwachaBoomConfig extends Config(
  new hwacha.DefaultHwachaConfig ++
  new DefaultBoomConfig)

class RoccBoomConfig extends Config(
  new WithRoccExample ++
  new DefaultBoomConfig)

class PWMBoomConfig extends Config(
  new WithPWMBoomTop ++
  new BaseBoomConfig)

class PWMAXI4BoomConfig extends Config(
  new WithPWMAXI4BoomTop ++
  new BaseBoomConfig)

class SimBlockDeviceBoomConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceBoomTop ++
  new BaseBoomConfig)

class BlockDeviceModelBoomConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelBoomTop ++
  new BaseBoomConfig)

/**
 * Slightly different looking configs since we need to override
 * the `WithNBoomCores` with the DefaultBoomConfig params
 */
class DualCoreBoomConfig extends Config(
  new WithNormalBoomTop ++
  new WithBootROM ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.system.BaseConfig)

class DualCoreSmallBoomConfig extends Config(
  new WithNormalBoomTop ++
  new WithBootROM ++
  new boom.common.WithRVC ++
  new boom.common.WithSmallBooms ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.system.BaseConfig)

class RV32BoomConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallRV32UnifiedBoomConfig)

class GPIOBoomConfig extends Config(
  new WithGPIO ++
  new WithGPIOBoomTop ++
  new BaseBoomConfig)

// ---------------------
// BOOM + Rocket Configs
// ---------------------

//class BaseRocketConfig extends Config(
//  new WithBootROM ++
//  new freechips.rocketchip.system.DefaultConfig)
//
//class DefaultRocketConfig extends Config(
//  new WithNormalRocketTop ++
//  new BaseRocketConfig)
//
//class BaseConfig extends Config(
//  new WithDefaultMemPort() ++
//  new WithDefaultMMIOPort() ++
//  new WithDefaultSlavePort() ++
//  new WithTimebase(BigInt(1000000)) ++ // 1 MHz
//  new WithDTS("freechips,rocketchip-unknown", Nil) ++
//  new WithNExtTopInterrupts(2) ++
//  new BaseSubsystemConfig()
//)
//
//class DefaultConfig extends Config(new WithNBigCores(1) ++ new BaseConfig)
//
////boom
//  new WithRVC ++
//  new DefaultBoomConfig ++
//  new WithNBoomCores(1) ++
//  new WithoutTLMonitors ++
//  new freechips.rocketchip.system.BaseConfig)

class BaseBoomAndRocketConfig extends Config(
  new WithBootROM ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

//class BaseBoomAndRocketConfig extends Config(
//  new WithBootROM ++
//  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
//  new boom.system.BoomConfig)

class SmallBaseBoomAndRocketConfig extends Config(
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new boom.system.SmallBoomConfig)

class DefaultBoomAndRocketConfig extends Config(
  new WithNormalBoomAndRocketTop ++
  new BaseBoomAndRocketConfig)

class SmallDefaultBoomAndRocketConfig extends Config(
  new WithNormalBoomAndRocketTop ++
  new SmallBaseBoomAndRocketConfig)

class HwachaBoomAndRocketConfig extends Config(
  new hwacha.DefaultHwachaConfig ++
  new DefaultBoomAndRocketConfig)

class RoccBoomAndRocketConfig extends Config(
  new WithRoccExample ++
  new DefaultBoomAndRocketConfig)

class PWMBoomAndRocketConfig extends Config(
  new WithPWMBoomAndRocketTop ++
  new BaseBoomAndRocketConfig)

class PWMAXI4BoomAndRocketConfig extends Config(
  new WithPWMAXI4BoomAndRocketTop ++
  new BaseBoomAndRocketConfig)

class SimBlockDeviceBoomAndRocketConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceBoomAndRocketTop ++
  new BaseBoomAndRocketConfig)

class BlockDeviceModelBoomAndRocketConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelBoomAndRocketTop ++
  new BaseBoomAndRocketConfig)

class DualCoreBoomAndOneRocketConfig extends Config(
  // Core gets tacked onto existing list
  new boom.system.WithNBoomCores(2) ++
  new DefaultBoomAndRocketConfig)

class RV32BoomAndNormalRocketConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallRV32UnifiedBoomConfig)

class GPIOBoomAndRocketConfig extends Config(
  new WithGPIO ++
  new WithGPIOBoomAndRocketTop ++
  new BaseBoomAndRocketConfig)
