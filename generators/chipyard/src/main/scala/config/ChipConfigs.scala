package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._

// A simple config demonstrating how to set up a basic chip in Chipyard
class ChipLikeQuadRocketConfig extends Config(
  //==================================
  // Set up TestHarness
  //==================================
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute frequencies for simulations in the harness
                                                                   // NOTE: This only simulates properly in VCS

  //==================================
  // Set up tiles
  //==================================
  new freechips.rocketchip.subsystem.WithAsynchronousRocketTiles(3, 3) ++    // Add rational crossings between RocketTile and uncore
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++                     // quad-core (4 RocketTiles)

  //==================================
  // Set up I/O
  //==================================
  new testchipip.WithSerialTLWidth(4) ++
  new testchipip.WithSerialTLBackingMemory ++                                           // Backing memory is over serial TL protocol
  new chipyard.harness.WithSimAXIMemOverSerialTL ++                                     // Attach fast SimDRAM to TestHarness
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++                  // 4GB max external memory
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

  //==================================
  // Set up clock./reset
  //==================================
  new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++   // Use a PLL-based clock selector/divider generator structure

  // Create the uncore clock group
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus"), Nil)) ++

  new chipyard.config.AbstractConfig)

