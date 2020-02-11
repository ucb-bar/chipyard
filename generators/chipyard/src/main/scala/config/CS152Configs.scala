package chipyard

import chisel3._

import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._

// Do not modify
class CS152AbstractConfig extends Config(
  new chipyard.config.WithNPerfCounters ++
  new chipyard.config.WithBroadcastManager ++ // remove L2
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusFrequencyAsDefault ++
  new chipyard.config.WithSystemBusFrequency(500.0) ++
  new chipyard.config.WithMemoryBusFrequency(500.0) ++
  new chipyard.config.WithPeripheryBusFrequency(500.0) ++
  new chipyard.config.AbstractConfig)

/**********************************************************************
 * Directed Portion
 **********************************************************************/

class CS152RocketConfig extends Config(
  new WithL1ICacheSets(64) ++
  new WithL1ICacheWays(1) ++
  new WithL1DCacheSets(64) ++
  new WithL1DCacheWays(1) ++
  new CS152AbstractConfig ++
  new WithCacheBlockBytes(64))

class CS152RocketL2Config extends Config(
  new WithInclusiveCache(nBanks = 1, nWays = 8, capacityKB = 64) ++
  new CS152RocketConfig)

/**********************************************************************
 * Open-Ended Problem 4.1
 **********************************************************************/

class CS152RocketMysteryConfig extends Config(
  new cs152.lab2.WithSecrets ++
  new CS152AbstractConfig)

/**********************************************************************
 * Open-Ended Problem 4.2
 **********************************************************************/

// Baseline CONFIG with prefetching disabled
class CS152RocketNoPrefetchConfig extends Config(
  new freechips.rocketchip.subsystem.WithNonblockingL1(nMSHRs = 4) ++ // use non-blocking L1D
  new freechips.rocketchip.subsystem.WithNBanks(2) ++ // increase number of broadcast hub trackers
  new CS152AbstractConfig)

// Evaluation CONFIG with prefetching enabled
class CS152RocketPrefetchConfig extends Config(
  new WithL1Prefetcher ++               // enable L1 prefetcher
  new CS152RocketNoPrefetchConfig)

// TODO: Replace the module instantiation with your own
// e.g., CustomL1Prefetcher, ModelL1Prefetcher
class WithL1Prefetcher extends Config((site, here, up) => {
  case BuildL1Prefetcher => Some((p: Parameters) => Module(new ExampleL1Prefetcher()(p)))
})
