package sifive.blocks.devices.gpio

import chisel3._ 
import sifive.blocks.devices.pinctrl.{Pin}

// While this is a bit pendantic, it keeps the GPIO
// device more similar to the other devices. It's not 'special'
// even though it looks like something that more directly talks to
// a pin. It also makes it possible to change the exact
// type of pad this connects to.
class GPIOSignals[T <: Data](private val pingen: () => T, private val c: GPIOParams) extends Bundle {
  val pins = Vec(c.width, pingen())
}

class GPIOPins[T <: Pin](pingen: () => T, c: GPIOParams) extends GPIOSignals[T](pingen, c)

object GPIOPinsFromPort {

  def apply[T <: Pin](pins: GPIOSignals[T], port: GPIOPortIO, clock: Clock, reset: Bool){

    // This will just match up the components of the Bundle that
    // exist in both.
    withClockAndReset(clock, reset) {
      (pins.pins zip port.pins) foreach {case (pin, port) =>
        pin <> port
      }
    }
  }

  def apply[T <: Pin](pins: GPIOSignals[T], port: GPIOPortIO){

    // This will just match up the components of the Bundle that
    // exist in both.
    (pins.pins zip port.pins) foreach {case (pin, port) =>
      pin <> port
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
