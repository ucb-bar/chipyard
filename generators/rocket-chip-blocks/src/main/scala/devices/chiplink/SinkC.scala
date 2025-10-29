package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._

class SinkC(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c = Flipped(Decoupled(new TLBundleC(info.edgeIn.bundle)))
    val q = Decoupled(new DataLayer(info.params))
  }

  // Map TileLink sources to ChipLink sources+domain
  val tl2cl = info.sourceMap
  val source = info.mux(tl2cl.mapValues(_.source).toMap)
  val domain = info.mux(tl2cl.mapValues(_.domain).toMap)

  // We need a Q because we stall the channel while serializing it's header
  val c = Queue(io.c, 1, flow=true)
  val c_last = info.edgeIn.last(c)
  val c_hasData = info.edgeIn.hasData(c.bits)
  val c_release = c.bits.opcode === TLMessages.Release || c.bits.opcode === TLMessages.ReleaseData

  // A simple FSM to generate the packet components
  val state = RegInit(0.U(2.W))
  val s_header   = 0.U(2.W)
  val s_address0 = 1.U(2.W)
  val s_address1 = 2.U(2.W)
  val s_data     = 3.U(2.W)

  when (io.q.fire) {
    switch (state) {
      is (s_header)   { state := s_address0 }
      is (s_address0) { state := s_address1 }
      is (s_address1) { state := Mux(c_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!c_last,   s_data, s_header) }
    }
  }

  // Construct the header beat
  val header = info.encode(
    format = 2.U,
    opcode = c.bits.opcode,
    param  = c.bits.param,
    size   = c.bits.size,
    domain = 0.U, // only caches (unordered) can release
    source = Mux(c_release, source(c.bits.source), 0.U))

  assert (!c.valid || domain(c.bits.source) === 0.U)

  // Construct the address beats
  val address0 = c.bits.address
  val address1 = c.bits.address >> 32

  // Frame the output packet
  val isLastState = state === Mux(c_hasData, s_data, s_address1)
  c.ready := io.q.ready && isLastState
  io.q.valid := c.valid
  io.q.bits.last  := c_last && isLastState
  io.q.bits.data  := VecInit(header, address0, address1, c.bits.data)(state)
  io.q.bits.beats := Mux(c_hasData, info.size2beats(c.bits.size), 0.U) + 3.U
}

/*
   Copyright 2016 SiFive, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
