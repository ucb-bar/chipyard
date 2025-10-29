package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SourceE(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val e = Decoupled(new TLBundleE(info.edgeOut.bundle))
    val q = Flipped(Decoupled(UInt(info.params.dataBits.W)))
  }

  // Extract header fields
  val Seq(_, _, _, _, _, q_sink) = info.decode(io.q.bits)

  io.q.ready := io.e.ready
  io.e.valid := io.q.valid
  io.e.bits.sink := q_sink
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
