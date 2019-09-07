//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import chisel3.util.{HasBlackBoxInline, HasBlackBoxResource, HasBlackBoxPath}
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._

case object Sha3WidthP extends Field[Int]
case object Sha3Stages extends Field[Int]
case object Sha3FastMem extends Field[Boolean]
case object Sha3BufferSram extends Field[Boolean]

class Sha3Accel(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new Sha3AccelImp(this)
}

class WrapBundle(nPTWports: Int)(implicit p: Parameters) extends Bundle {
  val io = new RoCCIO(nPTWports)
  val clock = Clock(INPUT)
  val reset = Input(UInt(1.W))
}

class Sha3BlackBox(nPTWports: Int)(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new WrapBundle(nPTWports))

  setResource("/vsrc/Sha3BlackBox.v")
}

class Sha3AccelImp(outer: Sha3Accel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  //parameters
  val W = p(Sha3WidthP)
  val S = p(Sha3Stages)
  //constants
  val r = 2*256
  val c = 25*W - r
  val round_size_words = c/W
  val rounds = 24 //12 + 2l
  val hash_size_words = 256/W
  val bytes_per_word = W/8

  val sha3bb = Module(new Sha3BlackBox(0))

  io <> sha3bb.io.io
  sha3bb.io.clock := clock
  sha3bb.io.reset := reset
}

class WithSha3Accel extends Config ((site, here, up) => {
      case Sha3WidthP => 64
      case Sha3Stages => 1
      case Sha3FastMem => true
      case Sha3BufferSram => false
      case BuildRoCC => Seq(
        (p: Parameters) => {
          val sha3 = LazyModule.apply(new Sha3Accel(OpcodeSet.custom2)(p))
          sha3
        }
      )
  })
