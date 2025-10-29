package sifive.blocks.devices.spi

import chisel3._
import chisel3.experimental.dataview._
import chisel3.util.{Decoupled, Queue, Mux1H}

class SPIFIFOControl(c: SPIParamsBase) extends SPIBundle(c) {
  val fmt = new SPIFormat(c) with HasSPILength
  val cs = new Bundle with HasSPICSMode
  val wm = new SPIWatermark(c)
}

class SPIFIFO(c: SPIParamsBase) extends Module {
  val io = IO(new Bundle {
    val ctrl = Input(new SPIFIFOControl(c))
    val link = new SPIInnerIO(c)
    val tx = Flipped(Decoupled(Bits(c.frameBits.W)))
    val rx = Decoupled(Bits(c.frameBits.W))
    val ip = Output(new SPIInterrupts())
  })

  val txq = Module(new Queue(UInt(c.frameBits.W), c.txDepth))
  val rxq = Module(new Queue(UInt(c.frameBits.W), c.rxDepth))

  txq.io.enq <> io.tx
  io.link.tx <> txq.io.deq

  val fire_tx = io.link.tx.fire
  val fire_rx = io.link.rx.fire
  val rxen = RegInit(false.B)

  rxq.io.enq.valid := io.link.rx.valid && rxen
  rxq.io.enq.bits := io.link.rx.bits
  io.rx <> rxq.io.deq

  when (fire_rx) {
    rxen := false.B
  }
  when (fire_tx) {
    rxen := (io.link.fmt.iodir === SPIDirection.Rx)
  }

  val proto = SPIProtocol.decode(io.link.fmt.proto).zipWithIndex
  val cnt_quot = Mux1H(proto.map { case (s, i) => s -> (io.ctrl.fmt.len >> i) })
  val cnt_rmdr = Mux1H(proto.map { case (s, i) => s -> (if (i > 0) io.ctrl.fmt.len(i-1, 0).orR else 0.U) })
  io.link.fmt <> io.ctrl.fmt.viewAsSupertype(new SPIFormat(c))
  io.link.cnt := cnt_quot + cnt_rmdr

  val cs_mode = RegNext(io.ctrl.cs.mode, SPICSMode.Auto)
  val cs_mode_hold = (cs_mode === SPICSMode.Hold)
  val cs_mode_off = (cs_mode === SPICSMode.Off)
  val cs_update = (cs_mode =/= io.ctrl.cs.mode)
  val cs_clear = !(cs_mode_hold || cs_mode_off)

  io.link.cs.set := !cs_mode_off
  io.link.cs.clear := cs_update || (fire_tx && cs_clear)
  io.link.cs.hold := false.B

  io.link.lock := io.link.tx.valid || rxen

  io.ip.txwm := (txq.io.count < io.ctrl.wm.tx)
  io.ip.rxwm := (rxq.io.count > io.ctrl.wm.rx)
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
