package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._

class SinkD(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val d = Flipped(Decoupled(new TLBundleD(info.edgeOut.bundle)))
    val q = Decoupled(new DataLayer(info.params))
    val a_tlSource = Valid(UInt(info.params.sourceBits.W))
    val a_clSource = Input(UInt(info.params.clSourceBits.W))
    val c_tlSource = Valid(UInt(info.params.sourceBits.W))
    val c_clSource = Input(UInt(info.params.clSourceBits.W))
  }

  // The FSM states
  val state = RegInit(0.U(2.W))
  val s_header   = 0.U(2.W)
  val s_sink     = 1.U(2.W)
  val s_data     = 2.U(2.W)

  // We need a Q because we stall the channel while serializing it's header
  val d = Queue(io.d, 1, flow=true)
  val d_last = info.edgeOut.last(d)
  val d_hasData = info.edgeOut.hasData(d.bits)
  val d_grant = d.bits.opcode === TLMessages.Grant || d.bits.opcode === TLMessages.GrantData

  when (io.q.fire) {
    switch (state) {
      is (s_header)   { state := Mux(d_grant, s_sink, Mux(d_hasData, s_data, s_header)) }
      is (s_sink)     { state := Mux(d_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(d_last, s_header, s_data) }
    }
  }

  // Release the TL source
  val relack = d.bits.opcode === TLMessages.ReleaseAck
  io.a_tlSource.valid := io.q.fire && state === s_header && !relack
  io.a_tlSource.bits := d.bits.source
  io.c_tlSource.valid := io.q.fire && state === s_header &&  relack
  io.c_tlSource.bits := d.bits.source

  // Construct the header beat
  val header = info.encode(
    format = 3.U,
    opcode = d.bits.opcode,
    param  = Cat(d.bits.denied, d.bits.param),
    size   = d.bits.size,
    domain = d.bits.source >> log2Ceil(info.params.sourcesPerDomain),
    source = Mux(relack, io.c_clSource, io.a_clSource))

  val isLastState = state === Mux(d_hasData, s_data, Mux(d_grant, s_sink, s_header))
  d.ready := io.q.ready && isLastState
  io.q.valid := d.valid
  io.q.bits.last  := d_last && isLastState
  io.q.bits.data  := VecInit(header, d.bits.sink, d.bits.data)(state)
  io.q.bits.beats := Mux(d_hasData, info.size2beats(d.bits.size), 0.U) + 1.U + d_grant.asUInt
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
