package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// Ariane Configs
// ---------------------

class ArianeConfig extends Config(
  new ariane.WithNArianeCores(1) ++                    // single Ariane core
  new chipyard.config.AbstractConfig)

class dmiArianeConfig extends Config(
  new chipyard.harness.WithTiedOffTSISerial ++         // Tie off the serial port, override default instantiation of SimSerial
  new chipyard.config.WithDMIDTM ++                    // have debug module expose a clocked DMI port
  new ariane.WithNArianeCores(1) ++                    // single Ariane core
  new chipyard.config.AbstractConfig)
