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

class Brainiac3WideConfig extends Config(
  new brainiac.WithBrainiacFetchWidth(16) ++
  new brainiac.WithBrainiacRetireWidth(3) ++
  new BrainiacConfig)

class Brainiac4WideConfig extends Config(
  new brainiac.WithBrainiacFetchWidth(16) ++
  new brainiac.WithBrainiacRetireWidth(4) ++
  new BrainiacConfig)
