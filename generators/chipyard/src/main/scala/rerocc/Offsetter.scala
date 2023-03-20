package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.{PTWResp}
import freechips.rocketchip.tile.{HasNonDiplomaticTileParameters}


class AddressOffsetterControlIO(implicit p: Parameters) extends Bundle {
  val vm_enabled = Input(Bool())
  val offset = Input(UInt(64.W))
  val base = Input(Vec(4, UInt(64.W)))
  val size = Input(Vec(4, UInt(32.W)))
}
class AddressOffsetter(n: Int)(implicit p: Parameters) extends LazyModule with HasNonDiplomaticTileParameters {
  val node = TLIdentityNode()
  override def shouldBeInlined = false
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val ctrl = new AddressOffsetterControlIO
      val ptw_in = Flipped(Valid(new PTWResp))
      val ptw_out = Valid(new PTWResp)
    })

    val bounds = Reg(Vec(n, UInt(64.W)))
    for (i <- 0 until n) {
      bounds(i) := io.ctrl.base(i) + io.ctrl.size(i)
    }
    require (node.in.size == 1)

    io.ptw_out := io.ptw_in

    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val q = Queue(in.a, 1, pipe=true)

      q.ready := out.a.ready
      out.a.valid := q.valid
      out.a.bits := q.bits

      val addr = Mux(io.ctrl.vm_enabled,
        io.ptw_in.bits.pte.ppn << pgIdxBits,
        q.bits.address)
      val out_addr = WireInit(addr)
      for (i <- 0 until n) {
        val base = io.ctrl.base(i)
        val bound = bounds(i)
        when (base =/= 0.U && bound =/= 0.U && addr >= base && addr < bound) {
          out_addr := addr | io.ctrl.offset
        }
      }

      when (io.ctrl.vm_enabled) {
        io.ptw_out.bits.pte.ppn := out_addr >> pgIdxBits
      } .otherwise {
        out.a.bits.address := out_addr
      }

      in.d <> out.d
      if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
        in .b <> out.b
        out.c <> in .c
        out.e <> in .e
      } else {
        in.b.valid := false.B
        in.c.ready := true.B
        in.e.ready := true.B
        out.b.ready := true.B
        out.c.valid := false.B
        out.e.valid := false.B
      }
    }

  }

}
