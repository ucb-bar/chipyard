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

class SinkCResponse(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val last   = Bool()
  val set    = UInt(params.setBits.W)
  val tag    = UInt(params.tagBits.W)
  val source = UInt(params.inner.bundle.sourceBits.W)
  val param  = UInt(3.W)
  val data   = Bool()
}

class PutBufferCEntry(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val data = UInt(params.inner.bundle.dataBits.W)
  val corrupt = Bool()
}

class SinkC(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Decoupled(new FullRequest(params)) // Release
    val resp = Valid(new SinkCResponse(params)) // ProbeAck
    val c = Flipped(Decoupled(new TLBundleC(params.inner.bundle)))
    // Find 'way' via MSHR CAM lookup
    val set = UInt(params.setBits.W)
    val way = Flipped(UInt(params.wayBits.W))
    // ProbeAck write-back
    val bs_adr = Decoupled(new BankedStoreInnerAddress(params))
    val bs_dat = new BankedStoreInnerPoison(params)
    // SourceD sideband
    val rel_pop  = Flipped(Decoupled(new PutBufferPop(params)))
    val rel_beat = new PutBufferCEntry(params)
  })

  if (params.firstLevel) {
    // Tie off unused ports
    io.req.valid := false.B
    io.req.bits := DontCare
    io.resp.valid := false.B
    io.resp.bits := DontCare
    io.c.ready := true.B
    io.set := 0.U
    io.bs_adr.valid := false.B
    io.bs_adr.bits := DontCare
    io.bs_dat := DontCare
    io.rel_pop.ready := true.B
    io.rel_beat := DontCare
  } else {
    // No restrictions on the type of buffer
    val c = params.micro.innerBuf.c(io.c)

    val (tag, set, offset) = params.parseAddress(c.bits.address)
    val (first, last, _, beat) = params.inner.count(c)
    val hasData = params.inner.hasData(c.bits)
    val raw_resp = c.bits.opcode === TLMessages.ProbeAck || c.bits.opcode === TLMessages.ProbeAckData
    val resp = Mux(c.valid, raw_resp, RegEnable(raw_resp, c.valid))

    // Handling of C is broken into two cases:
    //   ProbeAck
    //     if hasData, must be written to BankedStore
    //     if last beat, trigger resp
    //   Release
    //     if first beat, trigger req
    //     if hasData, go to putBuffer
    //     if hasData && first beat, must claim a list

    assert (!(c.valid && c.bits.corrupt), "Data poisoning unavailable")

    io.set := Mux(c.valid, set, RegEnable(set, c.valid)) // finds us the way

    // Cut path from inner C to the BankedStore SRAM setup
    //   ... this makes it easier to layout the L2 data banks far away
    val bs_adr = Wire(chiselTypeOf(io.bs_adr))
    io.bs_adr <> Queue(bs_adr, 1, pipe=true)
    io.bs_dat.data   := RegEnable(c.bits.data,    bs_adr.fire)
    bs_adr.valid     := resp && (!first || (c.valid && hasData))
    bs_adr.bits.noop := !c.valid
    bs_adr.bits.way  := io.way
    bs_adr.bits.set  := io.set
    bs_adr.bits.beat := Mux(c.valid, beat, RegEnable(beat + bs_adr.ready.asUInt, c.valid))
    bs_adr.bits.mask := ~0.U(params.innerMaskBits.W)
    params.ccover(bs_adr.valid && !bs_adr.ready, "SINKC_SRAM_STALL", "Data SRAM busy")

    io.resp.valid := resp && c.valid && (first || last) && (!hasData || bs_adr.ready)
    io.resp.bits.last   := last
    io.resp.bits.set    := set
    io.resp.bits.tag    := tag
    io.resp.bits.source := c.bits.source
    io.resp.bits.param  := c.bits.param
    io.resp.bits.data   := hasData

    val putbuffer = Module(new ListBuffer(ListBufferParameters(new PutBufferCEntry(params), params.relLists, params.relBeats, false)))
    val lists = RegInit(0.U(params.relLists.W))

    val lists_set = WireInit(init = 0.U(params.relLists.W))
    val lists_clr = WireInit(init = 0.U(params.relLists.W))
    lists := (lists | lists_set) & ~lists_clr

    val free = !lists.andR
    val freeOH = ~(leftOR(~lists) << 1) & ~lists
    val freeIdx = OHToUInt(freeOH)

    val req_block = first && !io.req.ready
    val buf_block = hasData && !putbuffer.io.push.ready
    val set_block = hasData && first && !free

    params.ccover(c.valid && !raw_resp && req_block, "SINKC_REQ_STALL", "No MSHR available to sink request")
    params.ccover(c.valid && !raw_resp && buf_block, "SINKC_BUF_STALL", "No space in putbuffer for beat")
    params.ccover(c.valid && !raw_resp && set_block, "SINKC_SET_STALL", "No space in putbuffer for request")

    c.ready := Mux(raw_resp, !hasData || bs_adr.ready, !req_block && !buf_block && !set_block)

    io.req.valid := !resp && c.valid && first && !buf_block && !set_block
    putbuffer.io.push.valid := !resp && c.valid && hasData && !req_block && !set_block
    when (!resp && c.valid && first && hasData && !req_block && !buf_block) { lists_set := freeOH }

    val put = Mux(first, freeIdx, RegEnable(freeIdx, first))

    io.req.bits.prio   := VecInit(4.U(3.W).asBools)
    io.req.bits.control:= false.B
    io.req.bits.opcode := c.bits.opcode
    io.req.bits.param  := c.bits.param
    io.req.bits.size   := c.bits.size
    io.req.bits.source := c.bits.source
    io.req.bits.offset := offset
    io.req.bits.set    := set
    io.req.bits.tag    := tag
    io.req.bits.put    := put

    putbuffer.io.push.bits.index := put
    putbuffer.io.push.bits.data.data    := c.bits.data
    putbuffer.io.push.bits.data.corrupt := c.bits.corrupt

    // Grant access to pop the data
    putbuffer.io.pop.bits := io.rel_pop.bits.index
    putbuffer.io.pop.valid := io.rel_pop.fire
    io.rel_pop.ready := putbuffer.io.valid(io.rel_pop.bits.index(log2Ceil(params.relLists)-1,0))
    io.rel_beat := putbuffer.io.data

    when (io.rel_pop.fire && io.rel_pop.bits.last) {
      lists_clr := UIntToOH(io.rel_pop.bits.index, params.relLists)
    }
  }
}
