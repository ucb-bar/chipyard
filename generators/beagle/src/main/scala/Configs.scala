package beagle

import chisel3._

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{ExtMem, RocketTilesKey, BankedL2Key, WithJtagDTM, WithNMemoryChannels, WithNBanks, SystemBusKey, MemoryBusKey, ControlBusKey, CacheBlockBytes}
import freechips.rocketchip.diplomacy.{LazyModule, ValName, AddressSet}
import freechips.rocketchip.tile.{LazyRoCC, BuildRoCC, OpcodeSet, TileKey, RocketTileParams}
import freechips.rocketchip.rocket.{RocketCoreParams, BTBParams, DCacheParams, ICacheParams, MulDivParams}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

import hbwif.tilelink._
import hbwif._

import hwacha.{Hwacha}

import boom.system.{BoomTilesKey}

import systolic.{SystolicArray, SystolicArrayKey, SystolicArrayConfig, Dataflow}

// -------
// CONFIGS
// -------

/**
 * Heterogeneous (BOOM + Rocket)
 */
class BeagleBoomAndRocketNoHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleSimChanges ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  new boom.system.WithRenumberHarts ++
  // make tiles have different clocks
  new boom.system.WithAsynchronousBoomTiles(4, 4) ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(4, 4) ++
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
  new WithBeagleSimChanges ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  new boom.system.WithRenumberHarts ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // make tiles have different clocks
  new boom.system.WithAsynchronousBoomTiles(4, 4) ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(4, 4) ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous ((BOOM + Hwacha) + (Rocket + Systolic))
 *
 * Note: ORDER OF MIXINS MATTERS
 */
class BeagleConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++

  // note: THIS MUST BE ABOVE hwacha.DefaultHwachaConfig TO WORK
  new example.WithMultiRoCC ++ // attach particular RoCC accelerators based on the hart
  new example.WithMultiRoCCHwacha(1) ++ // add a hwacha to just boom
  new WithMultiRoCCSystolic(0) ++ // add a systolic to just rocket
  new boom.system.WithRenumberHarts ++ // renumber harts with boom starting at 0 then rocket

  // systolic parameter setup mixins
  new WithSystolicParams ++
  // hwacha parameter setup mixins
  new hwacha.DefaultHwachaConfig ++

  // make tiles have different clocks
  new boom.system.WithAsynchronousBoomTiles(4, 4) ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(4, 4) ++

  // rocket mixins
  new WithMiniRocketCore ++

  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++

  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous ((BOOM + Hwacha) + (Rocket + Systolic))
 *
 * FOR FASTER SIMULATION
 *
 * Note: ORDER OF MIXINS MATTERS
 */
class BeagleSimConfig extends Config(
  // for faster simulation
  new WithBeagleSimChanges ++
  new BeagleConfig)

/**
 * Heterogeneous ((Mega BOOM + Hwacha) + (Rocket + Systolic))
 *
 * Note: ORDER OF MIXINS MATTERS
 */
class MegaBeagleConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++

  // note: THIS MUST BE ABOVE hwacha.DefaultHwachaConfig TO WORK
  new example.WithMultiRoCC ++ // attach particular RoCC accelerators based on the hart
  new example.WithMultiRoCCHwacha(1) ++ // add a hwacha to just boom
  new WithMultiRoCCSystolic(0) ++ // add a systolic to just rocket
  new boom.system.WithRenumberHarts ++ // renumber harts with boom starting at 0 then rocket

  // systolic parameter setup mixins
  new WithSystolicParams ++
  // hwacha parameter setup mixins
  new hwacha.DefaultHwachaConfig ++

  // make tiles have different clocks
  new boom.system.WithAsynchronousBoomTiles(4, 4) ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(4, 4) ++

  // rocket mixins
  new WithMiniRocketCore ++

  // boom mixins
  new boom.common.WithRVC ++
  new WithMegaBeagleBooms ++
  new boom.system.WithNBoomCores(1) ++

  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous ((Mega BOOM + Hwacha) + (Rocket + Systolic))
 *
 * FOR FASTER SIMULATION
 *
 * Note: ORDER OF MIXINS MATTERS
 */
class MegaBeagleSimConfig extends Config(
  // for faster simulation
  new WithBeagleSimChanges ++
  new MegaBeagleConfig)
