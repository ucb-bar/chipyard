package sifive.blocks.devices.chiplink

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SourceD(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val d = Decoupled(new TLBundleD(info.edgeIn.bundle))
    val q = Flipped(Decoupled(UInt(info.params.dataBits.W)))
    // Used by E to find the txn
    val e_tlSink = Flipped(Valid(UInt(info.params.sinkBits.W)))
    val e_clSink = Output(UInt(info.params.clSinkBits.W))
  }

  // We need a sink id CAM
  val cam = Module(new CAM(info.params.sinks, info.params.clSinkBits))

  // Map ChipLink transaction to TileLink source
  val cl2tl = info.sourceMap.map(_.swap)
  val nestedMap = cl2tl.groupBy(_._1.domain).mapValues(_.map { case (TXN(_, cls), tls) => (cls, tls) })
  val muxes = Seq.tabulate(info.params.domains) { i =>
    info.mux(nestedMap.lift(i).getOrElse(Map(0 -> 0)))
  }

  // The FSM states
  val state = RegInit(0.U(2.W))
  val s_header   = 0.U(2.W)
  val s_sink     = 1.U(2.W)
  val s_data     = 2.U(2.W)

  private def hold(key: UInt)(data: UInt) = {
    val enable = state === key
    Mux(enable, data, RegEnable(data, enable))
  }

  // Extract header fields from the message
  val Seq(_, q_opcode, q_param, q_size, q_domain, q_source) =
    info.decode(io.q.bits).map(hold(s_header) _)

  // Extract sink from the optional second beat
  val q_sink = hold(s_sink)(io.q.bits(15, 0))

  val q_grant = q_opcode === TLMessages.Grant || q_opcode === TLMessages.GrantData
  val (_, q_last) = info.firstlast(io.q, Some(3.U))
  val d_first = RegEnable(state =/= s_data, io.q.fire)
  val s_maybe_data = Mux(q_last, s_header, s_data)

  when (io.q.fire) {
    switch (state) {
      is (s_header)   { state := Mux(q_grant, s_sink, s_maybe_data) }
      is (s_sink)     { state := s_maybe_data }
      is (s_data)     { state := s_maybe_data }
    }
  }

  // Look for an available sink
  val sink_ok = !q_grant || cam.io.alloc.ready
  val sink  = cam.io.key holdUnless d_first
  val stall = d_first && !sink_ok
  val xmit  = q_last || state === s_data

  io.d.bits.opcode  := q_opcode
  io.d.bits.param   := q_param(1,0)
  io.d.bits.size    := q_size
  io.d.bits.source  := VecInit(muxes.map { m => m(q_source) })(q_domain)
  io.d.bits.sink    := Mux(q_grant, sink, 0.U)
  io.d.bits.denied  := q_param >> 2
  io.d.bits.data    := io.q.bits
  io.d.bits.corrupt := io.d.bits.denied && info.edgeIn.hasData(io.d.bits)

  io.d.valid := (io.q.valid && !stall) &&  xmit
  io.q.ready := (io.d.ready && !stall) || !xmit

  cam.io.alloc.valid := q_grant && d_first && xmit && io.q.valid && io.d.ready
  cam.io.alloc.bits  := q_sink

  // Free the CAM
  io.e_clSink := cam.io.data
  cam.io.free := io.e_tlSink
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
