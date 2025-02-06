package chipyard

import org.chipsalliance.cde.config.{Config}

// ------------------------------------------------------------
// Configs which demonstrate modifying the uncore memory system
// ------------------------------------------------------------

class SimAXIRocketConfig extends Config(
  new chipyard.harness.WithSimAXIMem ++                     // drive the master AXI4 memory with a SimAXIMem, a 1-cycle magic memory, instead of default SimDRAM
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class GB1MemoryRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1<<30) * 1L) ++ // use 1GB simulated external memory
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: mbusscratchpadrocket
class MbusScratchpadOnlyRocketConfig extends Config(
  new testchipip.soc.WithMbusScratchpad(banks=2, partitions=2) ++ // add 2 partitions of 2 banks mbus backing scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++         // remove offchip mem port
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: mbusscratchpadrocket

class SbusScratchpadRocketConfig extends Config(
  new testchipip.soc.WithSbusScratchpad(base=0x70000000L, banks=4) ++ // add 4 banks sbus scratchpad
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class SbusBypassRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSbusBypass ++ // Add bypass path to access DRAM incoherently through an address alias
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class QuadChannelRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNMemoryChannels(4) ++      // 4 AXI4 channels
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

class BroadcastCoherenceRocketConfig extends Config(
  new chipyard.config.WithBroadcastManager ++                 // Use broadcast-based coherence hub
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
