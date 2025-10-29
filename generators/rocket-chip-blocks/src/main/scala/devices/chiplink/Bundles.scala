package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util.{UIntToOH, OHToUInt, Cat}
import freechips.rocketchip.util.{rightOR}

class WideDataLayerPortLane(val params: ChipLinkParams) extends Bundle {
  val clk  = Output(Clock())
  val rst  = Output(Bool())
  val send = Output(Bool())
  val data = Output(UInt(params.dataBits.W))
}

class WideDataLayerPort(val params: ChipLinkParams) extends Bundle {
  val c2b = new WideDataLayerPortLane(params)
  val b2c = Flipped(new WideDataLayerPortLane(params))
}

class DataLayer(val params: ChipLinkParams) extends Bundle {
  val data = Output(UInt(params.dataBits.W))
  val last = Output(Bool())
  val beats = Output(UInt((params.xferBits + 1).W))
}

class CreditBump(val params: ChipLinkParams) extends Bundle {
  val a = Output(UInt(params.creditBits.W))
  val b = Output(UInt(params.creditBits.W))
  val c = Output(UInt(params.creditBits.W))
  val d = Output(UInt(params.creditBits.W))
  val e = Output(UInt(params.creditBits.W))
  def X: Seq[UInt] = Seq(a, b, c, d, e)

  // saturating addition
  def +(that: CreditBump): CreditBump = {
    val out = Wire(new CreditBump(params))
    (out.X zip (X zip that.X)) foreach { case (o, (x, y)) =>
      val z = x +& y
      o := Mux((z >> params.creditBits).orR, ~0.U(params.creditBits.W), z)
    }
    out
  }

  // Send the MSB of the credits
  def toHeader: (UInt, CreditBump) = {
    def msb(x: UInt) = {
      val mask = rightOR(x) >> 1
      val msbOH = ~(~x | mask)
      val msb = OHToUInt(msbOH << 1, params.creditBits + 1) // 0 = 0, 1 = 1, 2 = 4, 3 = 8, ...
      val pad = (msb | 0.U(5.W))(4,0)
      (pad, x & mask)
    }
    val (a_msb, a_rest) = msb(a)
    val (b_msb, b_rest) = msb(b)
    val (c_msb, c_rest) = msb(c)
    val (d_msb, d_rest) = msb(d)
    val (e_msb, e_rest) = msb(e)
    val header = Cat(
      e_msb, d_msb, c_msb, b_msb, a_msb,
      0.U(4.W), // padding
      5.U(3.W))

    val out = Wire(new CreditBump(params))
    out.a := a_rest
    out.b := b_rest
    out.c := c_rest
    out.d := d_rest
    out.e := e_rest
    (header, out)
  }
}

object CreditBump {
  def apply(params: ChipLinkParams, x: Int): CreditBump = {
    val v = x.U(params.creditBits.W)
    val out = Wire(new CreditBump(params))
    out.X.foreach { _ := v }
    out
  }

  def apply(params: ChipLinkParams, header: UInt): CreditBump = {
    def convert(x: UInt) =
      Mux(x > params.creditBits.U,
          ~0.U(params.creditBits.W),
          UIntToOH(x, params.creditBits + 1) >> 1)
    val out = Wire(new CreditBump(params))
    out.a := convert(header(11,  7))
    out.b := convert(header(16, 12))
    out.c := convert(header(21, 17))
    out.d := convert(header(26, 22))
    out.e := convert(header(31, 27))
    out
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
