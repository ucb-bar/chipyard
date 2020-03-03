package firesim.firesim

import chisel3._

import freechips.rocketchip.config._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._

import freechips.rocketchip.devices.tilelink.PLICKey

import firesim.configs._

// DO NOT MODIFY
class CS152RocketChipDefaultConfig extends Config(
//new WithInclusiveCache(capacityKB = 128) ++
  new FRFCFS16GBQuadRank ++
  new WithBootROM ++
  new WithoutPLIC ++
  new WithSerial ++
  new WithPerfCounters ++
  new WithoutClockGating ++
  new WithDefaultMemModel ++
  new WithCS152FireSimBridges ++
  new freechips.rocketchip.system.DefaultConfig)

// This omits the Platform-Level Interrupt Controller since no devices
// with external interrupts are present
class WithoutPLIC extends Config((site, here, up) => {
  case PLICKey => None
})


/**********************************************************************
 * Directed Portion                                                   *
 **********************************************************************/

class CS152RocketChipConfig extends Config(
  new WithL1ICacheSets(64) ++
  new WithL1ICacheWays(1) ++
  new WithL1DCacheSets(64) ++
  new WithL1DCacheWays(1) ++
  new CS152RocketChipDefaultConfig ++
  new WithCacheBlockBytes(64))

class CS152RocketChipL2Config extends Config(
  new WithInclusiveCache(nBanks = 1, nWays = 8, capacityKB = 64) ++
  new CS152RocketChipConfig)


/**********************************************************************
 * Open-Ended Problem 4.1                                             *
 **********************************************************************/

class CS152RocketChipMysteryConfig extends Config(
  new cs152.lab2.WithSecrets ++
  new CS152RocketChipDefaultConfig)


/**********************************************************************
 * Open-Ended Problem 4.2                                             *
 **********************************************************************/

// Baseline TARGET_CONFIG with prefetching disabled
class CS152RocketChipNoPrefetchConfig extends Config(
  new WithNonblockingL1(nMSHRs = 1) ++  // Use non-blocking L1D
  new CS152RocketChipDefaultConfig)

// Evaluation TARGET_CONFIG with prefetching enabled
class CS152RocketChipPrefetchConfig extends Config(
  new WithL1Prefetcher ++               // Enable L1 prefetcher
  new CS152RocketChipNoPrefetchConfig)

// TODO: Replace the module instantiation with your own
// e.g., CustomL1Prefetcher, ModelL1Prefetcher
class WithL1Prefetcher extends Config((site, here, up) => {
  case BuildL1Prefetcher => Some((p: Parameters) => Module(new ExampleL1Prefetcher()(p)))
})


