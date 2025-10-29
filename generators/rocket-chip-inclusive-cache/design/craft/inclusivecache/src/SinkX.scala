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

class SinkXRequest(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val address = UInt(params.inner.bundle.addressBits.W)
}

class SinkX(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Decoupled(new FullRequest(params))
    val x = Flipped(Decoupled(new SinkXRequest(params)))
  })

  val x = Queue(io.x, 1)
  val (tag, set, offset) = params.parseAddress(x.bits.address)

  x.ready := io.req.ready
  io.req.valid := x.valid
  params.ccover(x.valid && !x.ready, "SINKX_STALL", "Backpressure when accepting a control message")

  io.req.bits.prio   := VecInit(1.U(3.W).asBools) // same prio as A
  io.req.bits.control:= true.B
  io.req.bits.opcode := 0.U
  io.req.bits.param  := 0.U
  io.req.bits.size   := params.offsetBits.U
  // The source does not matter, because a flush command never allocates a way.
  // However, it must be a legal source, otherwise assertions might spuriously fire.
  io.req.bits.source := params.inner.client.clients.map(_.sourceId.start).min.U
  io.req.bits.offset := 0.U
  io.req.bits.set    := set
  io.req.bits.tag    := tag
  io.req.bits.put    := 0.U
}
