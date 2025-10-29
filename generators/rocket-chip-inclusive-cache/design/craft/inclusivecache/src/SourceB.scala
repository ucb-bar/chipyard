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

class SourceBRequest(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val param   = UInt(3.W)
  val tag     = UInt(params.tagBits.W)
  val set     = UInt(params.setBits.W)
  val clients = UInt(params.clientBits.W)
}

class SourceB(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new SourceBRequest(params)))
    val b = Decoupled(new TLBundleB(params.inner.bundle))
  })

  if (params.firstLevel) {
    // Tie off unused ports
    io.req.ready := true.B
    io.b.valid := false.B
    io.b.bits := DontCare
  } else {
    val remain = RegInit(0.U(params.clientBits.W))
    val remain_set = WireInit(init = 0.U(params.clientBits.W))
    val remain_clr = WireInit(init = 0.U(params.clientBits.W))
    remain := (remain | remain_set) & ~remain_clr

    val busy = remain.orR
    val todo = Mux(busy, remain, io.req.bits.clients)
    val next = ~(leftOR(todo) << 1) & todo

    if (params.clientBits > 1) {
      params.ccover(PopCount(remain) > 1.U, "SOURCEB_MULTI_PROBE", "Had to probe more than one client")
    }

    assert (!io.req.valid || io.req.bits.clients =/= 0.U)

    io.req.ready := !busy
    when (io.req.fire) { remain_set := io.req.bits.clients }

    // No restrictions on the type of buffer used here
    val b = Wire(chiselTypeOf(io.b))
    io.b <> params.micro.innerBuf.b(b)

    b.valid := busy || io.req.valid
    when (b.fire) { remain_clr := next }
    params.ccover(b.valid && !b.ready, "SOURCEB_STALL", "Backpressured when issuing a probe")

    val tag = Mux(!busy, io.req.bits.tag, RegEnable(io.req.bits.tag, io.req.fire))
    val set = Mux(!busy, io.req.bits.set, RegEnable(io.req.bits.set, io.req.fire))
    val param = Mux(!busy, io.req.bits.param, RegEnable(io.req.bits.param, io.req.fire))

    b.bits.opcode  := TLMessages.Probe
    b.bits.param   := param
    b.bits.size    := params.offsetBits .U
    b.bits.source  := params.clientSource(next)
    b.bits.address := params.expandAddress(tag, set, 0.U)
    b.bits.mask    := ~0.U(params.inner.manager.beatBytes.W)
    b.bits.data    := 0.U
    b.bits.corrupt := false.B
  }
}
