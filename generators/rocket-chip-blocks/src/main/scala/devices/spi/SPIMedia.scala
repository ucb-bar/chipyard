package sifive.blocks.devices.spi

import chisel3._ 
import chisel3.util._
import freechips.rocketchip.util._

class SPILinkIO(c: SPIParamsBase) extends SPIBundle(c) {
  val tx = Decoupled(Bits(c.frameBits.W))
  val rx = Flipped(Valid(Bits(c.frameBits.W)))

  val cnt = Output(UInt(c.countBits.W))
  val fmt = Output(new SPIFormat(c))
  val cs = new Bundle {
    val set = Output(Bool())
    val clear = Output(Bool()) // Deactivate CS
    val hold = Output(Bool()) // Supress automatic CS deactivation
  }
  val active = Input(Bool())
  val disableOE = c.oeDisableDummy.option(Output(Bool())) // disable oe during dummy cycles in flash mode
}

class SPIMedia(c: SPIParamsBase) extends Module {
  val io = IO(new Bundle {
    val port = new SPIPortIO(c)
    val ctrl = new Bundle {
      val sck = Input(new SPIClocking(c))
      val dla = Input(new SPIDelay(c))
      val cs = Input(new SPIChipSelect(c))
      val extradel = Input(new SPIExtraDelay(c))
      val sampledel = Input(new SPISampleDelay(c))
    }
    val link = Flipped(new SPILinkIO(c))
  })

  val phy = Module(new SPIPhysical(c))
  phy.io.ctrl.sck := io.ctrl.sck
  phy.io.ctrl.fmt := io.link.fmt
  phy.io.ctrl.extradel := io.ctrl.extradel
  phy.io.ctrl.sampledel := io.ctrl.sampledel

  private val op = phy.io.op
  op.valid := true.B
  op.bits.fn := SPIMicroOp.Delay
  op.bits.stb := false.B
  op.bits.cnt := io.link.cnt
  op.bits.data := io.link.tx.bits
  op.bits.disableOE.foreach(_ := io.link.disableOE.get)

  val cs = Reg(new SPIChipSelect(c))
  val cs_set = Reg(Bool())
  val cs_active = io.ctrl.cs.toggle(io.link.cs.set)
  val cs_update = (cs_active.asUInt =/= cs.dflt.asUInt)

  val clear = RegInit(false.B)
  val cs_assert = RegInit(false.B)
  val cs_deassert = clear || (cs_update && !io.link.cs.hold)

  clear := clear || (io.link.cs.clear && cs_assert)

  val continuous = (io.ctrl.dla.interxfr === 0.U)

  io.port.sck := phy.io.port.sck
  io.port.dq <> phy.io.port.dq
  io.port.cs := cs.dflt

  io.link.rx := phy.io.rx
  io.link.tx.ready := false.B
  io.link.active := cs_assert

  val (s_main :: s_interxfr :: s_intercs :: Nil) = Enum(3)
  val state = RegInit(s_main)

  switch (state) {
    is (s_main) {
      when (cs_assert) {
        when (cs_deassert) {
          op.bits.cnt := io.ctrl.dla.sckcs
          when (op.ready) {
            state := s_intercs
          }
        } .otherwise {
          op.bits.fn := SPIMicroOp.Transfer
          op.bits.stb := true.B

          op.valid := io.link.tx.valid
          io.link.tx.ready := op.ready
          when (op.fire) {
            state := s_interxfr
          }
        }
      } .elsewhen (io.link.tx.valid) {
        // Assert CS
        op.bits.cnt := io.ctrl.dla.cssck
        when (op.ready) {
          cs_assert := true.B
          cs_set := io.link.cs.set
          cs.dflt := cs_active
        }
      } .otherwise {
        // Idle
        op.bits.cnt := 0.U
        op.bits.stb := true.B
        cs := io.ctrl.cs
      }
    }

    is (s_interxfr) {
      // Skip if interxfr delay is zero
      op.valid := !continuous
      op.bits.cnt := io.ctrl.dla.interxfr
      when (op.ready || continuous) {
        state := s_main
      }
    }

    is (s_intercs) {
      // Deassert CS
      op.bits.cnt := io.ctrl.dla.intercs
      op.bits.stb := true.B
      cs_assert := false.B
      clear := false.B
      when (op.ready) {
        cs.dflt := cs.toggle(cs_set)
        state := s_main
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
