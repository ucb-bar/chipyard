package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS, SBUS}
import testchipip.soc.{OBUS}

//==================================================
// This file contains examples of the different ways
// clocks can be generated for chiypard designs
//==================================================

// The default constructs IOs for all requested clocks in the chiptopClockGroupsNode
// Note: This is what designs inheriting from AbstractConfig do by default
class DefaultClockingRocketConfig extends Config(
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// This is a more physically realistic approach, normally we can't punch out a separate
// pin for each clock domain. The standard "test chip" approach is to punch a few slow clock
// inputs, integrate a PLL, and generate an array of selectors/dividers to configure the
// clocks for each domain. See the source for WithPLLSelectorDividerClockGenerator for more info
class ChipLikeClockingRocketConfig extends Config(
  new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// This merges all the clock domains in chiptopClockGroupsNode into one, then generates a single
// clock input pin.
class SingleClockBroadcastRocketConfig extends Config(
  new chipyard.clocking.WithSingleClockBroadcastClockGenerator ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
