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

class QueuedRequest(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val prio   = Vec(3, Bool()) // A=001, B=010, C=100
  val control= Bool() // control command
  val opcode = UInt(3.W)
  val param  = UInt(3.W)
  val size   = UInt(params.inner.bundle.sizeBits.W)
  val source = UInt(params.inner.bundle.sourceBits.W)
  val tag    = UInt(params.tagBits.W)
  val offset = UInt(params.offsetBits.W)
  val put    = UInt(params.putBits.W)
}

class FullRequest(params: InclusiveCacheParameters) extends QueuedRequest(params)
{
  val set = UInt(params.setBits.W)
}

class AllocateRequest(params: InclusiveCacheParameters) extends FullRequest(params)
{
  val repeat = Bool() // set is the same
}
