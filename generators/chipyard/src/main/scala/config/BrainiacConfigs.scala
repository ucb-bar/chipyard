package chipyard

import org.chipsalliance.cde.config.Config

// ---------------------
// Brainiac Configs
// ---------------------

class BrainiacConfig extends Config(
  new brainiac.WithBrainiacROM("./generators/brainiac/src/main/resources/sw/e.b") ++
  new brainiac.WithBrainiacLoopPredictor ++
  new brainiac.WithNBrainiacCores(1) ++
  new chipyard.config.AbstractConfig)
