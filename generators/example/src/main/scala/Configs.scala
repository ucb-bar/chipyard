package example

import chisel3._

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.subsystem.{WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32, WithExtMemSize, WithNBanks}

import testchipip._

// --------------
// Rocket Configs
// --------------

/**
 * Base config that points to `rebar` bootrom files and sets default Rocket parameters
 */
class BaseRocketConfig extends Config(
  new WithBootROM ++
  new freechips.rocketchip.system.DefaultConfig)

/**
 * Default config to setup Rocket parameters and add the correct top-level system
 */
class DefaultRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new BaseRocketConfig)

/**
 * Config to setup Rocket parameters and add a default Hwacha
 */
class HwachaConfig extends Config(
  new hwacha.DefaultHwachaConfig ++
  new DefaultRocketConfig)

/**
 * Config to setup Rocket parameters and add a default example RoCC accelerator
 */
class RoccRocketConfig extends Config(
  new WithRoccExample ++
  new DefaultRocketConfig)

/**
 * Config to setup Rocket parameters and add PWM device/system
 */
class PWMRocketConfig extends Config(
  new WithPWMBoomRocketTop ++
  new BaseRocketConfig)

/**
 * Config to setup Rocket parameters and add PWM device with a AXI4 interface
 */
class PWMAXI4RocketConfig extends Config(
  new WithPWMAXI4BoomRocketTop ++
  new BaseRocketConfig)

/**
 * Config to setup Rocket parameters and add a simulated block device
 */
class SimBlockDeviceRocketConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceBoomRocketTop ++
  new BaseRocketConfig)

/**
 * Config to setup Rocket parameters and add a block device
 */
class BlockDeviceModelRocketConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelBoomRocketTop ++
  new BaseRocketConfig)

/**
 * Config to setup Rocket parameters and add GPIO ports/system
 */
class GPIORocketConfig extends Config(
  new WithGPIO ++
  new WithGPIOBoomRocketTop ++
  new BaseRocketConfig)

/**
 * Config to setup Rocket parameters for a dual core Rocket system
 */
class DualCoreRocketConfig extends Config(
  new WithNBigCores(2) ++
  new DefaultRocketConfig)

/**
 * Config to setup Rocket parameters and enable RV32
 */
class RV32RocketConfig extends Config(
  new WithRV32 ++
  new DefaultRocketConfig)

/**
 * Config to setup Rocket parameters and add a large external memory
 */
class GB1MemoryConfig extends Config(
  new WithExtMemSize((1<<30) * 1L) ++
  new DefaultRocketConfig)

// ------------
// BOOM Configs
// ------------

/**
 * Base config that points to `rebar` bootrom files and sets default BOOM parameters
 */
class BaseBoomConfig extends Config(
  new WithBootROM ++
  new boom.system.BoomConfig)

/**
 * Base config that points to `rebar` bootrom files and sets small default BOOM parameters
 */
class SmallBaseBoomConfig extends Config(
  new WithBootROM ++
  new boom.system.SmallBoomConfig)

/**
 * Default config to setup BOOM parameters and add the correct top-level system
 */
class DefaultBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new BaseBoomConfig)

/**
 * Default config to setup small BOOM parameters and add the correct top-level system
 */
class SmallDefaultBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new SmallBaseBoomConfig)

/**
 * Config to setup BOOM parameters and add a default Hwacha
 */
class HwachaBoomConfig extends Config(
  new hwacha.DefaultHwachaConfig ++
  new DefaultBoomConfig)

/**
 * Config to setup BOOM parameters and add a default example RoCC accelerator
 */
class RoccBoomConfig extends Config(
  new WithRoccExample ++
  new DefaultBoomConfig)

/**
 * Config to setup BOOM parameters and add PWM device/system
 */
class PWMBoomConfig extends Config(
  new WithPWMBoomRocketTop ++
  new BaseBoomConfig)

/**
 * Config to setup BOOM parameters and add PWM device with a AXI4 interface
 */
class PWMAXI4BoomConfig extends Config(
  new WithPWMAXI4BoomRocketTop ++
  new BaseBoomConfig)

/**
 * Config to setup BOOM parameters and add a simulated block device
 */
class SimBlockDeviceBoomConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceBoomRocketTop ++
  new BaseBoomConfig)

/**
 * Config to setup BOOM parameters and add a block device
 */
class BlockDeviceModelBoomConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelBoomRocketTop ++
  new BaseBoomConfig)

