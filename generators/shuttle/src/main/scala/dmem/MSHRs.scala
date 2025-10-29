package shuttle.dmem

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._


class ShuttleDCacheMSHRFile(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ShuttleMSHRReq))
    val req_data = Input(UInt(coreDataBits.W))
    val req_mask = Input(UInt(coreDataBytes.W))
    val probe_addr = Input(UInt(paddrBits.W))
    val resp = Decoupled(new ShuttleDMemResp)
    val secondary_miss = Output(Bool())

    val mem_acquire  = Decoupled(new TLBundleA(edge.bundle))
    val mem_grant = Flipped(Valid(new TLBundleD(edge.bundle)))
    val mem_finish = Decoupled(new TLBundleE(edge.bundle))

    val refill = Output(new L1RefillReq())
    val meta_write = Decoupled(new L1MetaWriteReq)
    val replay = Decoupled(new ShuttleMSHRReq)
    val replay_way = Output(UInt(nWays.W))
    val wb_req = Decoupled(new WritebackReq(edge.bundle))

    val probe_rdy = Output(Bool())
    val fence_rdy = Output(Bool())
    val replay_next = Output(Bool())
    val store_pending = Output(Bool())
  })

  // determine if the request is cacheable or not
  val cacheable = edge.manager.supportsAcquireBFast(io.req.bits.addr, lgCacheBlockBytes.U)

  val sdq_val = RegInit(0.U(cfg.nSDQ.W))
  val sdq_alloc_id = PriorityEncoder(~sdq_val(cfg.nSDQ-1,0))
  val sdq_rdy = !sdq_val.andR
  val sdq_enq = io.req.valid && io.req.ready && cacheable && isWrite(io.req.bits.cmd)
  val sdq = Mem(cfg.nSDQ, new Bundle {
    val data = UInt(coreDataBits.W)
    val mask = UInt(coreDataBytes.W)
  })
  when (sdq_enq) {
    sdq(sdq_alloc_id).data := io.req_data
    sdq(sdq_alloc_id).mask := io.req_mask
  }

  val idxMatch = Wire(Vec(cfg.nMSHRs, Bool()))
  val tagList = Wire(Vec(cfg.nMSHRs, UInt(tagBits.W)))
  val addr_match = (idxMatch zip tagList).map { case (i,t) => i && t === io.req.bits.addr >> untagBits }
  assert(PopCount(addr_match) <= 1.U)

  val wbTagList = Wire(Vec(cfg.nMSHRs, UInt()))
  val refillMux = Wire(Vec(cfg.nMSHRs, new L1RefillReq))
  val meta_write_arb = Module(new Arbiter(new L1MetaWriteReq, cfg.nMSHRs))
  val wb_req_arb = Module(new Arbiter(new WritebackReq(edge.bundle), cfg.nMSHRs))
  val replay_arb = Module(new Arbiter(new ShuttleMSHRReq, cfg.nMSHRs))
  val alloc_arb = Module(new Arbiter(Bool(), cfg.nMSHRs))
  alloc_arb.io.in.foreach(_.bits := DontCare)

  var idx_match = false.B
  var pri_rdy = false.B

  io.fence_rdy := true.B
  io.probe_rdy := true.B

  val mshrs = (0 until cfg.nMSHRs) map { i =>
    val mshr = Module(new ShuttleDCacheMSHR(i))

    idxMatch(i) := mshr.io.idx_match
    tagList(i) := mshr.io.tag
    wbTagList(i) := mshr.io.wb_req.bits.tag

    alloc_arb.io.in(i).valid := mshr.io.req_pri_rdy
    mshr.io.req_pri_val := alloc_arb.io.in(i).ready

    mshr.io.req_sec_val := io.req.valid && sdq_rdy && addr_match(i)
    mshr.io.req_bits := io.req.bits
    mshr.io.req_bits.sdq_id := sdq_alloc_id
    mshr.io.probe_addr := io.probe_addr

    meta_write_arb.io.in(i) <> mshr.io.meta_write
    wb_req_arb.io.in(i) <> mshr.io.wb_req
    replay_arb.io.in(i) <> mshr.io.replay

    mshr.io.mem_grant.valid := io.mem_grant.valid && io.mem_grant.bits.source === i.U
    mshr.io.mem_grant.bits := io.mem_grant.bits
    refillMux(i) := mshr.io.refill

    pri_rdy = pri_rdy || mshr.io.req_pri_rdy
    idx_match = idx_match || mshr.io.idx_match

    when (!mshr.io.req_pri_rdy) { io.fence_rdy := false.B }
    when (!mshr.io.probe_rdy) { io.probe_rdy := false.B }

    mshr
  }

  val slot_match = (idxMatch zip mshrs).map { case (i,m) => i && m.io.meta_write.bits.way_en === io.req.bits.way_en }
  val sec_rdy = Mux1H(addr_match, mshrs.map(_.io.req_sec_rdy))

  alloc_arb.io.out.ready := io.req.valid && sdq_rdy && cacheable && !(addr_match.reduce(_||_) || slot_match.reduce(_||_))

  io.meta_write <> meta_write_arb.io.out
  io.wb_req <> wb_req_arb.io.out

  val mmio_alloc_arb = Module(new Arbiter(Bool(), nIOMSHRs))
  mmio_alloc_arb.io.in.foreach(_.bits := DontCare)
  val resp_arb = Module(new Arbiter(new ShuttleDMemResp, nIOMSHRs))

  var mmio_rdy = false.B
  io.replay_next := false.B

  val mmios = (0 until nIOMSHRs) map { i =>
    val id = cfg.nMSHRs + i
    val mshr = Module(new IOHandler(id))

    mmio_alloc_arb.io.in(i).valid := mshr.io.req.ready
    mshr.io.req.valid := mmio_alloc_arb.io.in(i).ready
    mshr.io.req.bits := io.req.bits
    mshr.io.req.bits.data := io.req_data
    mshr.io.req.bits.mask := io.req_mask

    mmio_rdy = mmio_rdy || mshr.io.req.ready

    mshr.io.mem_ack.bits := io.mem_grant.bits
    mshr.io.mem_ack.valid := io.mem_grant.valid && io.mem_grant.bits.source === id.U

    resp_arb.io.in(i) <> mshr.io.resp

    when (!mshr.io.req.ready) { io.fence_rdy := false.B }
    when (mshr.io.replay_next) { io.replay_next := true.B }

    mshr
  }

  mmio_alloc_arb.io.out.ready := io.req.valid && !cacheable

  TLArbiter.lowestFromSeq(edge, io.mem_acquire, mshrs.map(_.io.mem_acquire) ++ mmios.map(_.io.mem_access))
  TLArbiter.lowestFromSeq(edge, io.mem_finish,  mshrs.map(_.io.mem_finish))

  io.store_pending := sdq_val =/= 0.U || mmios.map(_.io.store_pending).orR

  io.resp <> resp_arb.io.out
  io.req.ready := Mux(!cacheable,
    mmio_rdy,
    sdq_rdy && Mux(addr_match.reduce(_||_), sec_rdy, Mux(slot_match.reduce(_||_), false.B, pri_rdy)))
  io.secondary_miss := addr_match.reduce(_||_) || slot_match.reduce(_||_)
  io.refill := refillMux(io.mem_grant.bits.source)

  val free_sdq = io.replay.fire && isWrite(io.replay.bits.cmd)
  io.replay.bits.data := sdq(replay_arb.io.out.bits.sdq_id).data
  io.replay.bits.mask := sdq(replay_arb.io.out.bits.sdq_id).mask
  io.replay_way := Mux1H(UIntToOH(replay_arb.io.chosen), mshrs.map(_.io.meta_write.bits.way_en))
  io.replay.valid := replay_arb.io.out.valid
  io.replay.bits := replay_arb.io.out.bits
  replay_arb.io.out.ready := io.replay.ready

  when (io.replay.valid || sdq_enq) {
    sdq_val := sdq_val & ~(UIntToOH(replay_arb.io.out.bits.sdq_id) & Fill(cfg.nSDQ, free_sdq)) |
               PriorityEncoderOH(~sdq_val(cfg.nSDQ-1,0)) & Fill(cfg.nSDQ, sdq_enq)
  }
}

