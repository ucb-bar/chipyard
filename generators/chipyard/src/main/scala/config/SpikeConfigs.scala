package chipyard

import org.chipsalliance.cde.config.{Config}
// import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet, XLen}

// Configs which instantiate a Spike-simulated
// tile that interacts with the Chipyard SoC
// as a hardware core would

class SpikeConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  // new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class SpikeZicntrConfig extends Config(
  new chipyard.WithSpikeZicntr ++
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.AbstractConfig)

class SpikeAdderExampleConfig extends Config(
  new chipyard.WithSpikeZicntr ++
  new chipyard.WithAdderRoCC ++
  new SpikeConfig)

class SpikeAccumExampleConfig extends Config(
  new chipyard.WithSpikeZicntr ++
  new chipyard.WithAccumRoCC ++
  new SpikeConfig)

class SpikeCharCountExampleConfig extends Config(
  new chipyard.WithSpikeZicntr ++
  new chipyard.WithCharCountRoCC ++
  new SpikeConfig)

class SpikeEE290L1BMMRoCCConfig extends Config(
  new chipyard.WithSpikeZicntr ++
  new ee290.WithEE290RoCCAccelWithCacheBlackBox ++
  new SpikeConfig)

class SpikeGemminiConfig extends Config(
  new chipyard.WithSpikeZicntr ++
  new gemmini.DefaultGemminiConfig ++                            // use Gemmini systolic array GEMM accelerator
  new SpikeConfig)

class dmiSpikeConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tilelink
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new SpikeConfig)

// Avoids polling on the UART registers
class SpikeFastUARTConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUART(txEntries=128, rxEntries=128) ++   // Spike sim requires a larger UART FIFO buffer,
  new chipyard.config.WithNoUART() ++                             // so we overwrite the default one
  new chipyard.config.WithUniformBusFrequencies(2) ++               // configured to be as fast as possible
  new chipyard.config.AbstractConfig)

// No L2 and a ludicrous L1D
class SpikeUltraFastConfig extends Config(
  new testchipip.soc.WithNoScratchpads ++
  new chipyard.WithSpikeTCM ++
  new chipyard.config.WithBroadcastManager ++
  new SpikeFastUARTConfig)

class dmiSpikeUltraFastConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tilelink
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new SpikeUltraFastConfig)

class dmiCheckpointingSpikeUltraFastConfig extends Config(
  new chipyard.config.WithNPMPs(0) ++                            // remove PMPs (reduce non-core arch state)
  new dmiSpikeUltraFastConfig)

// Add the default firechip devices
class SpikeUltraFastDevicesConfig extends Config(
  new chipyard.harness.WithSimBlockDevice ++
  new chipyard.harness.WithLoopbackNIC ++
  new icenet.WithIceNIC ++
  new testchipip.iceblk.WithBlockDevice ++
  new SpikeUltraFastConfig)
