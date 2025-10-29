package sifive.blocks.devices.spi

import chisel3._ 
import chisel3.util._

class SPIInnerIO(c: SPIParamsBase) extends SPILinkIO(c) {
  val lock = Output(Bool())
}

class SPIArbiter(c: SPIParamsBase, n: Int) extends Module {
  val io = IO(new Bundle {
    val inner = Flipped(Vec(n, new SPIInnerIO(c)))
    val outer = new SPILinkIO(c)
    val sel = Input(UInt(log2Up(n).W))
  })

  val sel = RegInit(VecInit(true.B +: Seq.fill(n-1)(false.B)))

  io.outer.tx.valid := Mux1H(sel, io.inner.map(_.tx.valid))
  io.outer.tx.bits := Mux1H(sel, io.inner.map(_.tx.bits))
  io.outer.cnt := Mux1H(sel, io.inner.map(_.cnt))
  io.outer.fmt := Mux1H(sel, io.inner.map(_.fmt))
  // Workaround for overzealous combinational loop detection
  io.outer.cs := Mux(sel(0), io.inner(0).cs, io.inner(1).cs)
  io.outer.disableOE.foreach (_ := io.inner(0).disableOE.get)
  require(n == 2, "SPIArbiter currently only supports 2 clients")

  (io.inner zip sel).foreach { case (inner, s) =>
    inner.tx.ready := io.outer.tx.ready && s
    inner.rx.valid := io.outer.rx.valid && s
    inner.rx.bits := io.outer.rx.bits
    inner.active := io.outer.active && s
  }

  val nsel = VecInit.tabulate(n)(io.sel === _.U)
  val lock = Mux1H(sel, io.inner.map(_.lock))
  when (!lock) {
    sel := nsel
    when (sel.asUInt =/= nsel.asUInt) {
      io.outer.cs.clear := true.B
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
