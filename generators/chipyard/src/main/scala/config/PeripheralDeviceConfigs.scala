package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{MBUS}

// ---------------------------------------------------------
// Configs which add non-default peripheral devices or ports
// ---------------------------------------------------------

class LargeSPIFlashROMRocketConfig extends Config(
  new chipyard.harness.WithSimSPIFlashModel(true) ++        // add the SPI flash model in the harness (read-only)
  new chipyard.config.WithSPIFlash ++                       // add the SPI flash controller
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallSPIFlashRocketConfig extends Config(
  new chipyard.harness.WithSimSPIFlashModel(false) ++       // add the SPI flash model in the harness (writeable)
  new chipyard.config.WithSPIFlash(0x100000) ++             // add the SPI flash controller (1 MiB)
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SimBlockDeviceRocketConfig extends Config(
  new chipyard.harness.WithSimBlockDevice ++                // drive block-device IOs with SimBlockDevice
  new testchipip.WithBlockDevice ++                         // add block-device module to peripherybus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class BlockDeviceModelRocketConfig extends Config(
  new chipyard.harness.WithBlockDeviceModel ++              // drive block-device IOs with a BlockDeviceModel
  new testchipip.WithBlockDevice ++                         // add block-device module to periphery bus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: GPIORocketConfig
class GPIORocketConfig extends Config(
  new chipyard.config.WithGPIO ++                           // add GPIOs to the peripherybus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GPIORocketConfig

class LoopbackNICRocketConfig extends Config(
  new chipyard.harness.WithLoopbackNIC ++                      // drive NIC IOs with loopback
  new icenet.WithIceNIC ++                                     // add an IceNIC
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MMIORocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LBWIFRocketConfig extends Config(
  new testchipip.WithSerialTLMem(isMainMemory=true) ++      // set lbwif memory base to DRAM_BASE, use as main memory
  new freechips.rocketchip.subsystem.WithNoMemPort ++       // remove AXI4 backing memory
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: DmiRocket
class dmiRocketConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: DmiRocket

class ManyPeripheralsRocketConfig extends Config(
  new testchipip.WithBlockDevice ++                          // add block-device module to peripherybus
  new testchipip.WithOffchipBusClient(MBUS) ++               // OBUS provides backing memory to the MBUS
  new testchipip.WithOffchipBus ++                           // OBUS must exist for serial-tl to master off-chip memory
  new testchipip.WithSerialTLMem(isMainMemory=true) ++       // set lbwif memory base to DRAM_BASE, use as main memory
  new chipyard.harness.WithSimSPIFlashModel(true) ++         // add the SPI flash model in the harness (read-only)
  new chipyard.harness.WithSimBlockDevice ++                 // drive block-device IOs with SimBlockDevice
  new chipyard.config.WithSPIFlash ++                        // add the SPI flash controller
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new freechips.rocketchip.subsystem.WithNoMemPort ++        // remove AXI4 backing memory
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class QuadChannelRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNMemoryChannels(4) ++      // 4 AXI4 channels
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class UARTTSIRocketConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++
  new testchipip.WithUARTTSIClient ++
  new chipyard.config.WithMemoryBusFrequency(10) ++
  new chipyard.config.WithFrontBusFrequency(10) ++
  new chipyard.config.WithPeripheryBusFrequency(10) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)
