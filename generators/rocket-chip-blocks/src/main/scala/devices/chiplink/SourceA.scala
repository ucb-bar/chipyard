package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SourceA(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val a = Decoupled(new TLBundleA(info.edgeOut.bundle))
    val q = Flipped(Decoupled(UInt(width = info.params.dataBits.W)))
    // Used by D to find the txn
    val d_tlSource = Flipped(Valid(UInt(width = info.params.sourceBits.W)))
    val d_clSource = Output(UInt(info.params.clSourceBits.W))
  }

  // CAM of sources used for each domain
  val cams = Seq.fill(info.params.domains) {
    Module(new CAM(info.params.sourcesPerDomain, info.params.clSourceBits))
  }

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
  val Seq(_, q_opcode, q_param, q_size, q_domain, q_source) =
    info.decode(io.q.bits).map(hold(s_header) _)

  // Latch address
  val q_address0 = hold(s_address0)(io.q.bits)
  val q_address1 = hold(s_address1)(io.q.bits)

  val (_, q_last) = info.firstlast(io.q, Some(0.U))
  val q_hasData = !q_opcode(2)
  val a_first = RegEnable(state =/= s_data, io.q.fire)

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
  val q_acq = q_opcode === TLMessages.AcquireBlock || q_opcode === TLMessages.AcquirePerm
  val q_write = Mux(q_acq, q_param === TLPermissions.NtoT || q_param === TLPermissions.BtoT, q_hasData)
  val exists = info.edgeOut.manager.containsSafe(q_address)
  private def writeable(m: TLManagerParameters): Boolean = if (m.supportsAcquireB) m.supportsAcquireT else m.supportsPutFull
  private def acquireable(m: TLManagerParameters): Boolean = m.supportsAcquireB || m.supportsAcquireT
  private def toBool(x: Boolean) = x.asBool
  val writeOk = info.edgeOut.manager.fastProperty(q_address, writeable, toBool)
  val acquireOk = info.edgeOut.manager.fastProperty(q_address, acquireable, toBool)
  val q_legal = exists && (!q_write || writeOk) && (!q_acq || acquireOk)

  // Look for an available source in the correct domain
  val source_ok = VecInit(cams.map(_.io.alloc.ready))(q_domain)
  val source    = VecInit(cams.map(_.io.key))(q_domain) holdUnless a_first
  val a_sel = UIntToOH(q_domain)

  // Feed our preliminary A channel via the Partial Extractor FSM
  val extract = Module(new ParitalExtractor(io.a.bits))
  io.a <> extract.io.o
  val a = extract.io.i
  extract.io.last := q_last

  a.bits.opcode  := q_opcode
  a.bits.param   := q_param
  a.bits.size    := q_size
  a.bits.source  := Cat(q_domain, source)
  a.bits.address := info.makeError(q_legal, q_address)
  a.bits.mask    := MaskGen(q_address0, q_size, info.params.dataBytes)
  a.bits.data    := io.q.bits
  a.bits.corrupt := false.B

  val stall = a_first && !source_ok
  val xmit = q_last || state === s_data
  a.valid := (io.q.valid && !stall) &&  xmit
  io.q.ready := (a.ready && !stall) || !xmit
  (cams zip a_sel.asBools) foreach { case (cam, sel) =>
    cam.io.alloc.valid := sel && a_first && xmit && io.q.valid && a.ready
    cam.io.alloc.bits  := q_source
  }

  // Free the CAM entries
  val d_clDomain = io.d_tlSource.bits >> log2Ceil(info.params.sourcesPerDomain)
  val d_sel = UIntToOH(d_clDomain)
  io.d_clSource := VecInit(cams.map(_.io.data))(d_clDomain)
  (cams zip d_sel.asBools) foreach { case (cam, sel) =>
    cam.io.free.bits  := io.d_tlSource.bits
    cam.io.free.valid := io.d_tlSource.valid && sel
  }
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
