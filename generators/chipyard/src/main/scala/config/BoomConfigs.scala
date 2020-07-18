package chipyard

import freechips.rocketchip.config.{Config}

// ---------------------
// BOOM Configs
// ---------------------

class SmallBoomConfig extends Config(
  new boom.common.WithNSmallBooms(1) ++                          // small boom config
  new chipyard.config.AbstractConfig)

class MediumBoomConfig extends Config(
  new boom.common.WithNMediumBooms(1) ++                         // medium boom config
  new chipyard.config.AbstractConfig)

class LargeBoomConfig extends Config(
  new boom.common.WithNLargeBooms(1) ++                          // large boom config
  new chipyard.config.AbstractConfig)

class MegaBoomConfig extends Config(
  new boom.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.AbstractConfig)

class DualSmallBoomConfig extends Config(
  new boom.common.WithNSmallBooms(2) ++                          // 2 boom cores
  new chipyard.config.AbstractConfig)

class HwachaLargeBoomConfig extends Config(
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class LoopbackNICLargeBoomConfig extends Config(
  new chipyard.iobinders.WithLoopbackNIC ++                      // drive NIC IOs with loopback
  new icenet.WithIceNIC ++                                       // build a NIC
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class DromajoBoomConfig extends Config(
  new chipyard.iobinders.WithSimDromajoBridge ++                 // attach Dromajo
  new chipyard.config.WithTraceIO ++                             // enable the traceio
  new boom.common.WithNSmallBooms(1) ++
  new chipyard.config.AbstractConfig)

class BoomRemoteMemClientConfig extends Config(
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithRemoteMemClientKey ++
  new chipyard.config.WithRemoteMemClientSystem ++
  new chipyard.config.WithStandardL2(4) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class BoomDRAMCacheConfig extends Config(
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithDRAMCacheKey(8, 8, 2) ++
  new chipyard.config.WithDRAMCacheSystem ++
  new chipyard.config.WithStandardL2(4) ++
  new memblade.prefetcher.WithPrefetchMiddleManTopology ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class BoomHwachaDRAMCacheConfig extends Config(
  new chipyard.config.WithHwachaNVMTEntries(64) ++
  new chipyard.config.WithHwachaConfPrec ++
  new hwacha.WithNLanes(2) ++
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithDRAMCacheKey(8, 8, 2) ++
  new chipyard.config.WithDRAMCacheSystem ++
  new chipyard.config.WithStandardL2(4) ++
  new memblade.prefetcher.WithPrefetchMiddleManTopology ++
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class DualBoomDRAMCacheConfig extends Config(
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithDRAMCacheKey(8, 8, 2) ++
  new chipyard.config.WithDRAMCacheSystem ++
  new chipyard.config.WithStandardL2(4) ++
  new memblade.prefetcher.WithPrefetchMiddleManTopology ++
  new boom.common.WithNLargeBooms(2) ++
  new chipyard.config.AbstractConfig)

class DualLargeBoomConfig extends Config(
  new chipyard.config.WithStandardL2(4) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new boom.common.WithNLargeBooms(2) ++
  new chipyard.config.AbstractConfig)
