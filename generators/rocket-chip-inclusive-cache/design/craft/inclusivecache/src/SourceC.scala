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

class SourceCRequest(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val opcode = UInt(3.W)
  val param  = UInt(3.W)
  val source = UInt(params.outer.bundle.sourceBits.W)
  val tag    = UInt(params.tagBits.W)
  val set    = UInt(params.setBits.W)
  val way    = UInt(params.wayBits.W)
  val dirty  = Bool()
}

class SourceC(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new SourceCRequest(params)))
    val c = Decoupled(new TLBundleC(params.outer.bundle))
    // BankedStore port
    val bs_adr = Decoupled(new BankedStoreOuterAddress(params))
    val bs_dat = Flipped(new BankedStoreOuterDecoded(params))
    // RaW hazard
    val evict_req = new SourceDHazard(params)
    val evict_safe = Flipped(Bool())
  })

  // We ignore the depth and pipe is useless here (we have to provision for worst-case=stall)
  require (!params.micro.outerBuf.c.pipe)

  val beatBytes = params.outer.manager.beatBytes
  val beats = params.cache.blockBytes / beatBytes
  val flow = params.micro.outerBuf.c.flow
  val queue = Module(new Queue(chiselTypeOf(io.c.bits), beats + 3 + (if (flow) 0 else 1), flow = flow))

  // queue.io.count is far too slow
  val fillBits = log2Up(beats + 4)
  val fill = RegInit(0.U(fillBits.W))
  val room = RegInit(true.B)
  when (queue.io.enq.fire =/= queue.io.deq.fire) {
    fill := fill + Mux(queue.io.enq.fire, 1.U, ~0.U(fillBits.W))
    room := fill === 0.U || ((fill === 1.U || fill === 2.U) && !queue.io.enq.fire)
  }
  assert (room === queue.io.count <= 1.U)

  val busy = RegInit(false.B)
  val beat = RegInit(0.U(params.outerBeatBits.W))
  val last = if (params.cache.blockBytes == params.outer.manager.beatBytes) true.B else (beat === ~(0.U(params.outerBeatBits.W)))
  val req  = Mux(!busy, io.req.bits, RegEnable(io.req.bits, !busy && io.req.valid))
  val want_data = busy || (io.req.valid && room && io.req.bits.dirty)

  io.req.ready := !busy && room

  io.evict_req.set := req.set
  io.evict_req.way := req.way

  io.bs_adr.valid := (beat.orR || io.evict_safe) && want_data
  io.bs_adr.bits.noop := false.B
  io.bs_adr.bits.way  := req.way
  io.bs_adr.bits.set  := req.set
  io.bs_adr.bits.beat := beat
  io.bs_adr.bits.mask := ~0.U(params.outerMaskBits.W)

  params.ccover(io.req.valid && io.req.bits.dirty && room && !io.evict_safe, "SOURCEC_HAZARD", "Prevented Eviction data hazard with backpressure")
  params.ccover(io.bs_adr.valid && !io.bs_adr.ready, "SOURCEC_SRAM_STALL", "Data SRAM busy")

  when (io.req.valid && room && io.req.bits.dirty) { busy := true.B }
  when (io.bs_adr.fire) {
    beat := beat + 1.U
    when (last) {
      busy := false.B
      beat := 0.U
    }
  }

  val s2_latch = Mux(want_data, io.bs_adr.fire, io.req.fire)
  val s2_valid = RegNext(s2_latch)
  val s2_req = RegEnable(req, s2_latch)
  val s2_beat = RegEnable(beat, s2_latch)
  val s2_last = RegEnable(last, s2_latch)

  val s3_latch = s2_valid
  val s3_valid = RegNext(s3_latch)
  val s3_req = RegEnable(s2_req, s3_latch)
  val s3_beat = RegEnable(s2_beat, s3_latch)
  val s3_last = RegEnable(s2_last, s3_latch)

  val c = Wire(chiselTypeOf(io.c))
  c.valid        := s3_valid
  c.bits.opcode  := s3_req.opcode
  c.bits.param   := s3_req.param
  c.bits.size    := params.offsetBits.U
  c.bits.source  := s3_req.source
  c.bits.address := params.expandAddress(s3_req.tag, s3_req.set, 0.U)
  c.bits.data    := io.bs_dat.data
  c.bits.corrupt := false.B

  // We never accept at the front-end unless we're sure things will fit
  assert(!c.valid || c.ready)
  params.ccover(!c.ready, "SOURCEC_QUEUE_FULL", "Eviction queue fully utilized")

  queue.io.enq <> c
  io.c <> queue.io.deq
}
