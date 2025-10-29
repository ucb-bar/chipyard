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

// The control port response source
class SourceXRequest(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val fail = Bool()
}

class SourceX(params: InclusiveCacheParameters) extends Module
{
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new SourceXRequest(params)))
    val x = Decoupled(new SourceXRequest(params))
  })

  val x = Wire(chiselTypeOf(io.x)) // ready must not depend on valid
  io.x <> Queue(x, 1)

  io.req.ready := x.ready
  x.valid := io.req.valid
  params.ccover(x.valid && !x.ready, "SOURCEX_STALL", "Backpressure when sending a control message")

  x.bits := io.req.bits
}
