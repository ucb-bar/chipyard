package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// CVA6 Configs
// ---------------------

class Cva6Config extends Config(
  new cva6.WithNCva6Cores(1) ++                    // single CVA6 core
  new chipyard.config.AbstractConfig)

class dmiCva6Config extends Config(
  new chipyard.iobinders.WithTiedOffSerial ++          // Tie off the serial port, override default instantiation of SimSerial
  new chipyard.iobinders.WithSimDebug ++               // add SimDebug and use it to drive simulation, override default tie-off debug
  new cva6.WithNCva6Cores(1) ++                    // single CVA6 core
  new chipyard.config.AbstractConfig)
