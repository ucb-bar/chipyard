package chipyard

import freechips.rocketchip.config.{Config}

// Configs which instantiate a Spike-simulated
// tile that interacts with the Chipyard SoC
// as a hardware core would

class SpikeConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.AbstractConfig)

// Avoids polling on the UART registers
class SpikeFastUARTConfig extends Config(
  new chipyard.WithNSpikeCores(1) ++
  new chipyard.config.WithUARTFIFOEntries(128, 128) ++
  new chipyard.config.WithMemoryBusFrequency(1) ++
  new chipyard.config.WithPeripheryBusFrequency(1) ++
  new chipyard.config.AbstractConfig)
