package example

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// BOOM Configs
// ---------------------

class SmallBoomConfig extends Config(
  new WithSimBlockDeviceTop ++                              // use top with block-device IOs and connect to simblockdevice
  new testchipip.WithBlockDevice ++                         // add block-device module to periphery bus
  new WithBootROM ++                                        // use testchipip bootrom
  new freechips.rocketchip.subsystem.WithExtMemSize(((1 << 20) * 64)) ++
  new WithUART ++                                           // add a UART
  new freechips.rocketchip.subsystem.WithInclusiveCache ++  // use SiFive L2 cache
  new boom.common.WithSmallBooms ++                         // 1-wide BOOM
  new boom.common.WithNBoomCores(1) ++                      // single-core
  new freechips.rocketchip.system.BaseConfig)               // "base" rocketchip system

class MediumBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithExtMemSize(((1 << 20) * 64)) ++
  new WithUART ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithMediumBooms ++                        // 2-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class LargeBoomConfig extends Config(
  new WithSimBlockDeviceTop ++                             // use top with block-device IOs and connect to simblockdevice
  new testchipip.WithBlockDevice ++                        // add block-device module to periphery bus
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithExtMemSize(((1 << 20) * 64)) ++
  new WithUART ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithLargeBooms ++                         // 3-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class MegaBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new WithUART ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithMegaBooms ++                          // 4-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class DualSmallBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new WithUART ++                                          // add a UART
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithSmallBooms ++
  new boom.common.WithNBoomCores(2) ++                      // dual-core
  new freechips.rocketchip.system.BaseConfig)

class SmallRV32BoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new WithUART ++                                          // add a UART
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new boom.common.WithoutBoomFPU ++                        // no fp
  new boom.common.WithBoomRV32 ++                          // rv32 (32bit)
  new boom.common.WithSmallBooms ++
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class HwachaLargeBoomConfig extends Config(
  new WithTop ++
  new WithBootROM ++
  new WithUART ++                                          // add a UART
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new hwacha.DefaultHwachaConfig ++                         // use Hwacha vector accelerator
  new boom.common.WithLargeBooms ++                         // 3-wide BOOM
  new boom.common.WithNBoomCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
