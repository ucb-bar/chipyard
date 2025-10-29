package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._

class SinkE(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val e = Flipped(Decoupled(new TLBundleE(info.edgeIn.bundle)))
    val q = Decoupled(new DataLayer(info.params))
    // Find the sink from D
    val d_tlSink = Valid(UInt(info.params.sinkBits.W))
    val d_clSink = Input(UInt(info.params.clSinkBits.W))
  }

  io.d_tlSink.valid := io.e.fire
  io.d_tlSink.bits := io.e.bits.sink

  val header = info.encode(
    format = 4.U,
    opcode = 0.U,
    param  = 0.U,
    size   = 0.U,
    domain = 0.U,
    source = io.d_clSink)

  io.e.ready := io.q.ready
  io.q.valid := io.e.valid
  io.q.bits.last  := true.B
  io.q.bits.data  := header
  io.q.bits.beats := 1.U
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
