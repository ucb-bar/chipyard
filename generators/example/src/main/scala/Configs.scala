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

class SmallHwachaConfig extends Config(
  new WithNBanks(1) ++
  new hwacha.DefaultHwachaConfig ++
  new freechips.rocketchip.system.DefaultConfig)

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

class DualCoreBoomConfig extends Config(
  // Core gets tacked onto existing list
  new boom.system.WithNBoomCores(2) ++
  new DefaultBoomConfig)

class RV32BoomConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallRV32UnifiedBoomConfig)

class GPIOBoomConfig extends Config(
  new WithGPIO ++
  new WithGPIOBoomTop ++
  new BaseBoomConfig)
