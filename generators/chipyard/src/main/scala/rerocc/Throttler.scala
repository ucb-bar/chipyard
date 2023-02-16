
package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

class TLThrottlerReq(param_bitwidth: Int) extends Bundle {
  val epoch = UInt(param_bitwidth.W)
  val max_req = UInt(param_bitwidth.W)
}

class TLThrottlerResp(param_bitwidth: Int) extends Bundle {
  val prev_req = UInt(param_bitwidth.W)
}

class TLThrottler(param_bitwidth: Int, queue_depth: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLIdentityNode()
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
  //lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val req = Flipped(Decoupled(new TLThrottlerReq(param_bitwidth)))
      val resp = Decoupled(new TLThrottlerResp(param_bitwidth))
      //val busy = Output(Bool())
      //val flush = Input(Bool())
    })

    def satAdd(u: UInt, v: UInt, max: UInt): UInt = {
      Mux(u +& v > max, max, u + v)
    }

    def floorAdd(u: UInt, n: UInt, max_plus_one: UInt, en: Bool = true.B): UInt = {
      val max = max_plus_one - 1.U
      MuxCase(u + n, Seq(
        (!en) -> u,
        ((u +& n) > max) -> 0.U
      ))
    }
    val req_counter = RegInit(0.U((param_bitwidth).W))
    val epoch = RegInit(0.U((param_bitwidth).W))
    val max_req = RegInit(0.U((param_bitwidth).W))
    val epoch_temp = RegInit(0.U((param_bitwidth).W))
    val max_req_temp = RegInit(0.U((param_bitwidth).W))
    val prev_req = RegInit(0.U(param_bitwidth.W))

    io.resp.valid := true.B
    io.resp.bits.prev_req := prev_req
    val temp_waiting = RegInit(false.B)
    io.req.ready := !temp_waiting

    val epoch_counter = RegInit(0.U((param_bitwidth).W))
    when(io.req.fire){
      epoch_temp := io.req.bits.epoch
      max_req_temp := io.req.bits.max_req
      //io.req.ready := false.B
      temp_waiting := true.B
      when(epoch_counter === epoch - 1.U || epoch === 0.U){
        epoch := io.req.bits.epoch
        max_req := io.req.bits.max_req
        //io.req.ready := true.B
        temp_waiting := false.B
      }
    }
    epoch_counter := floorAdd(epoch_counter, 1.U, epoch, epoch > 0.U) // ToDo: initialize epoch when released & allocated

    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      //val throttle_queue = Queue(in.a)
      val throttle_queue = Module(new Queue(new TLBundleA(edgeIn.bundle), 1))
      throttle_queue.io.enq <> in.a
      //throttle_queue.io.enq.valid := in.a.valid
      throttle_queue.io.deq.ready := (req_counter < max_req || epoch === 0.U || max_req === 0.U)
      when(throttle_queue.io.deq.fire && epoch > 0.U){
        req_counter := satAdd(req_counter, (1.U << in.a.bits.size).asUInt, max_req)
      }
      //out.a <> in.a // Add throttle to this, in.a is Decoupled[TLBundleA]
      out.a <> throttle_queue.io.deq
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

    when(epoch === 0.U && !io.req.fire){
      epoch_counter := 0.U
      req_counter := 0.U
      max_req := 0.U
      //io.req.ready := true.B
      //prev_req := 0.U
      temp_waiting := false.B
    }.elsewhen(epoch_counter === epoch - 1.U) {
      req_counter := 0.U
      prev_req := req_counter
      when(temp_waiting) {
        epoch := epoch_temp
        max_req := max_req_temp
        //io.req.ready := true.B
        temp_waiting := false.B
      }
    }
  }
}
