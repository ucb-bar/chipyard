package beagle

import chisel3._

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{RocketTilesKey, WithJtagDTM, WithRationalRocketTiles, WithNMemoryChannels, WithNBanks, SystemBusKey, MemoryBusKey, ControlBusKey}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

class WithBeagleChanges extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
  case MemoryBusKey => up(MemoryBusKey).copy(beatBytes = 8)
  case ControlBusKey => {
    val cBus = up(ControlBusKey)
    cBus.copy(errorDevice = cBus.errorDevice.map(e => e.copy(maxTransfer=64)))
  }
  case BeaglePipelineResetDepth => 5
})

class WithBeagleSiFiveBlocks extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(GPIOParams(address = 0x9000, width = 16))
  case PeripherySPIKey => Seq(SPIParams(rAddress = 0xa000))
  case PeripheryI2CKey => Seq(I2CParams(address = 0xb000))
  case PeripheryUARTKey => Seq(UARTParams(address = 0xc000))
  case PeripheryBeagleKey => BeagleParams(scrAddress = 0x110000)
})

class WithHierTiles extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(boundaryBuffers = true) }
})

/**
 * Dual (Rocket + Hwacha)
 */
class BeagleDualRocketPlusHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Dual (BOOM + Hwacha)
 */
class BeagleDualBoomPlusHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous (BOOM + Rocket)
 */
class BeagleBoomAndRocketNoHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous ((BOOM + Hwacha) + (Rocket + Hwacha))
 */
class BeagleBoomAndRocketHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)
