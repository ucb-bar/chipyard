package customAccRoCC

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._

class WithCustomAccRoCC extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq((p: Parameters) => {
    val customAccRoCC = LazyModule(new customAccelerator(OpcodeSet.custom0)(p))
    customAccRoCC
  })
})