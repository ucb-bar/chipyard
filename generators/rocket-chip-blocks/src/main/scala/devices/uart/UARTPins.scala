package sifive.blocks.devices.uart

import chisel3._
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.blocks.devices.pinctrl.{Pin}

class UARTSignals[T <: Data](private val pingen: () => T, val wire4: Boolean = false) extends Bundle {
  val rxd = pingen()
  val txd = pingen()
  val cts_n = if (wire4) Option(pingen()) else None
  val rts_n = if (wire4) Option(pingen()) else None
}

class UARTPins[T <: Pin](pingen: () => T) extends UARTSignals[T](pingen)

object UARTPinsFromPort {
  def apply[T <: Pin](pins: UARTSignals[T], uart: UARTPortIO, clock: Clock, reset: Bool, syncStages: Int = 0) {
    withClockAndReset(clock, reset) {
      pins.txd.outputPin(uart.txd)
      val rxd_t = pins.rxd.inputPin()
      uart.rxd := SyncResetSynchronizerShiftReg(rxd_t, syncStages, init = true.B, name = Some("uart_rxd_sync"))
      pins.rts_n.foreach { rt => rt.outputPin(uart.rts_n.get) }
      pins.cts_n.foreach { ct => 
        val cts_t = ct.inputPin()
        uart.cts_n.get := SyncResetSynchronizerShiftReg(cts_t, syncStages, init = false.B, name = Some("uart_cts_sync"))
      }
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
