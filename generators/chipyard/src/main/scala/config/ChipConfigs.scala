package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS, SBUS}
import testchipip.{OBUS}

// A simple config demonstrating how to set up a basic chip in Chipyard
class ChipLikeRocketConfig extends Config(
  //==================================
  // Set up TestHarness
  //==================================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute frequencies for simulations in the harness
                                                                   // NOTE: This only simulates properly in VCS
  new chipyard.harness.WithSimAXIMemOverSerialTL ++                // Attach SimDRAM to serial-tl port

  //==================================
  // Set up tiles
  //==================================
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++    // Add rational crossings between RocketTile and uncore
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                     // 1 RocketTile

  //==================================
  // Set up I/O
  //==================================
  new testchipip.WithSerialTLWidth(4) ++
  new testchipip.WithSerialTLBackingMemory ++                                           // Backing memory is over serial TL protocol
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++                  // 4GB max external memory
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

  //==================================
  // Set up buses
  //==================================
  new testchipip.WithOffchipBusManager(MBUS) ++
  new testchipip.WithOffchipBus ++

  //==================================
  // Set up clock./reset
  //==================================
  new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++   // Use a PLL-based clock selector/divider generator structure

  // Create the uncore clock group
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus"))) ++

  new chipyard.config.AbstractConfig)

// A simple config demonstrating a "bringup prototype" to bringup the ChipLikeRocketconfig
class ChipBringupHostConfig extends Config(
  //=============================
  // Set up TestHarness for standalone-sim
  // These fragments only affect the design when simulated by itself (without the ChipLikeRocketConfig)
  //=============================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithSerialTLTiedOff ++ // when doing standalone sim, tie off the serial-tl port
  new chipyard.harness.WithSimTSIToUARTTSI ++
  new chipyard.iobinders.WithSerialTLPunchthrough ++

  //=============================
  // Setup the SerialTL side on the bringup device
  //=============================
  new testchipip.WithSerialTLWidth(4) ++                                       // match width with the chip
  new testchipip.WithSerialTLMem(base = 0x0, size = BigInt(1) << 48,           // accessible memory of the chip
                                 idBits = 4, isMainMemory = false) ++
  new testchipip.WithSerialTLClockDirection(provideClockFreqMHz = Some(75)) ++ // bringup board drives the clock for the serial-tl receiver on the chip, use 50MHz clock

  //============================
  // Setup bus topology on the bringup system
  //============================
  new testchipip.WithOffchipBusManager(SBUS,
    blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4),
    replicationBase = Some(BigInt(1) << 48)) ++
  new testchipip.WithOffchipBus ++                                             // offchip bus

  //=============================
  // Set up memory on the bringup system
  //=============================
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++         // match what the chip believes

  //=============================
  // Generate the TSI-over-UART side of the bringup system
  //=============================
  new testchipip.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++     // nonstandard baud rate to improve performance

  //=============================
  // Set up clocks of the bringup system
  //=============================
  new chipyard.clocking.WithPassthroughClockGenerator ++  // pass all the clocks through, since this isn't a chip
  new chipyard.config.WithFrontBusFrequency(75.0) ++
  new chipyard.config.WithMemoryBusFrequency(75.0) ++
  new chipyard.config.WithPeripheryBusFrequency(75.0) ++

  // Base is the no-cores config
  new chipyard.NoCoresConfig)

class TetheredChipLikeRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute freqs for sims in the harness
  new chipyard.harness.WithMultiChipSerialTL(0, 1) ++
  new chipyard.harness.WithMultiChip(0, new ChipLikeRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig))
