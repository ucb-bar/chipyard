/*
 * Copyright 2019 SiFive, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You should have received a copy of LICENSE.Apache2 along with
 * this software. If not, you may obtain a copy at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sifive.blocks.inclusivecache

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import TLMessages._
import TLAtomics._
import TLPermissions._

class SourceDRequest(params: InclusiveCacheParameters) extends FullRequest(params)
{
  val sink = UInt(params.inner.bundle.sinkBits.W)
  val way  = UInt(params.wayBits.W)
  val bad  = Bool()
}

class SourceDHazard(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val set = UInt(params.setBits.W)
  val way = UInt(params.wayBits.W)
}

class PutBufferACEntry(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val data = UInt(params.inner.bundle.dataBits.W)
  val mask = UInt((params.inner.bundle.dataBits/8).W)
  val corrupt = Bool()
}

class SourceD(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new SourceDRequest(params)))
    val d = Decoupled(new TLBundleD(params.inner.bundle))
    // Put data from SinkA
    val pb_pop = Decoupled(new PutBufferPop(params))
    val pb_beat = Flipped(new PutBufferAEntry(params))
    // Release data from SinkC
    val rel_pop  = Decoupled(new PutBufferPop(params))
    val rel_beat = Flipped(new PutBufferCEntry(params))
    // Access to the BankedStore
    val bs_radr = Decoupled(new BankedStoreInnerAddress(params))
    val bs_rdat = Flipped(new BankedStoreInnerDecoded(params))
    val bs_wadr = Decoupled(new BankedStoreInnerAddress(params))
    val bs_wdat = new BankedStoreInnerPoison(params)
    // Is it safe to evict/replace this way?
    val evict_req  = Flipped(new SourceDHazard(params))
    val evict_safe = Bool()
    val grant_req  = Flipped(new SourceDHazard(params))
    val grant_safe = Bool()
  })

  val beatBytes = params.inner.manager.beatBytes
  val writeBytes = params.micro.writeBytes

  val s1_valid = Wire(Bool())
  val s2_valid = Wire(Bool())
  val s3_valid = Wire(Bool())
  val s2_ready = Wire(Bool())
  val s3_ready = Wire(Bool())
  val s4_ready = Wire(Bool())

  ////////////////////////////////////// STAGE 1 //////////////////////////////////////
  // Reform the request beats

  val busy = RegInit(false.B)
  val s1_block_r = RegInit(false.B)
  val s1_counter = RegInit(0.U(params.innerBeatBits.W))
  val s1_req_reg = RegEnable(io.req.bits, !busy && io.req.valid)
  val s1_req = Mux(!busy, io.req.bits, s1_req_reg)
  val s1_x_bypass = Wire(UInt((beatBytes/writeBytes).W)) // might go from high=>low during stall
  val s1_latch_bypass = RegNext(!(busy || io.req.valid) || s2_ready)
  val s1_bypass = Mux(s1_latch_bypass, s1_x_bypass, RegEnable(s1_x_bypass, s1_latch_bypass))
  val s1_mask = MaskGen(s1_req.offset, s1_req.size, beatBytes, writeBytes) & ~s1_bypass
  val s1_grant = (s1_req.opcode === AcquireBlock && s1_req.param === BtoT) || s1_req.opcode === AcquirePerm
  val s1_need_r = s1_mask.orR && s1_req.prio(0) && s1_req.opcode =/= Hint && !s1_grant &&
                  (s1_req.opcode =/= PutFullData || s1_req.size < log2Ceil(writeBytes).U )
  val s1_valid_r = (busy || io.req.valid) && s1_need_r && !s1_block_r
  val s1_need_pb = Mux(s1_req.prio(0), !s1_req.opcode(2), s1_req.opcode(0)) // hasData
  val s1_single = Mux(s1_req.prio(0), s1_req.opcode === Hint || s1_grant, s1_req.opcode === Release)
  val s1_retires = !s1_single // retire all operations with data in s3 for bypass (saves energy)
  // Alternatively: val s1_retires = s1_need_pb // retire only updates for bypass (less backpressure from WB)
  val s1_beats1 = Mux(s1_single, 0.U, UIntToOH1(s1_req.size, log2Up(params.cache.blockBytes)) >> log2Ceil(beatBytes))
  val s1_beat = (s1_req.offset >> log2Ceil(beatBytes)) | s1_counter
  val s1_last = s1_counter === s1_beats1
  val s1_first = s1_counter === 0.U

  params.ccover(s1_block_r, "SOURCED_1_SRAM_HOLD", "SRAM read-out successful, but stalled by stage 2")
  params.ccover(!s1_latch_bypass, "SOURCED_1_BYPASS_HOLD", "Bypass match successful, but stalled by stage 2")
  params.ccover((busy || io.req.valid) && !s1_need_r, "SOURCED_1_NO_MODIFY", "Transaction servicable without SRAM")

  io.bs_radr.valid     := s1_valid_r
  io.bs_radr.bits.noop := false.B
  io.bs_radr.bits.way  := s1_req.way
  io.bs_radr.bits.set  := s1_req.set
  io.bs_radr.bits.beat := s1_beat
  io.bs_radr.bits.mask := s1_mask

  params.ccover(io.bs_radr.valid && !io.bs_radr.ready, "SOURCED_1_READ_STALL", "Data readout stalled")

  // Make a queue to catch BS readout during stalls
  val queue = Module(new Queue(chiselTypeOf(io.bs_rdat), 3, flow=true))
  queue.io.enq.valid := RegNext(RegNext(io.bs_radr.fire))
  queue.io.enq.bits := io.bs_rdat
  assert (!queue.io.enq.valid || queue.io.enq.ready)

  params.ccover(!queue.io.enq.ready, "SOURCED_1_QUEUE_FULL", "Filled SRAM skidpad queue completely")

  when (io.bs_radr.fire) { s1_block_r := true.B }
  when (io.req.valid) { busy := true.B }
  when (s1_valid && s2_ready) {
    s1_counter := s1_counter + 1.U
    s1_block_r := false.B
    when (s1_last) {
      s1_counter := 0.U
      busy := false.B
    }
  }

  params.ccover(s1_valid && !s2_ready, "SOURCED_1_STALL", "Stage 1 pipeline blocked")

  io.req.ready := !busy
  s1_valid := (busy || io.req.valid) && (!s1_valid_r || io.bs_radr.ready)

  ////////////////////////////////////// STAGE 2 //////////////////////////////////////
  // Fetch the request data

  val s2_latch = s1_valid && s2_ready
  val s2_full = RegInit(false.B)
  val s2_valid_pb = RegInit(false.B)
  val s2_beat = RegEnable(s1_beat, s2_latch)
  val s2_bypass = RegEnable(s1_bypass, s2_latch)
  val s2_req = RegEnable(s1_req, s2_latch)
  val s2_last = RegEnable(s1_last, s2_latch)
  val s2_need_r = RegEnable(s1_need_r, s2_latch)
  val s2_need_pb = RegEnable(s1_need_pb, s2_latch)
  val s2_retires = RegEnable(s1_retires, s2_latch)
  val s2_need_d = RegEnable(!s1_need_pb || s1_first, s2_latch)
  val s2_pdata_raw = Wire(new PutBufferACEntry(params))
  val s2_pdata = s2_pdata_raw holdUnless s2_valid_pb

  s2_pdata_raw.data    := Mux(s2_req.prio(0), io.pb_beat.data, io.rel_beat.data)
  s2_pdata_raw.mask    := Mux(s2_req.prio(0), io.pb_beat.mask, ~0.U(params.inner.manager.beatBytes.W))
  s2_pdata_raw.corrupt := Mux(s2_req.prio(0), io.pb_beat.corrupt, io.rel_beat.corrupt)

  io.pb_pop.valid := s2_valid_pb && s2_req.prio(0)
  io.pb_pop.bits.index := s2_req.put
  io.pb_pop.bits.last  := s2_last
  io.rel_pop.valid := s2_valid_pb && !s2_req.prio(0)
  io.rel_pop.bits.index := s2_req.put
  io.rel_pop.bits.last  := s2_last

  params.ccover(io.pb_pop.valid && !io.pb_pop.ready, "SOURCED_2_PUTA_STALL", "Channel A put buffer was not ready in time")
  if (!params.firstLevel)
    params.ccover(io.rel_pop.valid && !io.rel_pop.ready, "SOURCED_2_PUTC_STALL", "Channel C put buffer was not ready in time")

  val pb_ready = Mux(s2_req.prio(0), io.pb_pop.ready, io.rel_pop.ready)
  when (pb_ready) { s2_valid_pb := false.B }
  when (s2_valid && s3_ready) { s2_full := false.B }
  when (s2_latch) { s2_valid_pb := s1_need_pb }
  when (s2_latch) { s2_full := true.B }

  params.ccover(s2_valid && !s3_ready, "SOURCED_2_STALL", "Stage 2 pipeline blocked")

  s2_valid := s2_full && (!s2_valid_pb || pb_ready)
  s2_ready := !s2_full || (s3_ready && (!s2_valid_pb || pb_ready))

  ////////////////////////////////////// STAGE 3 //////////////////////////////////////
  // Send D response

  val s3_latch = s2_valid && s3_ready
  val s3_full = RegInit(false.B)
  val s3_valid_d = RegInit(false.B)
  val s3_beat = RegEnable(s2_beat, s3_latch)
  val s3_bypass = RegEnable(s2_bypass, s3_latch)
  val s3_req = RegEnable(s2_req, s3_latch)
  val s3_adjusted_opcode = Mux(s3_req.bad, Get, s3_req.opcode) // kill update when denied
  val s3_last = RegEnable(s2_last, s3_latch)
  val s3_pdata = RegEnable(s2_pdata, s3_latch)
  val s3_need_pb = RegEnable(s2_need_pb, s3_latch)
  val s3_retires = RegEnable(s2_retires, s3_latch)
  val s3_need_r = RegEnable(s2_need_r, s3_latch)
  val s3_need_bs = s3_need_pb
  val s3_acq = s3_req.opcode === AcquireBlock || s3_req.opcode === AcquirePerm

  // Collect s3's data from either the BankedStore or bypass
  // NOTE: we use the s3_bypass passed down from s1_bypass, because s2-s4 were guarded by the hazard checks and not stale
  val s3_bypass_data = Wire(UInt())
  def chunk(x: UInt): Seq[UInt] = Seq.tabulate(beatBytes/writeBytes) { i => x((i+1)*writeBytes*8-1, i*writeBytes*8) }
  def chop (x: UInt): Seq[Bool] = Seq.tabulate(beatBytes/writeBytes) { i => x(i) }
  def bypass(sel: UInt, x: UInt, y: UInt) =
    (chop(sel) zip (chunk(x) zip chunk(y))) .map { case (s, (x, y)) => Mux(s, x, y) } .asUInt
  val s3_rdata = bypass(s3_bypass, s3_bypass_data, queue.io.deq.bits.data)

  // Lookup table for response codes
  val grant = Mux(s3_req.param === BtoT, Grant, GrantData)
  val resp_opcode = VecInit(Seq(AccessAck, AccessAck, AccessAckData, AccessAckData, AccessAckData, HintAck, grant, Grant))

  // No restrictions on the type of buffer used here
  val d = Wire(chiselTypeOf(io.d))
  io.d <> params.micro.innerBuf.d(d)

  d.valid := s3_valid_d
  d.bits.opcode  := Mux(s3_req.prio(0), resp_opcode(s3_req.opcode), ReleaseAck)
  d.bits.param   := Mux(s3_req.prio(0) && s3_acq, Mux(s3_req.param =/= NtoB, toT, toB), 0.U)
  d.bits.size    := s3_req.size
  d.bits.source  := s3_req.source
  d.bits.sink    := s3_req.sink
  d.bits.denied  := s3_req.bad
  d.bits.data    := s3_rdata
  d.bits.corrupt := s3_req.bad && d.bits.opcode(0)

  queue.io.deq.ready := s3_valid && s4_ready && s3_need_r
  assert (!s3_full || !s3_need_r || queue.io.deq.valid)

  when (d.ready) { s3_valid_d := false.B }
  when (s3_valid && s4_ready) { s3_full := false.B }
  when (s3_latch) { s3_valid_d := s2_need_d }
  when (s3_latch) { s3_full := true.B }

  params.ccover(s3_valid && !s4_ready, "SOURCED_3_STALL", "Stage 3 pipeline blocked")

  s3_valid := s3_full && (!s3_valid_d || d.ready)
  s3_ready := !s3_full || (s4_ready && (!s3_valid_d || d.ready))

  ////////////////////////////////////// STAGE 4 //////////////////////////////////////
  // Writeback updated data

  val s4_latch = s3_valid && s3_retires && s4_ready
  val s4_full = RegInit(false.B)
  val s4_beat = RegEnable(s3_beat, s4_latch)
  val s4_need_r = RegEnable(s3_need_r, s4_latch)
  val s4_need_bs = RegEnable(s3_need_bs, s4_latch)
  val s4_need_pb = RegEnable(s3_need_pb, s4_latch)
  val s4_req = RegEnable(s3_req, s4_latch)
  val s4_adjusted_opcode = RegEnable(s3_adjusted_opcode, s4_latch)
  val s4_pdata = RegEnable(s3_pdata, s4_latch)
  val s4_rdata = RegEnable(s3_rdata, s4_latch)

  val atomics = Module(new Atomics(params.inner.bundle))
  atomics.io.write     := s4_req.prio(2)
  atomics.io.a.opcode  := s4_adjusted_opcode
  atomics.io.a.param   := s4_req.param
  atomics.io.a.size    := 0.U
  atomics.io.a.source  := 0.U
  atomics.io.a.address := 0.U
  atomics.io.a.mask    := s4_pdata.mask
  atomics.io.a.data    := s4_pdata.data
  atomics.io.a.corrupt := DontCare
  atomics.io.data_in   := s4_rdata

  io.bs_wadr.valid := s4_full && s4_need_bs
  io.bs_wadr.bits.noop := false.B
  io.bs_wadr.bits.way  := s4_req.way
  io.bs_wadr.bits.set  := s4_req.set
  io.bs_wadr.bits.beat := s4_beat
  io.bs_wadr.bits.mask := Cat(s4_pdata.mask.asBools.grouped(writeBytes).map(_.reduce(_||_)).toList.reverse)
  io.bs_wdat.data := atomics.io.data_out
  assert (!(s4_full && s4_need_pb && s4_pdata.corrupt), "Data poisoning unsupported")

  params.ccover(io.bs_wadr.valid && !io.bs_wadr.ready, "SOURCED_4_WRITEBACK_STALL", "Data writeback stalled")
  params.ccover(s4_req.prio(0) && s4_req.opcode === ArithmeticData && s4_req.param === MIN,  "SOURCED_4_ATOMIC_MIN",  "Evaluated a signed minimum atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === ArithmeticData && s4_req.param === MAX,  "SOURCED_4_ATOMIC_MAX",  "Evaluated a signed maximum atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === ArithmeticData && s4_req.param === MINU, "SOURCED_4_ATOMIC_MINU", "Evaluated an unsigned minimum atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === ArithmeticData && s4_req.param === MAXU, "SOURCED_4_ATOMIC_MAXU", "Evaluated an unsigned minimum atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === ArithmeticData && s4_req.param === ADD,  "SOURCED_4_ATOMIC_ADD",  "Evaluated an addition atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === LogicalData    && s4_req.param === XOR,  "SOURCED_4_ATOMIC_XOR",  "Evaluated a bitwise XOR atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === LogicalData    && s4_req.param === OR,   "SOURCED_4_ATOMIC_OR",   "Evaluated a bitwise OR atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === LogicalData    && s4_req.param === AND,  "SOURCED_4_ATOMIC_AND",  "Evaluated a bitwise AND atomic")
  params.ccover(s4_req.prio(0) && s4_req.opcode === LogicalData    && s4_req.param === SWAP, "SOURCED_4_ATOMIC_SWAP", "Evaluated a bitwise SWAP atomic")

  when (io.bs_wadr.ready || !s4_need_bs) { s4_full := false.B }
  when (s4_latch) { s4_full := true.B }

  s4_ready := !s3_retires || !s4_full || io.bs_wadr.ready || !s4_need_bs

  ////////////////////////////////////// RETIRED //////////////////////////////////////

  // Record for bypass the last three retired writebacks
  // We need 3 slots to collect what was in s2, s3, s4 when the request was in s1
  // ... you can't rely on s4 being full if bubbles got introduced between s1 and s2
  val retire = s4_full && (io.bs_wadr.ready || !s4_need_bs)

  val s5_req  = RegEnable(s4_req,  retire)
  val s5_beat = RegEnable(s4_beat, retire)
  val s5_dat  = RegEnable(atomics.io.data_out, retire)

  val s6_req  = RegEnable(s5_req,  retire)
  val s6_beat = RegEnable(s5_beat, retire)
  val s6_dat  = RegEnable(s5_dat,  retire)

  val s7_dat  = RegEnable(s6_dat,  retire)

  ////////////////////////////////////// BYPASSS //////////////////////////////////////

  // Manually retime this circuit to pull a register stage forward
  val pre_s3_req  = Mux(s3_latch, s2_req,  s3_req)
  val pre_s4_req  = Mux(s4_latch, s3_req,  s4_req)
  val pre_s5_req  = Mux(retire,   s4_req,  s5_req)
  val pre_s6_req  = Mux(retire,   s5_req,  s6_req)
  val pre_s3_beat = Mux(s3_latch, s2_beat, s3_beat)
  val pre_s4_beat = Mux(s4_latch, s3_beat, s4_beat)
  val pre_s5_beat = Mux(retire,   s4_beat, s5_beat)
  val pre_s6_beat = Mux(retire,   s5_beat, s6_beat)
  val pre_s5_dat  = Mux(retire,   atomics.io.data_out, s5_dat)
  val pre_s6_dat  = Mux(retire,   s5_dat,  s6_dat)
  val pre_s7_dat  = Mux(retire,   s6_dat,  s7_dat)
  val pre_s4_full = s4_latch || (!(io.bs_wadr.ready || !s4_need_bs) && s4_full)

  val pre_s3_4_match  = pre_s4_req.set === pre_s3_req.set && pre_s4_req.way === pre_s3_req.way && pre_s4_beat === pre_s3_beat && pre_s4_full
  val pre_s3_5_match  = pre_s5_req.set === pre_s3_req.set && pre_s5_req.way === pre_s3_req.way && pre_s5_beat === pre_s3_beat
  val pre_s3_6_match  = pre_s6_req.set === pre_s3_req.set && pre_s6_req.way === pre_s3_req.way && pre_s6_beat === pre_s3_beat

  val pre_s3_4_bypass = Mux(pre_s3_4_match, MaskGen(pre_s4_req.offset, pre_s4_req.size, beatBytes, writeBytes), 0.U)
  val pre_s3_5_bypass = Mux(pre_s3_5_match, MaskGen(pre_s5_req.offset, pre_s5_req.size, beatBytes, writeBytes), 0.U)
  val pre_s3_6_bypass = Mux(pre_s3_6_match, MaskGen(pre_s6_req.offset, pre_s6_req.size, beatBytes, writeBytes), 0.U)

  s3_bypass_data :=
    bypass(RegNext(pre_s3_4_bypass), atomics.io.data_out, RegNext(
    bypass(pre_s3_5_bypass, pre_s5_dat,
    bypass(pre_s3_6_bypass, pre_s6_dat,
                            pre_s7_dat))))

  // Detect which parts of s1 will be bypassed from later pipeline stages (s1-s4)
  // Note: we also bypass from reads ahead in the pipeline to save power
  val s1_2_match  = s2_req.set === s1_req.set && s2_req.way === s1_req.way && s2_beat === s1_beat && s2_full && s2_retires
  val s1_3_match  = s3_req.set === s1_req.set && s3_req.way === s1_req.way && s3_beat === s1_beat && s3_full && s3_retires
  val s1_4_match  = s4_req.set === s1_req.set && s4_req.way === s1_req.way && s4_beat === s1_beat && s4_full

  for (i <- 0 until 8) {
    val cover = 1.U
    val s2 = s1_2_match === cover(0)
    val s3 = s1_3_match === cover(1)
    val s4 = s1_4_match === cover(2)
    params.ccover(io.req.valid && s2 && s3 && s4, "SOURCED_BYPASS_CASE_" + i, "Bypass data from all subsets of pipeline stages")
  }

  val s1_2_bypass = Mux(s1_2_match, MaskGen(s2_req.offset, s2_req.size, beatBytes, writeBytes), 0.U)
  val s1_3_bypass = Mux(s1_3_match, MaskGen(s3_req.offset, s3_req.size, beatBytes, writeBytes), 0.U)
  val s1_4_bypass = Mux(s1_4_match, MaskGen(s4_req.offset, s4_req.size, beatBytes, writeBytes), 0.U)

  s1_x_bypass := s1_2_bypass | s1_3_bypass | s1_4_bypass

  ////////////////////////////////////// HAZARDS //////////////////////////////////////

  // SinkC, SourceC, and SinkD can never interfer with each other because their operation
  // is fully contained with an execution plan of an MSHR. That MSHR owns the entire set, so
  // there is no way for a data race.

  // However, SourceD is special. We allow it to run ahead after the MSHR and scheduler have
  // released control of a set+way. This is necessary to allow single cycle occupancy for
  // hits. Thus, we need to be careful about data hazards between SourceD and the other ports
  // of the BankedStore. We can at least compare to registers 's1_req_reg', because the first
  // cycle of SourceD falls within the occupancy of the MSHR's plan.

  // Must ReleaseData=> be interlocked? RaW hazard
  io.evict_safe :=
    (!busy    || io.evict_req.way =/= s1_req_reg.way || io.evict_req.set =/= s1_req_reg.set) &&
    (!s2_full || io.evict_req.way =/= s2_req.way     || io.evict_req.set =/= s2_req.set) &&
    (!s3_full || io.evict_req.way =/= s3_req.way     || io.evict_req.set =/= s3_req.set) &&
    (!s4_full || io.evict_req.way =/= s4_req.way     || io.evict_req.set =/= s4_req.set)

  // Must =>GrantData be interlocked? WaR hazard
  io.grant_safe :=
    (!busy    || io.grant_req.way =/= s1_req_reg.way || io.grant_req.set =/= s1_req_reg.set) &&
    (!s2_full || io.grant_req.way =/= s2_req.way     || io.grant_req.set =/= s2_req.set) &&
    (!s3_full || io.grant_req.way =/= s3_req.way     || io.grant_req.set =/= s3_req.set) &&
    (!s4_full || io.grant_req.way =/= s4_req.way     || io.grant_req.set =/= s4_req.set)

  // SourceD cannot overlap with SinkC b/c the only way inner caches could become
  // dirty such that they want to put data in via SinkC is if we Granted them permissions,
  // which must flow through the SourecD pipeline.
}
