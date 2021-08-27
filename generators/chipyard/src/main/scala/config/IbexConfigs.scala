package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// Ibex Configs
// ---------------------

class IbexConfig extends Config(
  new chipyard.config.WithBootROM ++               // Ibex reset vector is at 0x80
  new ibex.WithNIbexCores(1) ++                    // single Ibex core
  new chipyard.config.AbstractConfig)