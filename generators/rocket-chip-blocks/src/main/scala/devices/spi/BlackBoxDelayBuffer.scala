package sifive.blocks.devices.spi

import chisel3._
import freechips.rocketchip.util.ShiftRegInit

class BlackBoxDelayBuffer extends BlackBox {
  val io = IO(new Bundle() {
  val in = Input(UInt(1.W))
  val sel = Input(UInt(5.W))
  val out = Input(UInt(1.W))
  val mux_out = Output(UInt(1.W))
  })
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
