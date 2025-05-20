package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{MBUS}

// ---------------------------------------------------------
// Configs which add non-default peripheral devices or ports
// ---------------------------------------------------------

class LargeSPIFlashROMRocketConfig extends Config(
  new chipyard.harness.WithSimSPIFlashModel(true) ++        // add the SPI flash model in the harness (read-only)
  new chipyard.config.WithSPIFlash ++                       // add the SPI flash controller
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallSPIFlashRocketConfig extends Config(
  new chipyard.harness.WithSimSPIFlashModel(false) ++       // add the SPI flash model in the harness (writeable)
  new chipyard.config.WithSPIFlash(0x100000) ++             // add the SPI flash controller (1 MiB)
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class I2CRocketConfig extends Config(
  new chipyard.harness.WithI2CTiedOff ++                    // Tie off the I2C port in the harness
  new chipyard.config.WithI2C ++                            // Add I2C peripheral
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class SimBlockDeviceRocketConfig extends Config(
  new chipyard.harness.WithSimBlockDevice ++                // drive block-device IOs with SimBlockDevice
  new testchipip.iceblk.WithBlockDevice ++                  // add block-device module to peripherybus
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class BlockDeviceModelRocketConfig extends Config(
  new chipyard.harness.WithBlockDeviceModel ++              // drive block-device IOs with a BlockDeviceModel
  new testchipip.iceblk.WithBlockDevice ++                  // add block-device module to periphery bus
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: GPIORocketConfig
class GPIORocketConfig extends Config(
  new chipyard.config.WithGPIO ++                           // add GPIOs to the peripherybus
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GPIORocketConfig

class LoopbackNICRocketConfig extends Config(
  new chipyard.harness.WithLoopbackNIC ++                      // drive NIC IOs with loopback
  new icenet.WithIceNIC ++                                     // add an IceNIC
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class MMIORocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: DmiRocket
class dmiRocketConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: DmiRocket

class dmiCheckpointingRocketConfig extends Config(
  new freechips.rocketchip.rocket.WithCease(false) ++            // disable xrocket extension to match w/ spike
  new chipyard.config.WithNoUART ++                              // only use htif prints w/ checkpointing
  new chipyard.config.WithNPMPs(0) ++                            // remove PMPs (reduce non-core arch state)
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class dmiCospikeCheckpointingRocketConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tl
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new chipyard.harness.WithCospike ++                   // attach spike-cosim
  new chipyard.config.WithTraceIO ++                    // enable the traceio
  new chipyard.config.WithNPMPs(0) ++                   // remove PMPs (reduce non-core arch state)
  new freechips.rocketchip.rocket.WithDebugROB ++       // cospike needs wdata given by the unsynth. debug rom
  new freechips.rocketchip.rocket.WithCease(false) ++   // remove xrocket ISA extension
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)


class ManyPeripheralsRocketConfig extends Config(
  new chipyard.harness.WithI2CTiedOff ++                    // Tie off the I2C port in the harness
  new chipyard.harness.WithSimSPIFlashModel(true) ++         // add the SPI flash model in the harness (read-only)
  new chipyard.harness.WithSimBlockDevice ++                 // drive block-device IOs with SimBlockDevice

  new testchipip.iceblk.WithBlockDevice ++                   // add block-device module to peripherybus
  new testchipip.soc.WithOffchipBusClient(MBUS) ++           // OBUS provides backing memory to the MBUS
  new testchipip.soc.WithOffchipBus ++                       // OBUS must exist for serial-tl to master off-chip memory
  new testchipip.serdes.WithSerialTLMem(isMainMemory=true) ++ // set lbwif memory base to DRAM_BASE, use as main memory
  new chipyard.config.WithI2C ++                             // Add I2C peripheral
  new chipyard.config.WithPeripheryTimer ++                  // add the pwm timer device
  new chipyard.config.WithSPIFlash ++                        // add the SPI flash controller
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new freechips.rocketchip.subsystem.WithNoMemPort ++        // remove AXI4 backing memory
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class UARTTSIRocketConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++
  new testchipip.tsi.WithUARTTSIClient ++
  new chipyard.config.WithUniformBusFrequencies(2) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)
