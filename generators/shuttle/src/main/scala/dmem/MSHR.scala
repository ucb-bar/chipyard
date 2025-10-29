package shuttle.dmem

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._

class ShuttleDCacheMSHR(id: Int)(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val req_pri_val    = Input(Bool())
    val req_pri_rdy    = Output(Bool())
    val req_sec_val    = Input(Bool())
    val req_sec_rdy    = Output(Bool())
    val req_bits       = Input(new ShuttleMSHRReq())
    val probe_addr     = Input(UInt(paddrBits.W))

    val idx_match       = Output(Bool())
    val tag             = Output(Bits(tagBits.W))

    val mem_acquire  = Decoupled(new TLBundleA(edge.bundle))
    val mem_grant = Flipped(Valid(new TLBundleD(edge.bundle)))
    val mem_finish = Decoupled(new TLBundleE(edge.bundle))

    val refill = Output(new L1RefillReq()) // Data is bypassed
    val meta_write = Decoupled(new L1MetaWriteReq)
    val replay = Decoupled(new ShuttleMSHRReq)
    val wb_req = Decoupled(new WritebackReq(edge.bundle))
    val probe_rdy = Output(Bool())
  })

  val s_invalid :: s_wb_req :: s_wb_resp :: s_meta_clear :: s_refill_req :: s_refill_resp :: s_meta_write_req :: s_meta_write_resp :: s_drain_rpq :: Nil = Enum(9)
  val state = RegInit(s_invalid)

  val req = Reg(new ShuttleMSHRReq)
  val req_idx = req.addr(untagBits-1,blockOffBits)
  val req_tag = req.addr >> untagBits
  val req_block_addr = (req.addr >> blockOffBits) << blockOffBits
  val idx_match = req_idx === io.req_bits.addr(untagBits-1,blockOffBits)
  val probe_idx_match = req_idx === io.probe_addr(untagBits-1,blockOffBits)

  val new_coh = RegInit(ClientMetadata.onReset)
  val (_, shrink_param, coh_on_clear)    = req.old_meta.coh.onCacheControl(M_FLUSH)
  val grow_param                                  = new_coh.onAccess(req.cmd)._2
  val coh_on_grant                                = new_coh.onGrant(req.cmd, io.mem_grant.bits.param)
  // We only accept secondary misses if we haven't yet sent an Acquire to outer memory
  // or if the Acquire that was sent will obtain a Grant with sufficient permissions
  // to let us replay this new request. I.e. we don't handle multiple outstanding
  // Acquires on the same block for now.
  val (cmd_requires_second_acquire, is_hit_again, _, dirtier_coh, dirtier_cmd) =
    new_coh.onSecondaryAccess(req.cmd, io.req_bits.cmd)

  val states_before_refill = Seq(s_wb_req, s_wb_resp, s_meta_clear)
  val (_, _, refill_done, refill_address_inc) = edge.addr_inc(io.mem_grant)
  val sec_rdy = idx_match &&
                  (state.isOneOf(states_before_refill) ||
                    (state.isOneOf(s_refill_req, s_refill_resp) &&
                      !cmd_requires_second_acquire && !refill_done))

  val rpq = Module(new Queue(new ShuttleMSHRReq, cfg.nRPQ))
  rpq.io.enq.valid := (io.req_pri_val && io.req_pri_rdy || io.req_sec_val && sec_rdy) && !isPrefetch(io.req_bits.cmd)
  rpq.io.enq.bits := io.req_bits
  rpq.io.deq.ready := (io.replay.ready && state === s_drain_rpq) || state === s_invalid

  val acked = Reg(Bool())
  when (io.mem_grant.valid) { acked := true.B }

  when (state === s_drain_rpq && !rpq.io.deq.valid) {
    state := s_invalid
  }
  when (state === s_meta_write_resp) {
    // this wait state allows us to catch RAW hazards on the tags via nack_victim
    state := s_drain_rpq
  }
  when (state === s_meta_write_req && io.meta_write.ready) {
    state := s_meta_write_resp
  }
  when (state === s_refill_resp && refill_done) {
    new_coh := coh_on_grant
    state := s_meta_write_req
  }
  when (io.mem_acquire.fire) { // s_refill_req
    state := s_refill_resp
  }
  when (state === s_meta_clear && io.meta_write.ready) {
    state := s_refill_req
  }
  when (state === s_wb_resp && io.wb_req.ready && acked) {
    state := s_meta_clear
  }
  when (io.wb_req.fire) { // s_wb_req
    state := s_wb_resp
  }
  when (io.req_sec_val && io.req_sec_rdy) { // s_wb_req, s_wb_resp, s_refill_req
    //If we get a secondary miss that needs more permissions before we've sent
    //  out the primary miss's Acquire, we can upgrade the permissions we're 
    //  going to ask for in s_refill_req
    req.cmd := dirtier_cmd
    when (is_hit_again) {
      new_coh := dirtier_coh
    }
  }
  when (io.req_pri_val && io.req_pri_rdy) {
    req := io.req_bits
    acked := false.B
    val old_coh = io.req_bits.old_meta.coh
    val needs_wb = old_coh.onCacheControl(M_FLUSH)._1
    val (is_hit, _, coh_on_hit) = old_coh.onAccess(io.req_bits.cmd)
    when (io.req_bits.tag_match) {
      when (is_hit) { // set dirty bit
        new_coh := coh_on_hit
        state := s_meta_write_req
      }.otherwise { // upgrade permissions
        new_coh := old_coh
        state := s_refill_req
      }
    }.otherwise { // writback if necessary and refill
      new_coh := ClientMetadata.onReset
      state := Mux(needs_wb, s_wb_req, s_meta_clear)
    }
  }

  val grantackq = Module(new Queue(new TLBundleE(edge.bundle), 1))
  val can_finish = state.isOneOf(s_invalid, s_refill_req)
  grantackq.io.enq.valid := refill_done && edge.isRequest(io.mem_grant.bits)
  grantackq.io.enq.bits := edge.GrantAck(io.mem_grant.bits)
  io.mem_finish.valid := grantackq.io.deq.valid && can_finish
  io.mem_finish.bits := grantackq.io.deq.bits
  grantackq.io.deq.ready := io.mem_finish.ready && can_finish

  io.idx_match := (state =/= s_invalid) && idx_match
  io.refill.way_en := req.way_en
  io.refill.addr := req_block_addr | refill_address_inc
  io.tag := req_tag
  io.req_pri_rdy := state === s_invalid
  io.req_sec_rdy := sec_rdy && rpq.io.enq.ready

  val meta_hazard = RegInit(0.U(2.W))
  when (meta_hazard =/= 0.U) { meta_hazard := meta_hazard + 1.U }
  when (io.meta_write.fire) { meta_hazard := 1.U }
  io.probe_rdy := !(state =/= s_invalid && probe_idx_match) || (!state.isOneOf(states_before_refill) && meta_hazard === 0.U)

  io.meta_write.valid := state.isOneOf(s_meta_write_req, s_meta_clear)
  io.meta_write.bits.idx := req_idx
  io.meta_write.bits.tag := io.tag
  io.meta_write.bits.data.coh := Mux(state === s_meta_clear, coh_on_clear, new_coh)
  io.meta_write.bits.data.tag := io.tag
  io.meta_write.bits.way_en := req.way_en

  io.wb_req.valid := state === s_wb_req
  io.wb_req.bits.source := id.U
  io.wb_req.bits.tag := req.old_meta.tag
  io.wb_req.bits.idx := req_idx
  io.wb_req.bits.param := shrink_param
  io.wb_req.bits.way_en := req.way_en
  io.wb_req.bits.voluntary := true.B

  io.mem_acquire.valid := state === s_refill_req && grantackq.io.enq.ready
  io.mem_acquire.bits := edge.AcquireBlock(
                                fromSource = id.U,
                                toAddress = Cat(io.tag, req_idx) << blockOffBits,
                                lgSize = lgCacheBlockBytes.U,
                                growPermissions = grow_param)._2

  io.replay.valid := state === s_drain_rpq && rpq.io.deq.valid
  io.replay.bits := rpq.io.deq.bits
  io.replay.bits.addr := Cat(io.tag, req_idx, rpq.io.deq.bits.addr(blockOffBits-1,0))
  io.replay.bits.cmd := rpq.io.deq.bits.cmd

  when (state === s_drain_rpq && !rpq.io.deq.valid) {
    state := Mux(RegNext(!rpq.io.deq.valid), s_invalid, state)
  }
  rpq.io.deq.ready := io.replay.ready && state === s_drain_rpq
}
