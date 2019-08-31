package example

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// BOOM Configs
// ---------------------

class SmallBoomConfig extends Config(
  new WithTop ++                                            // use normal top
  new WithBootROM ++                                        // use testchipip bootrom
  new freechips.rocketchip.subsystem.WithInclusiveCache ++  // use SiFive L2 cache
  new boom.common.WithSmallBooms ++                         // 1-wide BOOM
  new boom.common.WithNBoomCores(1) ++                      // single-core
  new freechips.rocketchip.system.BaseConfig)               // "base" rocketchip system

class MediumBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithMediumBooms ++                        // 2-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class LargeBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithLargeBooms ++                         // 3-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class MegaBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithMegaBooms ++                          // 4-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class DualSmallBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithSmallBooms ++
  new boom.common.WithNBoomCores(2) ++                      // dual-core
  new freechips.rocketchip.system.BaseConfig)

class SmallRV32UnifiedBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithoutBoomFPU ++                       // no floating point
  new boom.common.WithUnifiedMemIntIQs ++                 // use unified mem+int issue queues
  new boom.common.WithSmallBooms ++
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class LoopbackNICBoomConfig extends Config(
  new WithIceNIC ++
  new WithLoopbackNICTop ++
  new MediumBoomConfig)

class DRAMCacheBoomConfig extends Config(
  new WithIceNIC ++
  new WithDRAMCache(
    sizeKB = 112, nTrackersPerBank = 4, nBanksPerChannel = 2) ++
  new WithMemBlade ++
  new WithPrefetchRoCC ++
  new WithDRAMCacheTop ++
  new LargeBoomConfig)
