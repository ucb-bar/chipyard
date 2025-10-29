package sifive.blocks.util

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.util.{AsyncResetRegVec, AsyncResetReg}

class AsyncDownCounter(clock: Clock, reset: Bool, value: Int)
    extends Module () {
  val io = new Bundle {
    val done = Output(Bool())
  }
  withClockAndReset(clock = clock, reset = reset) {
    val count_next = Wire(UInt(log2Ceil(value).W))
    val count = AsyncResetReg(
      updateData = count_next,
      resetData = value,
      name = "count_reg")
    val done_reg = AsyncResetReg(
      updateData = (count === 0.U),
      resetData = 0,
      name = "done_reg")

    when (count > 0.U) {
      count_next := count - 1.U
    } .otherwise {
      count_next := count
    }

    io.done := done_reg
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
