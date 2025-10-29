package sifive.blocks.devices.jtag

import chisel3._ 

// ------------------------------------------------------------
// SPI, UART, etc are with their respective packages,
// JTAG doesn't really correspond directly to a device, but it does
// define pins as those devices do.
// ------------------------------------------------------------

import org.chipsalliance.cde.config._
import freechips.rocketchip.jtag.{JTAGIO}
import sifive.blocks.devices.pinctrl.{Pin, PinCtrl}

class JTAGSignals[T <: Data](val pingen: () => T, val hasTRSTn: Boolean = true) extends Bundle {
  val TCK         = pingen()
  val TMS         = pingen()
  val TDI         = pingen()
  val TDO        = pingen()
  val TRSTn = if (hasTRSTn) Option(pingen()) else None
}

class JTAGPins[T <: Pin](pingen: () => T, hasTRSTn: Boolean = true) extends JTAGSignals[T](pingen, hasTRSTn)

object JTAGPinsFromPort {

  def apply[T <: Pin] (pins: JTAGSignals[T], jtag: JTAGIO): Unit = {
    jtag.TCK  := pins.TCK.inputPin (pue = true.B).asClock
    jtag.TMS  := pins.TMS.inputPin (pue = true.B)
    jtag.TDI  := pins.TDI.inputPin(pue = true.B)
    jtag.TRSTn.foreach{t => t := pins.TRSTn.get.inputPin(pue = true.B)}

    pins.TDO.outputPin(jtag.TDO.data)
    pins.TDO.o.oe := jtag.TDO.driven
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
