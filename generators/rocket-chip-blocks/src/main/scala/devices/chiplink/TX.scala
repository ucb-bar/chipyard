package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class TX(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c2b_clk  = Output(Clock())
    val c2b_rst  = Output(Bool())
    val c2b_send = Output(Bool())
    val c2b_data = Output(UInt(info.params.dataBits.W))
    val a = Flipped(new AsyncBundle(new DataLayer(info.params), info.params.crossing))
    val b = Flipped(new AsyncBundle(new DataLayer(info.params), info.params.crossing))
    val c = Flipped(new AsyncBundle(new DataLayer(info.params), info.params.crossing))
    val d = Flipped(new AsyncBundle(new DataLayer(info.params), info.params.crossing))
    val e = Flipped(new AsyncBundle(new DataLayer(info.params), info.params.crossing))
    val sa = Flipped(DecoupledIO(new DataLayer(info.params)))
    val sb = Flipped(DecoupledIO(new DataLayer(info.params)))
    val sc = Flipped(DecoupledIO(new DataLayer(info.params)))
    val sd = Flipped(DecoupledIO(new DataLayer(info.params)))
    val se = Flipped(DecoupledIO(new DataLayer(info.params)))
    val rxc = Flipped(new AsyncBundle(new CreditBump(info.params), AsyncQueueParams.singleton()))
    val txc = Flipped(new AsyncBundle(new CreditBump(info.params), AsyncQueueParams.singleton()))
  }

  // Currently available credits
  val rx = RegInit(CreditBump(info.params, 0))
  val tx = RegInit(CreditBump(info.params, 0))

  // Constantly pull credits from RX
  val rxInc = FromAsyncBundle(io.rxc)
  val txInc = FromAsyncBundle(io.txc)
  rxInc.ready := true.B
  txInc.ready := true.B

  // Cross the requests (if necessary)
  val sync = info.params.syncTX
  val qa = if (sync) ShiftQueue(io.sa, 2) else FromAsyncBundle(io.a)
  val qb = if (sync) ShiftQueue(io.sb, 2) else FromAsyncBundle(io.b)
  val qc = if (sync) ShiftQueue(io.sc, 2) else FromAsyncBundle(io.c)
  val qd = if (sync) ShiftQueue(io.sd, 2) else FromAsyncBundle(io.d)
  val qe = if (sync) ShiftQueue(io.se, 2) else FromAsyncBundle(io.e)
  private def qX = Seq(qa, qb, qc, qd, qe)

  // Consume TX credits and propagate pre-paid requests
  val ioX = (qX zip (tx.X zip txInc.bits.X)) map { case (q, (credit, gain)) =>
    val first = RegEnable(q.bits.last, true.B, q.fire)
    val delta = credit -& q.bits.beats
    val allow = !first || (delta.asSInt >= 0.S)
    credit := Mux(q.fire && first, delta, credit) + Mux(txInc.fire, gain, 0.U)

    val cq = Module(new ShiftQueue(q.bits.cloneType, 2)) // maybe flow?
    cq.io.enq.bits := q.bits
    cq.io.enq.valid := q.valid && allow
    q.ready := cq.io.enq.ready && allow
    cq.io.deq
  }

  // Prepare RX credit update headers
  val rxQ = Module(new ShiftQueue(new DataLayer(info.params), 2)) // maybe flow?
  val (rxHeader, rxLeft) = rx.toHeader
  rxQ.io.enq.valid := true.B 
  rxQ.io.enq.bits.data  := rxHeader
  rxQ.io.enq.bits.last  := true.B 
  rxQ.io.enq.bits.beats := 1.U
  rx := Mux(rxQ.io.enq.fire, rxLeft, rx) + Mux(rxInc.fire, rxInc.bits, CreditBump(info.params, 0))

  // Include the F credit channel in arbitration
  val f = WireDefault(rxQ.io.deq)
  val ioF = ioX :+ f
  val requests = Cat(ioF.map(_.valid).reverse)
  val lasts = Cat(ioF.map(_.bits.last).reverse)

  // How often should we force transmission of a credit update? sqrt
  val xmitBits = log2Ceil(info.params.Qdepth) / 2
  val xmit = RegInit(0.U(xmitBits.W))
  val forceXmit = xmit === 0.U
  when (!forceXmit) { xmit := xmit - 1.U }
  when (f.fire) { xmit := ~0.U(xmitBits.W) }

  // Flow control for returned credits
  val allowReturn = !ioX.map(_.valid).reduce(_ || _) || forceXmit
  f.bits  := rxQ.io.deq.bits
  f.valid := rxQ.io.deq.valid && allowReturn
  rxQ.io.deq.ready := f.ready && allowReturn

  // Select a channel to transmit from those with data and space
  val first = RegInit(true.B)
  val state = RegInit(0.U(6.W))
  val readys = TLArbiter.roundRobin(6, requests, first)
  val winner = readys & requests
  val grant = Mux(first, winner, state)
  val allowed = Mux(first, readys, state)
  (ioF zip allowed.asBools) foreach { case (beat, sel) => beat.ready := sel }

  val send = Mux(first, rxQ.io.deq.valid, (state & requests) =/= 0.U)
  assert (send === ((grant & requests) =/= 0.U))

  when (send) { first := (grant & lasts).orR }
  when (first) { state := winner }

  // Form the output beat
  io.c2b_clk  := clock
  io.c2b_rst  := AsyncResetReg(false.B, clock, reset.asBool, true, None)
  io.c2b_send := RegNext(RegNext(send, false.B), false.B)
  io.c2b_data := RegNext(Mux1H(RegNext(grant), RegNext(VecInit(ioF.map(_.bits.data)))))
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
