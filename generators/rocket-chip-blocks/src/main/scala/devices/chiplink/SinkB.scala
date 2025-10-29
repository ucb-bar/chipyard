package sifive.blocks.devices.chiplink

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._

class SinkB(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val b = Flipped(Decoupled(new TLBundleB(info.edgeOut.bundle)))
    val q = Decoupled(new DataLayer(info.params))
  }

  // We need a Q because we stall the channel while serializing it's header
  val inject = Module(new PartialInjector(io.b.bits))
  inject.io.i <> Queue(io.b, 1, flow=true)
  inject.io.i_last := info.edgeOut.last(inject.io.i)
  val b = inject.io.o
  val b_last = inject.io.o_last
  val b_hasData = info.edgeOut.hasData(b.bits)
  val b_partial = b.bits.opcode === TLMessages.PutPartialData

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
      is (s_address1) { state := Mux(b_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!b_last,   s_data, s_header) }
    }
  }

  // Construct the header beat
  val header = info.encode(
    format = 1.U,
    opcode = b.bits.opcode,
    param  = b.bits.param,
    size   = b.bits.size,
    domain = 0.U, // ChipLink only allows one remote cache, in domain 0
    source = 0.U)

  assert (!b.valid || b.bits.source === 0.U)

  // Construct the address beats
  val address0 = b.bits.address
  val address1 = b.bits.address >> 32

  // Frame the output packet
  val isLastState = state === Mux(b_hasData, s_data, s_address1)
  b.ready := io.q.ready && isLastState
  io.q.valid := b.valid
  io.q.bits.last  := b_last && isLastState
  io.q.bits.data  := VecInit(header, address0, address1, b.bits.data)(state)
  io.q.bits.beats := Mux(b_hasData, info.size2beats(b.bits.size), 0.U) + 3.U +
                     Mux(b_partial, info.mask2beats(b.bits.size), 0.U)
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
