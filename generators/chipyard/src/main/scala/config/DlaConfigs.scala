package config

import chipyard.config.WithBootROM
import mdf.macrolib._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet}

//class DlaConfigs {
//
//}

class RoCCIceDMARocket2021Config extends Config(
//  new WithTop ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new WithRoCCIceDMA ++                        // use IceDMA
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.system.BaseConfig)

class WithRoCCIceDMA extends Config((site, here, up) => {
  case BuildRoCC => Seq(
    (p:Parameters) => {
      val regBufferNum = 4096 // Reg width
      val roccdma = LazyModule (new RoCCIceDMA(OpcodeSet.custom0, regBufferNum)(p))
      roccdma
    }
  )
})