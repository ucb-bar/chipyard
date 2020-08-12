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
  new chipyard.iobinders.WithTiedOffSerial ++          // Tie off the serial port, override default instantiation of SimSerial
  new chipyard.iobinders.WithSimDebug ++               // add SimDebug and use it to drive simulation, override default tie-off debug
  new ariane.WithNArianeCores(1) ++                    // single Ariane core
  new chipyard.config.AbstractConfig)
