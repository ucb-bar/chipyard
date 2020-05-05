package chipyard

import chisel3._

import freechips.rocketchip.config.{Config}

// ---------------------
// Ariane Configs
// ---------------------

class ArianeConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++                      // display UART with a SimUARTAdapter
  new chipyard.iobinders.WithTieOffInterrupts ++                 // tie off top-level interrupts
  new chipyard.iobinders.WithSimAXIMem ++                        // drive the master AXI4 memory with a SimAXIMem
  new chipyard.iobinders.WithTiedOffDebug ++                     // tie off debug (since we are using SimSerial for testing)
  new chipyard.iobinders.WithSimSerial ++                        // drive TSI with SimSerial for testing
  new testchipip.WithTSI ++                                      // use testchipip serial offchip link
  new chipyard.config.WithBootROM ++                             // use default bootrom
  new chipyard.config.WithUART ++                                // add a UART
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithInclusiveCache ++       // use Sifive L2 cache
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
  new ariane.WithNArianeCores(1) ++                              // single Ariane core
  new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system

class dmiArianeConfig extends Config(
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithSimAXIMem ++
  new chipyard.iobinders.WithTiedOffSerial ++
  new chipyard.iobinders.WithSimDebug ++               // add SimDebug and use it to drive simulation
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new ariane.WithNArianeCores(1) ++
  new freechips.rocketchip.system.BaseConfig)
