package chipyard

import org.chipsalliance.cde.config.{Config}

// ------------------------------
// Configs with RoCC Accelerators
// ------------------------------

class ReRoCCTestConfig extends Config(
  new rerocc.WithReRoCC ++
  new chipyard.config.WithCharacterCountRoCC ++                // rerocc tile4 is charcnt
  new chipyard.config.WithAccumulatorRoCC ++                   // rerocc tile3 is accum
  new chipyard.config.WithAccumulatorRoCC ++                   // rerocc tile2 is accum
  new chipyard.config.WithAccumulatorRoCC ++                   // rerocc tile1 is accum
  new chipyard.config.WithAccumulatorRoCC ++                   // rerocc tile0 is accum
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
