package sifive.blocks.util

import chisel3._ 
import chisel3.util._

//Allows us to specify a different clock for a shift register
// and to force input to be high for > 1 cycle.
class DeglitchShiftRegister(shift: Int) extends Module {
  val io = new Bundle {
    val d = Input(Bool())
    val q = Output(Bool())
  }
  val sync = ShiftRegister(io.d, shift)
  val last = ShiftRegister(sync, 1)
  io.q := sync & last
}

object DeglitchShiftRegister {
  def apply (shift: Int, d: Bool, clock: Clock,
    name: Option[String] = None): Bool = {
    val deglitch = Module (new DeglitchShiftRegister(shift))
    name.foreach(deglitch.suggestName(_))
    deglitch.clock := clock
    deglitch.reset := false.B
    deglitch.io.d := d
    deglitch.io.q
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