/**
 * Config to setup BOOM parameters and add GPIO ports/system
 */
class GPIOBoomConfig extends Config(
  new WithGPIO ++
  new WithGPIOBoomRocketTop ++
  new BaseBoomConfig)

/**
 * Slightly different looking configs since we need to override
 * the `WithNBoomCores` with the DefaultBoomConfig params
 */

/**
 * Config to setup BOOM parameters for a dual core BOOM system
 */
class DualCoreBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.system.BaseConfig)

/**
 * Config to setup BOOM parameters for a dual core small BOOM system
 */
class DualCoreSmallBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.common.WithRVC ++
  new boom.common.WithSmallBooms ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.system.BaseConfig)

/**
 * Config to setup BOOM parameters for a RV32 Unified Issue Queue BOOM system
 */
class RV32UnifiedBoomConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.system.SmallRV32UnifiedBoomConfig)

// -----------------------
// BOOM and Rocket Configs
// -----------------------

/**
 * Base config that points to `rebar` bootrom files and sets default BOOM and Rocket parameters
 */
class BaseBoomAndRocketConfig extends Config(
  new WithBootROM ++
  new boom.system.WithRenumberHarts ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

/**
 * Base config that points to `rebar` bootrom files and sets small default BOOM and Rocket parameters
 */
class SmallBaseBoomAndRocketConfig extends Config(
  new WithBootROM ++
  new boom.system.WithRenumberHarts ++
  new boom.common.WithRVC ++
  new boom.common.WithSmallBooms ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

/**
 * Default config to setup BOOM and Rocket parameters and add the correct top-level system
 */
class DefaultBoomAndRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new BaseBoomAndRocketConfig)

/**
 * Default config to setup small BOOM and Rocket parameters and add the correct top-level system
 */
class SmallDefaultBoomAndRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new SmallBaseBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add a default Hwacha to both cores
 */
class HwachaBoomAndRocketConfig extends Config(
  new hwacha.DefaultHwachaConfig ++
  new DefaultBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add a default example RoCC accelerators to both cores
 */
class RoccBoomAndRocketConfig extends Config(
  new WithRoccExample ++
  new DefaultBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add PWM device/system
 */
class PWMBoomAndRocketConfig extends Config(
  new WithPWMBoomRocketTop ++
  new BaseBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add PWM device with a AXI4 interface
 */
class PWMAXI4BoomAndRocketConfig extends Config(
  new WithPWMAXI4BoomRocketTop ++
  new BaseBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add a simulated block device
 */
class SimBlockDeviceBoomAndRocketConfig extends Config(
  new WithBlockDevice ++
  new WithSimBlockDeviceBoomRocketTop ++
  new BaseBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add a block device
 */
class BlockDeviceModelBoomAndRocketConfig extends Config(
  new WithBlockDevice ++
  new WithBlockDeviceModelBoomRocketTop ++
  new BaseBoomAndRocketConfig)

/**
 * Config to setup BOOM and Rocket parameters and add GPIO ports/system
 */
class GPIOBoomAndRocketConfig extends Config(
  new WithGPIO ++
  new WithGPIOBoomRocketTop ++
  new BaseBoomAndRocketConfig)

/**
 * Slightly different looking configs since we need to override
 * the `WithNBoomCores` with the DefaultBoomConfig params and
 * renumber the hartIds of each core.
 */

/**
 * Config to setup dual core BOOM parameters and single core Rocket parameters for a triple core BOOM and Rocket system
 */
class DualCoreBoomAndOneRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.system.WithRenumberHarts ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

/**
 * Config to setup dual core BOOM parameters and single core Rocket with a Hwacha parameters for a triple core BOOM + (Rocket + Hwacha) system.
 * Note: This uses MultiRoCC to specify which core will get the Hwacha accelerator attached.
 */
class DualCoreBoomAndOneHwachaRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new WithMultiRoCC ++
  new WithMultiRoCCHwacha(0) ++ // put Hwacha just on hart0 which was renumbered to Rocket
  new boom.system.WithRenumberHarts ++
  new hwacha.DefaultHwachaConfig ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

/**
 * Config to setup BOOM parameters for a RV32 Unified Issue Queue BOOM with a RV32 Rocket
 */
class RV32BoomAndRocketConfig extends Config(
  new WithNormalBoomRocketTop ++
  new WithBootROM ++
  new boom.system.WithRenumberHarts ++
  new boom.common.WithBoomRV32 ++
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithRV32 ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
