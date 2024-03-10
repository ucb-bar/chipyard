package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}

// --------------
// Rocket+EE290 Configs
// These live in a separate file to simplify patching out for classes.
// --------------

// DOC include start: EE290Rocket
class EE290RocketConfig extends Config(
  new ee290.WithEE290RoCCAccel ++                                // add EE290 rocc accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class EE290BlackBoxRocketConfig extends Config(
  new ee290.WithEE290RoCCAccelBlackBox ++                                // add EE290 rocc accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
  
// class EE290WithCacheBlackBoxRocketConfig extends Config(
//   new ee290.WithEE290RoCCAccelWithCacheBlackBox ++                                // add EE290 rocc accelerator
//   new freechips.rocketchip.subsystem.WithNBigCores(1) ++
//   new chipyard.config.AbstractConfig)
// DOC include end: EE290Rocket