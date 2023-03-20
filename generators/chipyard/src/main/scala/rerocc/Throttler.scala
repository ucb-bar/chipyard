
package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile.{HasNonDiplomaticTileParameters}

class TLThrottlerControlIO(width: Int) extends Bundle {
  val epoch = Input(UInt(width.W))
  val rate = Input(UInt(width.W))
  val prev_reqs = Output(UInt(width.W))
}

class TLThrottler(width: Int)(implicit p: Parameters) extends LazyModule with HasNonDiplomaticTileParameters {
  val node = TLIdentityNode()
  override def shouldBeInlined = false
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new TLThrottlerControlIO(width))

    val prev_reqs = RegInit(0.U(width.W))
    io.prev_reqs := prev_reqs
    val req_ctr = RegInit(0.U(width.W))
    val time_ctr = RegInit(0.U(width.W))
    val time_ctr_incr = time_ctr + 1.U
    time_ctr := Mux(time_ctr_incr >= io.epoch, 0.U, time_ctr_incr)
    when (time_ctr_incr >= io.epoch) {
      prev_reqs := req_ctr
      req_ctr := 0.U
    }

    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val throttle_queue = Module(new Queue(new TLBundleA(edgeIn.bundle), 1, pipe=true))
      throttle_queue.io.enq <> in.a

      when (out.a.fire && io.epoch > 0.U) {
        req_ctr := req_ctr + (1.U << (out.a.bits.size).asUInt)
      }

      out.a <> throttle_queue.io.deq
      when(req_ctr > io.rate && io.epoch =/= 0.U && io.rate =/= 0.U){
        throttle_queue.io.deq.ready := false.B
        out.a.valid := false.B
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
