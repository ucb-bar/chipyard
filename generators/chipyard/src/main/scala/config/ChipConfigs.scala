package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._

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
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++    // Add rational crossings between RocketTile and uncore
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++                     // 1 RocketTile

  //==================================
  // Set up I/O
  //==================================
  new testchipip.WithSerialTLWidth(4) ++
  new chipyard.harness.WithSimAXIMemOverSerialTL ++                                     // Attach fast SimDRAM to TestHarness
  new chipyard.config.WithSerialTLBackingMemory ++                                      // Backing memory is over serial TL protocol
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++                  // 4GB max external memory

  //==================================
  // Set up clock./reset
  //==================================
  new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++   // Use a PLL-based clock selector/divider generator structure

  // Create the uncore clock group
  new chipyard.clocking.WithClockGroupsCombinedByName("uncore", "implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus") ++

  new chipyard.config.AbstractConfig)

class ChipBringupHostConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.clocking.WithPassthroughClockGenerator(1) ++

  new testchipip.WithSerialTLMem(base = 0, size = BigInt("80000000", 16), isMainMemory = false) ++
  new testchipip.WithSerialTLClockDirection(provideClock = true) ++
  new testchipip.WithUARTTSITLClient ++

  new chipyard.config.WithFrontBusFrequency(75.0) ++
  new chipyard.config.WithMemoryBusFrequency(75.0) ++
  new chipyard.config.WithPeripheryBusFrequency(75.0) ++

  new chipyard.NoCoresConfig
)

class TetheredChipLikeRocketConfig extends Config(
  new chipyard.harness.WithMultiChip(0, new ChipLikeRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig)
)
