package sifive.blocks.devices.uart

import chisel3._
import chisel3.util._

import freechips.rocketchip.util._

/** UARTRx module recivies serial input from Rx port and transmits them to Rx fifo in parallel
  *
  * ==Datapass==
  * Port(Rx) -> sample -> shifter -> Rx fifo -> TL bus
  *
  * ==Structure==
  *  - baud rate divisor counter:
  *   generate pulse, the enable signal for sample and data shift
  *  - sample counter:
  *   sample happens in middle
  *  - data counter
  *   control signals for data shift process
  *  - sample and data shift logic
  *
  * ==State Machine==
  * s_idle: detect start bit, init data_count and sample count, start pulse counter
  * s_data: data reciving
  *
  * @note Rx fifo transmits Rx data to TL bus
  */
class UARTRx(c: UARTParams) extends Module {
  val io = IO(new Bundle {
    /** enable signal from top */
    val en = Input(Bool())
    /** input data from rx port */
    val in = Input(UInt(1.W))
    /** output data to Rx fifo */
    val out = Valid(UInt(c.dataBits.W))
    /** divisor bits */
    val div = Input(UInt(c.divisorBits.W))
    /** parity enable */
    val enparity = c.includeParity.option(Input(Bool()))
    /** parity select
      *
      * 0 -> even parity
      * 1 -> odd parity
      */
    val parity = c.includeParity.option(Input(Bool()))
    /** parity error bit */
    val errorparity = c.includeParity.option(Output(Bool()))
    /** databit select
      *
      * ture -> 8
      * false -> 9
      */
    val data8or9 = (c.dataBits == 9).option(Input(Bool()))
  })

  if (c.includeParity)
    io.errorparity.get := false.B

  val debounce = RegInit(0.U(2.W))
  val debounce_max = (debounce === 3.U)
  val debounce_min = (debounce === 0.U)

  val prescaler = Reg(UInt((c.divisorBits - c.oversample + 1).W))
  val start = WireDefault(false.B)
  /** enable signal for sampling and data shifting */
  val pulse = (prescaler === 0.U)

  private val dataCountBits = log2Floor(c.dataBits+c.includeParity.toInt) + 1
  /** init = data bits(8 or 9) + parity bit(0 or 1) + start bit(1) */
  val data_count = Reg(UInt(dataCountBits.W))
  val data_last = (data_count === 0.U)
  val parity_bit = (data_count === 1.U) && io.enparity.getOrElse(false.B)
  val sample_count = Reg(UInt(c.oversample.W))
  val sample_mid = (sample_count === ((c.oversampleFactor - c.nSamples + 1) >> 1).U)
  // todo unused
  val sample_last = (sample_count === 0.U)
  /** counter for data and sample
    *
    * {{{
    * |    data_count    |   sample_count  |
    * }}}
    */
  val countdown = Cat(data_count, sample_count) - 1.U

  // Compensate for the divisor not being a multiple of the oversampling period.
  // Let remainder k = (io.div % c.oversampleFactor).
  // For the last k samples, extend the sampling delay by 1 cycle.
  val remainder = io.div(c.oversample-1, 0)
  val extend = (sample_count < remainder) // Pad head: (sample_count > ~remainder)
  /** prescaler reset signal
    *
    * conditions:
    * {{{
    * start : transmisson starts
    * pulse : returns ture every pluse counter period
    * }}}
    */
  val restore = start || pulse
  val prescaler_in = Mux(restore, io.div >> c.oversample, prescaler)
  val prescaler_next = prescaler_in - Mux(restore && extend, 0.U, 1.U)
  /** buffer for sample results */
  val sample = Reg(UInt(c.nSamples.W))
  // take the majority bit of sample buffer
  val voter = Majority(sample.asBools.toSet)
  // data buffer
  val shifter = Reg(UInt(c.dataBits.W))

  val valid = RegInit(false.B)
  valid := false.B
  io.out.valid := valid
  io.out.bits := (if (c.dataBits == 8) shifter else Mux(io.data8or9.get, Cat(0.U, shifter(8,1)), shifter))

  val (s_idle :: s_data :: Nil) = Enum(2)
  val state = RegInit(s_idle)

  switch (state) {
    is (s_idle) {
      // todo !(!io.in)?
      when (!(!io.in) && !debounce_min) {
        debounce := debounce - 1.U
      }
      when (!io.in) {
        debounce := debounce + 1.U
        when (debounce_max) {
          state := s_data
          start := true.B
          prescaler := prescaler_next
          // init data_count
          data_count := (c.dataBits+1).U + (if (c.includeParity) io.enparity.get else 0.U) - io.data8or9.getOrElse(false.B).asUInt
          // init sample_count = 15
          sample_count := (c.oversampleFactor - 1).U
        }
      }
    }

    is (s_data) {
      prescaler := prescaler_next
      when (pulse) {
        // sample scan in
        sample := Cat(sample, io.in)
        data_count := countdown >> c.oversample
        sample_count := countdown(c.oversample-1, 0)

        when (sample_mid) {
          if (c.includeParity) {
            // act according to frame bit stage at its respective sampling point
            // check parity bit for error
            when (parity_bit) {
              io.errorparity.get := (shifter.asBools.reduce(_ ^ _) ^ voter ^ io.parity.get)
            }
            when (data_last) {
              state := s_idle
              valid := true.B
            } .elsewhen (!parity_bit) {
              // do not add parity bit to final rx data
              shifter := Cat(voter, shifter >> 1)
            }
          } else {
            when (data_last) {
              state := s_idle
              valid := true.B
            } .otherwise {
              shifter := Cat(voter, shifter >> 1)
            }
          }
        }
      }
    }
  }

  when (!io.en) {
    debounce := 0.U
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
