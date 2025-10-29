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

class PutBufferAEntry(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val data = UInt(params.inner.bundle.dataBits.W)
  val mask = UInt((params.inner.bundle.dataBits/8).W)
  val corrupt = Bool()
}

class PutBufferPop(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val index = UInt(params.putBits.W)
  val last = Bool()
}

class SinkA(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Decoupled(new FullRequest(params))
    val a = Flipped(Decoupled(new TLBundleA(params.inner.bundle)))
    // for use by SourceD:
    val pb_pop  = Flipped(Decoupled(new PutBufferPop(params)))
    val pb_beat = new PutBufferAEntry(params)
  })

  // No restrictions on the type of buffer
  val a = params.micro.innerBuf.a(io.a)

  val putbuffer = Module(new ListBuffer(ListBufferParameters(new PutBufferAEntry(params), params.putLists, params.putBeats, false)))
  val lists = RegInit((0.U(params.putLists.W)))

  val lists_set = WireInit(init = 0.U(params.putLists.W))
  val lists_clr = WireInit(init = 0.U(params.putLists.W))
  lists := (lists | lists_set) & ~lists_clr

  val free = !lists.andR
  val freeOH = ~(leftOR(~lists) << 1) & ~lists
  val freeIdx = OHToUInt(freeOH)

  val first = params.inner.first(a)
  val hasData = params.inner.hasData(a.bits)

  // We need to split the A input to three places:
  //   If it is the first beat, it must go to req
  //   If it has Data, it must go to the putbuffer
  //   If it has Data AND is the first beat, it must claim a list

  val req_block = first && !io.req.ready
  val buf_block = hasData && !putbuffer.io.push.ready
  val set_block = hasData && first && !free

  params.ccover(a.valid && req_block, "SINKA_REQ_STALL", "No MSHR available to sink request")
  params.ccover(a.valid && buf_block, "SINKA_BUF_STALL", "No space in putbuffer for beat")
  params.ccover(a.valid && set_block, "SINKA_SET_STALL", "No space in putbuffer for request")

  a.ready := !req_block && !buf_block && !set_block
  io.req.valid := a.valid && first && !buf_block && !set_block
  putbuffer.io.push.valid := a.valid && hasData && !req_block && !set_block
  when (a.valid && first && hasData && !req_block && !buf_block) { lists_set := freeOH }

  val (tag, set, offset) = params.parseAddress(a.bits.address)
  val put = Mux(first, freeIdx, RegEnable(freeIdx, first))

  io.req.bits.prio   := VecInit(1.U(3.W).asBools)
  io.req.bits.control:= false.B
  io.req.bits.opcode := a.bits.opcode
  io.req.bits.param  := a.bits.param
  io.req.bits.size   := a.bits.size
  io.req.bits.source := a.bits.source
  io.req.bits.offset := offset
  io.req.bits.set    := set
  io.req.bits.tag    := tag
  io.req.bits.put    := put

  putbuffer.io.push.bits.index := put
  putbuffer.io.push.bits.data.data    := a.bits.data
  putbuffer.io.push.bits.data.mask    := a.bits.mask
  putbuffer.io.push.bits.data.corrupt := a.bits.corrupt

  // Grant access to pop the data
  putbuffer.io.pop.bits := io.pb_pop.bits.index
  putbuffer.io.pop.valid := io.pb_pop.fire
  io.pb_pop.ready := putbuffer.io.valid(io.pb_pop.bits.index)
  io.pb_beat := putbuffer.io.data

  when (io.pb_pop.fire && io.pb_pop.bits.last) {
    lists_clr := UIntToOH(io.pb_pop.bits.index, params.putLists)
  }
}
