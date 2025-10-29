package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class ParitalExtractor[T <: TLDataChannel](gen: T) extends Module
{
  val io = new Bundle {
    val last = Input(Bool())
    val i = Flipped(Decoupled(gen))
    val o = Decoupled(gen)
  }

  io.o <> io.i

  // Grab references to the fields we care about
  val (i_opcode, i_data) = io.i.bits match {
    case a: TLBundleA => (a.opcode, a.data)
    case b: TLBundleB => (b.opcode, b.data)
  }
  val (o_data, o_mask) = io.o.bits match {
    case a: TLBundleA => (a.data, a.mask)
    case b: TLBundleB => (b.data, b.mask)
  }

  val state  = RegInit(0.U(4.W)) // number of nibbles; [0,8]
  val shift  = Reg(UInt(32.W))
  val enable = i_opcode === TLMessages.PutPartialData
  val empty  = state === 0.U

  when (enable) {
    val wide = shift | (i_data << (state << 2))
    o_data := VecInit.tabulate(4) { i => wide(9*(i+1)-1, 9*i+1) } .asUInt
    o_mask := VecInit.tabulate(4) { i => wide(9*i) } .asUInt

    // Swallow beat if we have no nibbles
    when (empty) {
      io.i.ready := true.B
      io.o.valid := false.B
    }

    // Update the FSM
    when (io.i.fire) {
      shift := Mux(empty, i_data, wide >> 36)
      state := state - 1.U
      when (empty)   { state := 8.U }
      when (io.last) { state := 0.U }
    }
  }
}

class PartialInjector[T <: TLDataChannel](gen: T) extends Module
{
  val io = new Bundle {
    val i_last = Input(Bool())
    val o_last = Output(Bool())
    val i = Flipped(Decoupled(gen))
    val o = Decoupled(gen)
  }

  io.o <> io.i

  // Grab references to the fields we care about
  val (i_opcode, i_data, i_mask) = io.i.bits match {
    case a: TLBundleA => (a.opcode, a.data, a.mask)
    case b: TLBundleB => (b.opcode, b.data, b.mask)
  }
  val o_data = io.o.bits match {
    case a: TLBundleA => a.data
    case b: TLBundleB => b.data
  }

  val state = RegInit(0.U(4.W)) // number of nibbles; [0,8]
  val shift = RegInit(0.U(32.W))
  val full  = state(3)
  val partial = i_opcode === TLMessages.PutPartialData

  val last = RegInit(false.B)
  io.o_last := Mux(partial, last, io.i_last)

  when (partial) {
    val bytes = Seq.tabulate(4) { i => i_data(8*(i+1)-1, 8*i) }
    val bits  = i_mask.asBools
    val mixed = Cat(Seq(bits, bytes).transpose.flatten.reverse)
    val wide  = shift | (mixed << (state << 2))
    o_data := wide

    // Inject a beat
    when ((io.i_last || full) && !last) {
      io.i.ready := false.B
    }

    // Update the FSM
    when (io.o.fire) {
      shift := wide >> 32
      state := state + 1.U
      when (full || last) {
        state := 0.U
        shift := 0.U
      }
      last := io.i_last && !last
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
