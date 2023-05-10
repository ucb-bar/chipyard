package chipyard.rerocc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.{PTWResp, PTWReq}
import freechips.rocketchip.tile.{HasTileParameters}


class AddressOffsetterControlIO(implicit p: Parameters) extends Bundle {
  val vm_enabled = Input(Bool())
  val offset = Input(UInt(64.W))
  val base = Input(Vec(4, UInt(64.W)))
  val size = Input(Vec(4, UInt(32.W)))
}
class AddressOffsetter(n: Int)(implicit p: Parameters) extends LazyModule with HasTileParameters {
  val node = TLIdentityNode()
  override def shouldBeInlined = false
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val ctrl = new AddressOffsetterControlIO
      val ptw = new Bundle {
        val req_in = Flipped(Decoupled(Valid(new PTWReq)))
        val req_out = Decoupled(Valid(new PTWReq))
        val resp_in = Flipped(Valid(new PTWResp))
        val resp_out = Valid(new PTWResp)
      }
    })

    val bounds = Reg(Vec(n, UInt(64.W)))
    for (i <- 0 until n) {
      bounds(i) := io.ctrl.base(i) + io.ctrl.size(i)
    }
    require (node.in.size == 1)

    val ptw_busy = RegInit(false.B)
    io.ptw.req_in.ready := !ptw_busy && io.ptw.req_out.ready
    io.ptw.req_out.valid := io.ptw.req_in.valid && !ptw_busy
    io.ptw.req_out.bits := io.ptw.req_in.bits
    when (io.ptw.req_in.fire()) { ptw_busy := true.B }
    val ptw_addr = Reg(UInt(vpnBits.W))
    when (io.ptw.req_in.fire()) { ptw_addr := io.ptw.req_in.bits.bits.addr }

    io.ptw.resp_out := io.ptw.resp_in
    when (io.ptw.resp_out.fire()) { ptw_busy := false.B }

    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val q = Queue(in.a, 1, pipe=true)

      q.ready := out.a.ready
      out.a.valid := q.valid
      out.a.bits := q.bits

      val addr = Mux(io.ctrl.vm_enabled,
        ptw_addr << pgIdxBits,
        q.bits.address)
      val edit_addr = Mux(io.ctrl.vm_enabled,
        io.ptw.resp_in.bits.pte.ppn << pgIdxBits,
        q.bits.address)
      val out_addr = WireInit(addr)
      for (i <- 0 until n) {
        val base = io.ctrl.base(i)
        val bound = bounds(i)
        when (base =/= 0.U && bound =/= 0.U && addr >= base && addr < bound) {
          out_addr := edit_addr | io.ctrl.offset
        }
      }

      when (io.ctrl.vm_enabled) {
        io.ptw.resp_out.bits.pte.ppn := out_addr >> pgIdxBits
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
