package sifive.blocks.devices.i2c

import chisel3._
import chisel3.{withClockAndReset}
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.blocks.devices.pinctrl.{Pin, PinCtrl}

class I2CSignals[T <: Data](private val pingen: () => T) extends Bundle {
  val scl: T = pingen()
  val sda: T = pingen()
}

class I2CPins[T <: Pin](pingen: () => T) extends I2CSignals[T](pingen)

object I2CPinsFromPort {

  def apply[T <: Pin](pins: I2CSignals[T], i2c: I2CPort, clock: Clock, reset: Bool, syncStages: Int = 0) = {
    withClockAndReset(clock, reset) {
      pins.scl.outputPin(i2c.scl.out, pue=true.B, ie = true.B)
      pins.scl.o.oe := i2c.scl.oe
      i2c.scl.in := SyncResetSynchronizerShiftReg(pins.scl.i.ival, syncStages, init = true.B,
        name = Some("i2c_scl_sync"))

      pins.sda.outputPin(i2c.sda.out, pue=true.B, ie = true.B)
      pins.sda.o.oe := i2c.sda.oe
      i2c.sda.in := SyncResetSynchronizerShiftReg(pins.sda.i.ival, syncStages, init = true.B,
        name = Some("i2c_sda_sync"))
    }
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
