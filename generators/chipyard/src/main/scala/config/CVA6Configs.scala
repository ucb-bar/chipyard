package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// CVA6 Configs
// ---------------------

class CVA6Config extends Config(
  new cva6.WithNCVA6Cores(1) ++                    // single CVA6 core
  new chipyard.config.AbstractConfig)

class dmiCVA6Config extends Config(
  new chipyard.harness.WithSerialAdapterTiedOff ++ // Tie off the serial port, override default instantiation of SimSerial
  new chipyard.config.WithDMIDTM ++                // have debug module expose a clocked DMI port
  new cva6.WithNCVA6Cores(1) ++                    // single CVA6 core
  new chipyard.config.AbstractConfig)
