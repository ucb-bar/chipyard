package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SourceC(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c = Decoupled(new TLBundleC(info.edgeOut.bundle))
    val q = Flipped(Decoupled(UInt(info.params.dataBits.W)))
    // Used by D to find the txn
    val d_tlSource = Flipped(Valid(UInt(info.params.sourceBits.W)))
    val d_clSource = Output(UInt(info.params.clSourceBits.W))
  }

  // CAM of sources used for release
  val cam = Module(new CAM(info.params.sourcesPerDomain, info.params.clSourceBits))

  // A simple FSM to generate the packet components
  val state = RegInit(0.U(2.W))
  val s_header   = 0.U(2.W)
  val s_address0 = 1.U(2.W)
  val s_address1 = 2.U(2.W)
  val s_data     = 3.U(2.W)

  private def hold(key: UInt)(data: UInt) = {
    val enable = state === key
    Mux(enable, data, RegEnable(data, enable))
  }

  // Extract header fields
  val Seq(_, q_opcode, q_param, q_size, _, q_source) =
    info.decode(io.q.bits).map(hold(s_header) _)

  // Latch address
  val q_address0 = hold(s_address0)(io.q.bits)
  val q_address1 = hold(s_address1)(io.q.bits)

  val (_, q_last) = info.firstlast(io.q, Some(2.U))
  val q_hasData = q_opcode(0)
  val c_first = RegEnable(state =/= s_data, io.q.fire)

  when (io.q.fire) {
    switch (state) {
      is (s_header)   { state := s_address0 }
      is (s_address0) { state := s_address1 }
      is (s_address1) { state := Mux(q_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!q_last,   s_data, s_header) }
    }
  }

  // Determine if the request is legal. If not, route to error device.
  val q_address = Cat(q_address1, q_address0)
  val exists = info.edgeOut.manager.containsSafe(q_address)
  private def writeable(m: TLManagerParameters): Boolean = if (m.supportsAcquireB) m.supportsAcquireT else m.supportsPutFull
  private def acquireable(m: TLManagerParameters): Boolean = m.supportsAcquireB || m.supportsAcquireT
  private def toBool(x: Boolean) = x.B
  val writeOk = info.edgeOut.manager.fastProperty(q_address, writeable, toBool)
  val acquireOk = info.edgeOut.manager.fastProperty(q_address, acquireable, toBool)
  val q_legal = exists && (!q_hasData || writeOk) && acquireOk

  // Look for an available source in the correct domain
  val q_release = q_opcode === TLMessages.Release || q_opcode === TLMessages.ReleaseData
  val source_ok = !q_release || cam.io.alloc.ready
  val source    = cam.io.key holdUnless c_first

  io.c.bits.opcode  := q_opcode
  io.c.bits.param   := q_param
  io.c.bits.size    := q_size
  io.c.bits.source  := Mux(q_release, source, 0.U) // always domain 0
  io.c.bits.address := info.makeError(q_legal, q_address)
  io.c.bits.data    := io.q.bits
  io.c.bits.corrupt := false.B

  val stall = c_first && !source_ok
  val xmit = q_last || state === s_data
  io.c.valid := (io.q.valid && !stall) &&  xmit
  io.q.ready := (io.c.ready && !stall) || !xmit
  cam.io.alloc.valid := q_release && c_first && xmit && io.q.valid && io.c.ready
  cam.io.alloc.bits  := q_source

  // Free the CAM entries
  io.d_clSource := cam.io.data
  cam.io.free := io.d_tlSource
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
