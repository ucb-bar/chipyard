package chipyard

import org.chipsalliance.cde.config.{Config}
// import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet, XLen}

// Configs which instantiate a Spike-simulated
// tile that interacts with the Chipyard SoC
// as a hardware core would

class SpikeConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class SpikeAdderExampleConfig extends Config(
  new chipyard.WithAdderRoCC ++
  new SpikeConfig)

class SpikeAccumExampleConfig extends Config(
  new chipyard.WithAccumRoCC ++
  new SpikeConfig)

class SpikeCharCountExampleConfig extends Config(
  new chipyard.WithCharCountRoCC ++
  new SpikeConfig)

class SpikeEE290L1BMMRoCCConfig extends Config(
  new ee290.WithEE290RoCCAccelWithCacheBlackBox ++
  new SpikeConfig)

class SpikeGemminiConfig extends Config(
  new gemmini.DefaultGemminiConfig ++                            // use Gemmini systolic array GEMM accelerator
  new SpikeConfig)

class dmiSpikeConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tilelink
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new SpikeConfig)

// Avoids polling on the UART registers
class SpikeFastUARTConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUART(txEntries=128, rxEntries=128) ++ // Spike sim requires a larger UART FIFO buffer, 
  new chipyard.config.WithNoUART() ++                           // so we overwrite the default one
  new chipyard.config.WithMemoryBusFrequency(2) ++
  new chipyard.config.WithPeripheryBusFrequency(2) ++
  new chipyard.config.AbstractConfig)

// Makes the UART fast, also builds no L2 and a ludicrous L1D
class SpikeUltraFastConfig extends Config(
  new chipyard.WithSpikeTCM ++
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUART(txEntries=128, rxEntries=128) ++ // Spike sim requires a larger UART FIFO buffer, 
  new chipyard.config.WithNoUART() ++                           // so we overwrite the default one
  new chipyard.config.WithMemoryBusFrequency(2) ++
  new chipyard.config.WithPeripheryBusFrequency(2) ++
  new chipyard.config.WithBroadcastManager ++
  new chipyard.config.AbstractConfig)

class dmiSpikeUltraFastConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tilelink
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new SpikeUltraFastConfig)

// Add the default firechip devices
class SpikeUltraFastDevicesConfig extends Config(
  new chipyard.harness.WithSimBlockDevice ++
  new chipyard.harness.WithLoopbackNIC ++
  new icenet.WithIceNIC ++
  new testchipip.iceblk.WithBlockDevice ++

  new chipyard.WithSpikeTCM ++
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUART(txEntries=128, rxEntries=128) ++ // Spike sim requires a larger UART FIFO buffer, 
  new chipyard.config.WithNoUART() ++                           // so we overwrite the default one
  new chipyard.config.WithMemoryBusFrequency(2) ++
  new chipyard.config.WithPeripheryBusFrequency(2) ++
  new chipyard.config.WithBroadcastManager ++
  new chipyard.config.AbstractConfig)
