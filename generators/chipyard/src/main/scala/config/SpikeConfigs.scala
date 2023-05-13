package chipyard

import org.chipsalliance.cde.config.{Config}

// Configs which instantiate a Spike-simulated
// tile that interacts with the Chipyard SoC
// as a hardware core would

class SpikeConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.AbstractConfig)

class dmiSpikeConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++                    // don't attach anything to serial-tilelink
  new chipyard.config.WithDMIDTM ++                              // have debug module expose a clocked DMI port
  new SpikeConfig)

// Avoids polling on the UART registers
class SpikeFastUARTConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUARTFIFOEntries(128, 128) ++
  new chipyard.config.WithMemoryBusFrequency(2) ++
  new chipyard.config.WithPeripheryBusFrequency(2) ++
  new chipyard.config.AbstractConfig)

// Makes the UART fast, also builds no L2 and a ludicrous L1D
class SpikeUltraFastConfig extends Config(
  new chipyard.WithSpikeTCM ++
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUARTFIFOEntries(128, 128) ++
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
  new testchipip.WithBlockDevice ++

  new chipyard.WithSpikeTCM ++
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUARTFIFOEntries(128, 128) ++
  new chipyard.config.WithMemoryBusFrequency(2) ++
  new chipyard.config.WithPeripheryBusFrequency(2) ++
  new chipyard.config.WithBroadcastManager ++
  new chipyard.config.AbstractConfig)
