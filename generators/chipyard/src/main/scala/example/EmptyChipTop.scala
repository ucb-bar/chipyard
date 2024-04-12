package chipyard.example

import chisel3._

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{DontTouch}

import chipyard._
import chipyard.harness.{BuildTop}

class EmptyChipTop(implicit p: Parameters) extends LazyModule {
  override lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) with DontTouch {
    // Your custom non-rocketchip-soc stuff here
  }
}

class WithEmptyChipTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new EmptyChipTop()(p)
})
