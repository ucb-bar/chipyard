package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{SBUS, MBUS}

// A simple config demonstrating how to set up a basic chip in Chipyard
class ChipLikeRocketConfig extends Config(
  //==================================
  // Set up TestHarness for standalone-sim
  // These fragments only affect the design when simulated by itself (without the BringupHostConfig)
  //==================================
  new chipyard.harness.WithSimAXIMemOverSerialTL ++                // Attach fast SimDRAM to TestHarness
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute frequencies for simulations in the harness
                                                                   // NOTE: This only simulates properly in VCS

  //==================================
  // Set up tiles
  //==================================
  new chipyard.config.WithL2TLBs(0) ++
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++    // Add rational crossings between RocketTile and uncore
  new freechips.rocketchip.subsystem.WithL1ICacheSets(16) ++                 // make the core small
  new freechips.rocketchip.subsystem.WithL1DCacheSets(16) ++
  new freechips.rocketchip.subsystem.WithL1ICacheWays(2) ++
  new freechips.rocketchip.subsystem.WithL1DCacheWays(2) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                     // 1 RocketTile

  //==================================
  // Set up subsystem
  //==================================
  new testchipip.WithOffchipBusManager(MBUS) ++
  new testchipip.WithOffchipBus ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays = 2, capacityKB = 64) ++ // make the L2 small for this example chip
  new freechips.rocketchip.subsystem.WithNBanks(2) ++                                  // 2 bank of L2 only

  //==================================
  // Set up I/O
  //==================================
  new testchipip.WithSerialTLWidth(4) ++
  new chipyard.config.WithSerialTLBackingMemory ++                                      // Backing memory is over serial TL protocol
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++                  // 4GB max external memory
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

  //==================================
  // Set up clock./reset
  //==================================
  new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++   // Use a PLL-based clock selector/divider generator structure

  // Create the uncore clock group
  new chipyard.clocking.WithClockGroupsCombinedByName("uncore", "implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus") ++

  new chipyard.config.AbstractConfig)

// A simple config demonstrating a "bringup prototype" to bringup the ChipLikeRocketconfig
class ChipBringupHostConfig extends Config(
  //=============================
  // Set up TestHarness for standalone-sim
  // These fragments only affect the design when simulated by itself (without the ChipLikeRocketConfig)
  //=============================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithSerialTLTiedOff ++ // when doing standalone sim, tie off the serial-tl port

  //=============================
  // Setup the SerialTL side on the bringup device
  //=============================
  new testchipip.WithSerialTLWidth(4) ++                                                  // match width with the chip
  new testchipip.WithOffchipBusManager(SBUS,
    blockRange = AddressSet.misaligned(0x80000000L, (1 << 30) * 4L),
    replicationBase = Some(BigInt("1000000000", 16))) ++
  new testchipip.WithOffchipBus ++                                                        // offchip bus, but don't directly connect it to existing buses
  new testchipip.WithSerialTLMem(base = 0x1000, size = BigInt("1000000000", 16) - 0x1000, // accessible memory of the chip
                                 idBits = 8, isMainMemory = false) ++
  new testchipip.WithSerialTLClockDirection(provideClockFreqMHz = Some(50)) ++            // bringup board drives the clock for the serial-tl receiver on the chip, use 50MHz clock

  //=============================
  // Set up memory on the bringup system
  //=============================
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++                  // match what the chip believes

  //=============================
  // Generate the TSI-over-UART side of the bringup system
  //=============================
  new testchipip.WithUARTTSITLClient(initBaudRate = BigInt(921600)) ++ // nonstandard baud rate to improve performance

  //=============================
  // Set up clocks of the bringup system
  //=============================
  new chipyard.clocking.WithPassthroughClockGenerator ++  // pass all the clocks through, since this isn't a chip
  new chipyard.config.WithFrontBusFrequency(75.0) ++
  new chipyard.config.WithMemoryBusFrequency(75.0) ++
  new chipyard.config.WithPeripheryBusFrequency(75.0) ++

  // Base is the no-cores config
  new chipyard.NoCoresConfig
)

// This config will instantiate both the Chip and the Bringup platform in a single RTL simulation
class TetheredChipLikeRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute freqs for sims in the harness
  new chipyard.harness.WithMultiChipSerialTL(0, 1) ++
  new chipyard.harness.WithMultiChip(0, new ChipLikeRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig)
)
