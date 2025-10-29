package sifive.blocks.devices.uart

import chisel3._
import chisel3.util._

import freechips.rocketchip.util._

/** UARTTx module recives TL bus data from Tx fifo in parallel and transmits them to Port(Tx).
  *
  * ==datapass==
  * TL bus -> Tx fifo -> io.in -> shifter  -> Port(Tx)
  *
  *  ==Structure==
  *  - baud rate divisor counter:
  *  generate pulse, the enable signal for data shift.
  *  - data shift logic:
  *  parallel in, serial out
  *
  * @note Tx fifo transmits TL bus data to Tx module
  */
class UARTTx(c: UARTParams) extends Module {
  val io = IO(new Bundle {
    /** Tx enable signal from top */
    val en = Input(Bool())
    /** data from Tx fifo */
    val in = Flipped(Decoupled(UInt(c.dataBits.W)))
    /** Tx port */
    val out = Output(UInt(1.W))
    /** divisor bits */
    val div = Input(UInt(c.divisorBits.W))
    /** number of stop bits */
    val nstop = Input(UInt(log2Up(c.stopBits).W))
    val tx_busy = Output(Bool())
    /** parity enable */
    val enparity = c.includeParity.option(Input(Bool()))
    /** parity select
      *
      * 0 -> even parity
      * 1 -> odd parity
      */
    val parity = c.includeParity.option(Input(Bool()))
    /** databit select
      *
      * ture -> 8
      * false -> 9
      */
    val data8or9 = (c.dataBits == 9).option(Input(Bool()))
    /** clear to sned signal */
    val cts_n = c.includeFourWire.option(Input(Bool()))
  })

  val prescaler = RegInit(0.U(c.divisorBits.W))
  val pulse = (prescaler === 0.U)

  private val n = c.dataBits + 1 + c.includeParity.toInt
  /** contains databit(8or9), start bit, stop bit and parity bit*/
  val counter = RegInit(0.U((log2Floor(n + c.stopBits) + 1).W))
  val shifter = Reg(UInt(n.W))
  val out = RegInit(1.U(1.W))
  io.out := out

  val plusarg_tx = PlusArg("uart_tx", 1, "Enable/disable the TX to speed up simulation").orR
  val plusarg_printf = PlusArg("uart_tx_printf", 0, "Enable/disable the TX printf").orR

  val busy = (counter =/= 0.U)
  io.in.ready := io.en && !busy
  io.tx_busy := busy
  when (io.in.fire && plusarg_printf) {
    printf("UART TX (%x): %c\n", io.in.bits, io.in.bits)
  }
  when (io.in.fire && plusarg_tx) {
    if (c.includeParity) {
      val includebit9 = if (c.dataBits == 9) Mux(io.data8or9.get, false.B, io.in.bits(8)) else false.B
      val parity = Mux(io.enparity.get, includebit9 ^ io.in.bits(7,0).asBools.reduce(_ ^ _) ^ io.parity.get, true.B)
      val paritywithbit9 = if (c.dataBits == 9) Mux(io.data8or9.get, Cat(1.U(1.W), parity), Cat(parity, io.in.bits(8))) 
                           else Cat(1.U(1.W), parity)
      shifter := Cat(paritywithbit9, io.in.bits(7,0), 0.U(1.W))
      counter := Mux1H((0 until c.stopBits).map(i =>
        (io.nstop === i.U) -> (n + i + 1).U)) - (!io.enparity.get).asUInt - io.data8or9.getOrElse(0.U)
      // n = max number of databits configured at elaboration + start bit + parity bit 
      // n + i + 1 = n + stop bits + pad bit(when counter === 0 no bit is transmitted)
      // n + i + 1 - 8_bit_mode(if c.dataBits == 9) - parity_disabled_at_runtime
    }
    else {
      val bit9 = if (c.dataBits == 9) Mux(io.data8or9.get, 1.U(1.W), io.in.bits(8)) else 1.U(1.W)
      shifter := Cat(bit9, io.in.bits(7,0), 0.U(1.W))
      counter := Mux1H((0 until c.stopBits).map(i =>
        (io.nstop === i.U) -> (n + i + 1).U)) - io.data8or9.getOrElse(0.U)
    }
  }
  when (busy) {
    prescaler := Mux(pulse || io.cts_n.getOrElse(false.B), io.div, prescaler - 1.U)
  }
  when (pulse && busy) {
    counter := counter - 1.U
    shifter := Cat(1.U(1.W), shifter >> 1)
    out := shifter(0)
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
