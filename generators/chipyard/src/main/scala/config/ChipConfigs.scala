package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS, SBUS}
import testchipip.soc.{OBUS}

// A simple config demonstrating how to set up a basic chip in Chipyard
class ChipLikeRocketConfig extends Config(
  //==================================
  // Set up TestHarness
  //==================================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute frequencies for simulations in the harness
                                                                   // NOTE: This only simulates properly in VCS

  //==================================
  // Set up tiles
  //==================================
  new freechips.rocketchip.rocket.WithAsynchronousCDCs(depth=8, sync=3) ++ // Add async crossings between RocketTile and uncore
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++                      // 1 RocketTile

  //==================================
  // Set up I/O
  //==================================
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(              // 1 serial tilelink port
    manager = Some(testchipip.serdes.SerialTLManagerParams(                             // port acts as a manager of offchip memory
      memParams = Seq(testchipip.serdes.ManagerRAMParams(                               // 4 GB of off-chip memory
        address = BigInt("80000000", 16),
        size    = BigInt("100000000", 16)
      )),
      isMemoryDevice = true
    )),
    client = Some(testchipip.serdes.SerialTLClientParams()),                            // Allow an external manager to probe this chip
    phyParams = testchipip.serdes.DecoupledExternalSyncSerialPhyParams(phitWidth=4, flitWidth=16)   // 4-bit bidir interface, sync'd to an external clock
  ))) ++

  new freechips.rocketchip.subsystem.WithNoMemPort ++                                   // Remove axi4 mem port
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

  //==================================
  // Set up buses
  //==================================
  new testchipip.soc.WithOffchipBusClient(MBUS) ++                                      // offchip bus connects to MBUS, since the serial-tl needs to provide backing memory
  new testchipip.soc.WithOffchipBus ++                                                  // attach a offchip bus, since the serial-tl will master some external tilelink memory

  //==================================
  // Set up clock./reset
  //==================================
  new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++   // Use a PLL-based clock selector/divider generator structure

  // Create the uncore clock group
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus"), Nil)) ++

  new chipyard.config.AbstractConfig)

class FlatChipTopChipLikeRocketConfig extends Config(
  new chipyard.example.WithFlatChipTop ++
  new chipyard.ChipLikeRocketConfig)

// A simple config demonstrating a "bringup prototype" to bringup the ChipLikeRocketconfig
class ChipBringupHostConfig extends Config(
  //=============================
  // Set up TestHarness for standalone-sim
  //=============================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++  // Generate absolute frequencies
  new chipyard.harness.WithSerialTLTiedOff ++                       // when doing standalone sim, tie off the serial-tl port
  new chipyard.harness.WithSimTSIToUARTTSI ++                       // Attach SimTSI-over-UART to the UART-TSI port
  new chipyard.iobinders.WithSerialTLPunchthrough ++                // Don't generate IOCells for the serial TL (this design maps to FPGA)

  //=============================
  // Setup the SerialTL side on the bringup device
  //=============================
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(
    manager = Some(testchipip.serdes.SerialTLManagerParams(
      memParams = Seq(testchipip.serdes.ManagerRAMParams(                            // Bringup platform can access all memory from 0 to DRAM_BASE
        address = BigInt("00000000", 16),
        size    = BigInt("80000000", 16)
      ))
    )),
    client = Some(testchipip.serdes.SerialTLClientParams()),                                        // Allow chip to access this device's memory (DRAM)
    phyParams = testchipip.serdes.DecoupledInternalSyncSerialPhyParams(phitWidth=4, flitWidth=16, freqMHz = 75) // bringup platform provides the clock
  ))) ++

  //============================
  // Setup bus topology on the bringup system
  //============================
  new testchipip.soc.WithOffchipBusClient(SBUS,                                // offchip bus hangs off the SBUS
    blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4)) ++ // offchip bus should not see the main memory of the testchip, since that can be accessed directly
  new testchipip.soc.WithOffchipBus ++                                         // offchip bus

  //=============================
  // Set up memory on the bringup system
  //=============================
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++         // match what the chip believes the max size should be

  //=============================
  // Generate the TSI-over-UART side of the bringup system
  //=============================
  new testchipip.tsi.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++       // nonstandard baud rate to improve performance

  //=============================
  // Set up clocks of the bringup system
  //=============================
  new chipyard.clocking.WithPassthroughClockGenerator ++ // pass all the clocks through, since this isn't a chip
  new chipyard.config.WithUniformBusFrequencies(75.0) ++   // run all buses of this system at 75 MHz

  // Base is the no-cores config
  new chipyard.NoCoresConfig)

// DOC include start: TetheredChipLikeRocketConfig
class TetheredChipLikeRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++   // use absolute freqs for sims in the harness
  new chipyard.harness.WithMultiChipSerialTL(0, 1) ++                // connect the serial-tl ports of the chips together
  new chipyard.harness.WithMultiChip(0, new ChipLikeRocketConfig) ++ // ChipTop0 is the design-to-be-taped-out
  new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig))  // ChipTop1 is the bringup design
// DOC include end: TetheredChipLikeRocketConfig

// Verilator does not initialize some of the async-reset reset-synchronizer
// flops properly, so this config disables them.
// This config should only be used for verilator simulations
class VerilatorCITetheredChipLikeRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++   // use absolute freqs for sims in the harness
  new chipyard.harness.WithMultiChipSerialTL(0, 1) ++                // connect the serial-tl ports of the chips together
  new chipyard.harness.WithMultiChip(0,                                         // These fragments remove all troublesome
    new chipyard.clocking.WithPLLSelectorDividerClockGenerator(enable=false) ++ // clocking features from the design
    new chipyard.iobinders.WithDebugIOCells(syncReset = false) ++
    new chipyard.config.WithNoResetSynchronizers ++
    new ChipLikeRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig))
