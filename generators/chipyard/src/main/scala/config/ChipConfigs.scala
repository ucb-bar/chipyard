package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS}

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

