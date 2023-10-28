package chipyard.config

import org.chipsalliance.cde.config.{Config}

// --------------
// Chipyard abstract ("base") configuration
// NOTE: This configuration is NOT INSTANTIABLE, as it defines a empty system with no tiles
//
// The default set of IOBinders instantiate IOcells and ChipTop IOs for digital IO bundles.
// The default set of HarnessBinders instantiate TestHarness hardware for interacting with ChipTop IOs
// --------------

class AbstractConfig extends Config(
  // The HarnessBinders control generation of hardware in the TestHarness
  new chipyard.harness.WithUARTAdapter ++                          // add UART adapter to display UART on stdout, if uart is present
  new chipyard.harness.WithBlackBoxSimMem ++                       // add SimDRAM DRAM model for axi4 backing memory, if axi4 mem is enabled
  new chipyard.harness.WithSimTSIOverSerialTL ++                   // add external serial-adapter and RAM
  new chipyard.harness.WithSimJTAGDebug ++                         // add SimJTAG if JTAG for debug exposed
  new chipyard.harness.WithSimDMI ++                               // add SimJTAG if DMI exposed
  new chipyard.harness.WithGPIOTiedOff ++                          // tie-off chiptop GPIOs, if GPIOs are present
  new chipyard.harness.WithSimSPIFlashModel ++                     // add simulated SPI flash memory, if SPI is enabled
  new chipyard.harness.WithSimAXIMMIO ++                           // add SimAXIMem for axi4 mmio port, if enabled
  new chipyard.harness.WithTieOffInterrupts ++                     // tie-off interrupt ports, if present
  new chipyard.harness.WithTieOffL2FBusAXI ++                      // tie-off external AXI4 master, if present
  new chipyard.harness.WithCustomBootPinPlusArg ++                 // drive custom-boot pin with a plusarg, if custom-boot-pin is present
  new chipyard.harness.WithSimUARTToUARTTSI ++                     // connect a SimUART to the UART-TSI port
  new chipyard.harness.WithClockFromHarness ++                     // all Clock I/O in ChipTop should be driven by harnessClockInstantiator
  new chipyard.harness.WithResetFromHarness ++                     // reset controlled by harness
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // generate clocks in harness with unsynthesizable ClockSourceAtFreqMHz

  // The IOBinders instantiate ChipTop IOs to match desired digital IOs
  // IOCells are generated for "Chip-like" IOs
  new chipyard.iobinders.WithSerialTLIOCells ++
  new chipyard.iobinders.WithDebugIOCells ++
  new chipyard.iobinders.WithUARTIOCells ++
  new chipyard.iobinders.WithGPIOCells ++
  new chipyard.iobinders.WithSPIFlashIOCells ++
  new chipyard.iobinders.WithExtInterruptIOCells ++
  new chipyard.iobinders.WithCustomBootPin ++
  // The "punchthrough" IOBInders below don't generate IOCells, as these interfaces shouldn't really be mapped to ASIC IO
  // Instead, they directly pass through the DigitalTop ports to ports in the ChipTop
  new chipyard.iobinders.WithI2CPunchthrough ++
  new chipyard.iobinders.WithSPIIOPunchthrough ++
  new chipyard.iobinders.WithAXI4MemPunchthrough ++
  new chipyard.iobinders.WithAXI4MMIOPunchthrough ++
  new chipyard.iobinders.WithTLMemPunchthrough ++
  new chipyard.iobinders.WithL2FBusAXI4Punchthrough ++
  new chipyard.iobinders.WithBlockDeviceIOPunchthrough ++
  new chipyard.iobinders.WithNICIOPunchthrough ++
  new chipyard.iobinders.WithTraceIOPunchthrough ++
  new chipyard.iobinders.WithUARTTSIPunchthrough ++
  new chipyard.iobinders.WithNMITiedOff ++

  // By default, punch out IOs to the Harness
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("sbus", "mbus", "pbus", "fbus", "cbus", "implicit"), Seq("tile"))) ++
  new chipyard.config.WithPeripheryBusFrequency(500.0) ++           // Default 500 MHz pbus
  new chipyard.config.WithMemoryBusFrequency(500.0) ++              // Default 500 MHz mbus

  new testchipip.WithCustomBootPin ++                               // add a custom-boot-pin to support pin-driven boot address
  new testchipip.WithBootAddrReg ++                                 // add a boot-addr-reg for configurable boot address
  new testchipip.WithSerialTLClientIdBits(4) ++                     // support up to 1 << 4 simultaneous requests from serialTL port
  new testchipip.WithSerialTLWidth(32) ++                           // fatten the serialTL interface to improve testing performance
  new testchipip.WithDefaultSerialTL ++                             // use serialized tilelink port to external serialadapter/harnessRAM
  new chipyard.config.WithDebugModuleAbstractDataWords(8) ++        // increase debug module data capacity
  new chipyard.config.WithBootROM ++                                // use default bootrom
  new chipyard.config.WithUART ++                                   // add a UART
  new chipyard.config.WithL2TLBs(1024) ++                           // use L2 TLBs
  new chipyard.config.WithNoSubsystemDrivenClocks ++                // drive the subsystem diplomatic clocks from ChipTop instead of using implicit clocks
  new chipyard.config.WithInheritBusFrequencyAssignments ++         // Unspecified clocks within a bus will receive the bus frequency if set
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++      // Default 1 memory channels
  new freechips.rocketchip.subsystem.WithClockGateModel ++          // add default EICG_wrapper clock gate model
  new freechips.rocketchip.subsystem.WithJtagDTM ++                 // set the debug module to expose a JTAG port
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++              // no top-level MMIO master port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithNoSlavePort ++             // no top-level MMIO slave port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithInclusiveCache ++          // use Sifive L2 cache
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++    // no external interrupts
  new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++ // leave the bus clocks undriven by sbus
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++     // hierarchical buses including sbus/mbus/pbus/fbus/cbus/l2
  new freechips.rocketchip.subsystem.WithDTS("ucb-bar,chipyard", Nil) ++ // custom device name for DTS
  new freechips.rocketchip.system.BaseConfig)                       // "base" rocketchip system
