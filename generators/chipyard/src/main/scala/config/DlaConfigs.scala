package chipyard

import chipyard.config.WithBootROM
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.{WithCoherentBusTopology, WithoutTLMonitors}
import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet}

//class DlaConfigs {
//
//}

class RoCCIceDMARocket2021Config extends Config(
//  below three lines are original RoCCIceNet mixins, which are already outdated, coz the chipyard design are getting more and more complicated, then the single BaseConfig is replaced by AbstractConfig, which is the a superset of BaseConfig (comparing to chipyard-100, RocketConfig's member changes from BaseConfig into AbstractConfig)
//  new WithTop ++
//  new WithBootROM ++
//  new freechips.rocketchip.system.BaseConfig

// todo find out if this enable and the coherent bus mixin will deactivate TileLink essentially
//  new WithoutTLMonitors ++
//  new WithCoherentBusTopology ++

  new WithRoCCIceDMA ++                        // use IceDMA
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig
)

class WithRoCCIceDMA extends Config((site, here, up) => {
  case BuildRoCC => Seq(
    (p:Parameters) => {
      val regBufferNum = 4096 // Reg width
      val roccdma = LazyModule (new RoCCIceDMA(OpcodeSet.all, regBufferNum)(p))
      roccdma
    }
  )
})