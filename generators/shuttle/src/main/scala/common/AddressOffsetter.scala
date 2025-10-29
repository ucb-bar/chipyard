package shuttle.common

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

class AddressOffsetter(mask: BigInt, offset: BigInt)(implicit p: Parameters) extends LazyModule {
  val node = TLAdapterNode({cp => cp}, {mp => mp})
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val base = Input(UInt())
    })
    def contains(x: UInt) = ((x ^ io.base).zext & (~mask).S) === 0.S
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out.a <> in.a
      val adjusted = in.a.bits.address | offset.U
      when (edgeOut.manager.containsSafe(adjusted) && contains(in.a.bits.address)) {
        out.a.bits.address := adjusted
      }

      in.b <> out.b
      when (out.b.valid) { assert(!contains(out.b.bits.address)) }

      out.c <> in.c
      when (in.c.valid) { assert(!contains(in.c.bits.address)) }

      in.d <> out.d
      out.e <> in.e
    }
  }
}
