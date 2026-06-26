package chipyard

import org.chipsalliance.cde.config.{Config}

// ---------------------
// BOOM V3 Configs
// Performant, stable baseline
// ---------------------

class SmallBoomV3Config extends Config(
  new boom.v3.common.WithNSmallBooms(1) ++                          // small boom config
  new chipyard.config.AbstractConfig)

class MediumBoomV3Config extends Config(
  new boom.v3.common.WithNMediumBooms(1) ++                         // medium boom config
  new chipyard.config.AbstractConfig)

class LargeBoomV3Config extends Config(
  new boom.v3.common.WithNLargeBooms(1) ++                          // large boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomV3Config extends Config(
  new boom.v3.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomV3CommitLogConfig extends Config(
  new boom.v3.common.WithBoomCommitLogPrintf ++                     // enable instruction commit log printf
  new boom.v3.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomV3HumanCommitLogConfig extends Config(
  new boom.v3.common.WithBoomHumanReadableCommitLog ++              // commit log + DASM() tokens for spike-dasm
  new boom.v3.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomV3HumanCommitLogTMAConfig extends Config(
  new boom.v3.common.WithBoomHumanReadableCommitLog ++              // commit log + DASM() tokens for spike-dasm
  new boom.v3.common.WithBoomTMASimDump ++                          // DPI-C TMA dump at $finish (needs +dump-tma-counters)
  new boom.v3.common.WithBoomTMACounters ++                         // TMA counter hardware + MMIO
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MediumBoomV3HumanCommitLogTMAConfig extends Config(
  new boom.v3.common.WithBoomHumanReadableCommitLog ++              // commit log + DASM() tokens for spike-dasm
  new boom.v3.common.WithBoomTMASimDump ++                          // DPI-C TMA dump at $finish (needs +dump-tma-counters)
  new boom.v3.common.WithBoomTMACounters ++                         // TMA counter hardware + MMIO
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMediumBooms(1) ++                         // medium boom config
  new chipyard.config.AbstractConfig)

class SmallBoomV3HumanCommitLogTMAConfig extends Config(
  new boom.v3.common.WithBoomHumanReadableCommitLog ++              // commit log + DASM() tokens for spike-dasm
  new boom.v3.common.WithBoomTMASimDump ++                          // DPI-C TMA dump at $finish (needs +dump-tma-counters)
  new boom.v3.common.WithBoomTMACounters ++                         // TMA counter hardware + MMIO
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNSmallBooms(1) ++                          // small boom config
  new chipyard.config.AbstractConfig)

class LargeBoomV3HumanCommitLogTMAConfig extends Config(
  new boom.v3.common.WithBoomHumanReadableCommitLog ++              // commit log + DASM() tokens for spike-dasm
  new boom.v3.common.WithBoomTMASimDump ++                          // DPI-C TMA dump at $finish (needs +dump-tma-counters)
  new boom.v3.common.WithBoomTMACounters ++                         // TMA counter hardware + MMIO
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNLargeBooms(1) ++                          // large boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class DualSmallBoomV3Config extends Config(
  new boom.v3.common.WithNSmallBooms(2) ++                          // 2 boom cores
  new chipyard.config.AbstractConfig)

class Cloned64MegaBoomV3Config extends Config(
  new boom.v3.common.WithCloneBoomTiles(63, 0) ++
  new boom.v3.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class LoopbackNICLargeBoomV3Config extends Config(
  new chipyard.harness.WithLoopbackNIC ++                        // drive NIC IOs with loopback
  new icenet.WithIceNIC ++                                       // build a NIC
  new boom.v3.common.WithNLargeBooms(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MediumBoomV3CosimConfig extends Config(
  new chipyard.harness.WithCospike ++                            // attach spike-cosim
  new chipyard.config.WithTraceIO ++                             // enable the traceio
  new boom.v3.common.WithNMediumBooms(1) ++
  new chipyard.config.AbstractConfig)

class dmiCheckpointingMediumBoomV3Config extends Config(
  new chipyard.config.WithNPMPs(0) ++                            // remove PMPs (reduce non-core arch state)
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new boom.v3.common.WithNMediumBooms(1) ++
  new chipyard.config.AbstractConfig)

class dmiMediumBoomV3CosimConfig extends Config(
  new chipyard.harness.WithCospike ++                            // attach spike-cosim
  new chipyard.config.WithTraceIO ++                             // enable the traceio
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anythint to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new boom.v3.common.WithNMediumBooms(1) ++
  new chipyard.config.AbstractConfig)

class SimBlockDeviceMegaBoomV3Config extends Config(
  new chipyard.harness.WithSimBlockDevice ++                     // drive block-device IOs with SimBlockDevice
  new testchipip.iceblk.WithBlockDevice ++                       // add block-device module to peripherybus
  new boom.v3.common.WithNMegaBooms(1) ++                        // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

// ---------------------
// BOOM V3 TMA Configs
// ---------------------

class SmallBoomTMAConfig extends Config(
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNSmallBooms(1) ++
  new chipyard.config.AbstractConfig)

class MegaBoomTMAConfig extends Config(
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBooms(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class SmallBoomVerilatorTMAConfig extends Config(
  new boom.v3.common.WithBoomTMASimDump ++                             // DPI-C dump (Verilator/VCS only)
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNSmallBooms(1) ++
  new chipyard.config.AbstractConfig)

class SmallBoomVerilatorDataDepConfig extends Config(
  new boom.v3.common.WithBoomDataDepCounters ++
  new boom.v3.common.WithBoomTMASimDump ++
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNSmallBooms(1) ++
  new chipyard.config.AbstractConfig)

class SmallBoomVerilatorOOOConfig extends Config(
  new boom.v3.common.WithBoomOOOEngineCounters ++
  new boom.v3.common.WithBoomDataDepCounters ++
  new boom.v3.common.WithBoomTMASimDump ++
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNSmallBooms(1) ++
  new chipyard.config.AbstractConfig)

class MegaBoomVerilatorConfig extends Config(
  new boom.v3.common.WithBoomTMASimDump ++                             // DPI-C dump (Verilator/VCS only)
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBooms(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

//*****************************************************************
// Boom configs, for Chia
//*****************************************************************

class MegaBoomChiaConfig extends Config(
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBooms(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomChiaBigCacheConfig extends Config(
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBoomsBigCaches(1) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new freechips.rocketchip.subsystem.WithEdgeDataBits(256) ++
  new chipyard.config.WithInclusiveCacheWriteBytes(32) ++     // moved: overrides after WithInclusiveCache sets base
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(
    capacityKB = 1024,
    nWays = 8
  ) ++
  new chipyard.config.AbstractConfig)

class MegaBoomChiaVerilatorConfig extends Config(
  new boom.v3.common.WithBoomTMASimDump ++                             // DPI-C dump (Verilator/VCS only)
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBooms(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomChiaVerilatorBigCacheConfig extends Config(
  new boom.v3.common.WithBoomTMASimDump ++
  new boom.v3.common.WithBoomTMACounters ++
  new boom.v3.common.WithNBoomPerfCounters(16) ++
  new boom.v3.common.WithNMegaBoomsBigCaches(1) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new freechips.rocketchip.subsystem.WithEdgeDataBits(256) ++
  new chipyard.config.WithInclusiveCacheWriteBytes(32) ++     // moved: overrides after WithInclusiveCache sets base
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(
    capacityKB = 1024,
    nWays = 8
  ) ++
  new chipyard.config.AbstractConfig)

// ---------------------
// BOOM V4 Configs
// Less stable and performant, but with more advanced micro-architecture
// Use for PD exploration
// ---------------------

class SmallBoomV4Config extends Config(
  new boom.v4.common.WithNSmallBooms(1) ++                          // small boom config
  new chipyard.config.AbstractConfig)

class MediumBoomV4Config extends Config(
  new boom.v4.common.WithNMediumBooms(1) ++                         // medium boom config
  new chipyard.config.AbstractConfig)

class LargeBoomV4Config extends Config(
  new boom.v4.common.WithNLargeBooms(1) ++                          // large boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MegaBoomV4Config extends Config(
  new boom.v4.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class DualSmallBoomV4Config extends Config(
  new boom.v4.common.WithNSmallBooms(2) ++                          // 2 boom cores
  new chipyard.config.AbstractConfig)

class Cloned64MegaBoomV4Config extends Config(
  new boom.v4.common.WithCloneBoomTiles(63, 0) ++
  new boom.v4.common.WithNMegaBooms(1) ++                           // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MediumBoomV4CosimConfig extends Config(
  new chipyard.harness.WithCospike ++                            // attach spike-cosim
  new chipyard.config.WithTraceIO ++                             // enable the traceio
  new boom.v4.common.WithNMediumBooms(1) ++
  new chipyard.config.AbstractConfig)

class dmiCheckpointingMediumBoomV4Config extends Config(
  new chipyard.config.WithNPMPs(0) ++                            // remove PMPs (reduce non-core arch state)
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new boom.v4.common.WithNMediumBooms(1) ++
  new chipyard.config.AbstractConfig)

class dmiMediumBoomV4CosimConfig extends Config(
  new chipyard.harness.WithCospike ++                            // attach spike-cosim
  new chipyard.config.WithTraceIO ++                             // enable the traceio
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anythint to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new boom.v4.common.WithNMediumBooms(1) ++
  new chipyard.config.AbstractConfig)

class SimBlockDeviceMegaBoomV4Config extends Config(
  new chipyard.harness.WithSimBlockDevice ++                     // drive block-device IOs with SimBlockDevice
  new testchipip.iceblk.WithBlockDevice ++                       // add block-device module to peripherybus
  new boom.v4.common.WithNMegaBooms(1) ++                        // mega boom config
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)
