package sifive.blocks.devices.spi

import chisel3._ 
import chisel3.util._




class SPIFlashInsn(c: SPIFlashParamsBase) extends SPIBundle(c) {
  val cmd = new Bundle with HasSPIProtocol {
    val code = Bits(c.insnCmdBits.W)
    val en = Bool()
  }
  val addr = new Bundle with HasSPIProtocol {
    val len = UInt(c.insnAddrLenBits.W)
  }
  val pad = new Bundle {
    val code = Bits(c.frameBits.W)
    val cnt = Bits(c.insnPadLenBits.W)
  }
  val data = new Bundle with HasSPIProtocol
}

class SPIFlashControl(c: SPIFlashParamsBase) extends SPIBundle(c) {
  val insn = new SPIFlashInsn(c)
  val fmt = new Bundle with HasSPIEndian
}

object SPIFlashInsn {
  def init(c: SPIFlashParamsBase): SPIFlashInsn = {
    val insn = Wire(new SPIFlashInsn(c))
    insn.cmd.en := true.B
    insn.cmd.code := 0x03.U
    insn.cmd.proto := SPIProtocol.Single
    insn.addr.len := 3.U
    insn.addr.proto := SPIProtocol.Single
    insn.pad.cnt := 0.U
    insn.pad.code := 0.U
    insn.data.proto := SPIProtocol.Single
    insn
  }
}

class SPIFlashAddr(c: SPIFlashParamsBase) extends SPIBundle(c) {
  val next = UInt(c.insnAddrBits.W)
  val hold = UInt(c.insnAddrBits.W)
}

class SPIFlashMap(c: SPIFlashParamsBase) extends Module {
  val io = IO(new Bundle {
    val en = Input(Bool())
    val ctrl = Input(new SPIFlashControl(c))
    val addr = Flipped(Decoupled(new SPIFlashAddr(c)))
    val data = Decoupled(UInt(c.frameBits.W))
    val link = new SPIInnerIO(c)
  })

  val addr = io.addr.bits.hold + 1.U
  val merge = io.link.active && (io.addr.bits.next === addr)

  private val insn = io.ctrl.insn
  io.link.tx.valid := true.B
  io.link.tx.bits := insn.cmd.code
  io.link.fmt.proto := insn.addr.proto
  io.link.fmt.iodir := SPIDirection.Tx
  io.link.fmt.endian := io.ctrl.fmt.endian
  io.link.cnt := Mux1H(
    SPIProtocol.decode(io.link.fmt.proto).zipWithIndex.map {
      case (s, i) => (s -> (c.frameBits >> i).U)
    })
  io.link.cs.set := true.B
  io.link.cs.clear := false.B
  io.link.cs.hold := true.B
  io.link.lock := true.B
  io.link.disableOE.foreach ( _ := false.B)

  io.addr.ready := false.B
  io.data.valid := false.B
  io.data.bits := io.link.rx.bits

  val cnt = Reg(UInt(math.max(c.insnPadLenBits, c.insnAddrLenBits).W))
  val cnt_en = WireDefault(false.B)
  val cnt_cmp = (0 to c.insnAddrBytes).map(cnt === _.U)
  val cnt_zero = cnt_cmp(0)
  val cnt_last = cnt_cmp(1) && io.link.tx.ready
  val cnt_done = cnt_last || cnt_zero
  when (cnt_en) {
    io.link.tx.valid := !cnt_zero
    when (io.link.tx.fire) {
      cnt := cnt - 1.U
    }
  }

  val (s_idle :: s_cmd :: s_addr :: s_pad :: s_data_pre :: s_data_post :: s_off :: Nil) = Enum(7)
  val state = RegInit(s_idle)

  switch (state) {
    is (s_idle) {
      io.link.tx.valid := false.B
      when (io.en) {
        io.addr.ready := true.B
        when (io.addr.valid) {
          when (merge) {
            state := s_data_pre
          } .otherwise {
            state := Mux(insn.cmd.en, s_cmd, s_addr)
            io.link.cs.clear := true.B
          }
        } .otherwise {
          io.link.lock := false.B
        }
      } .otherwise {
        io.addr.ready := true.B
        io.link.lock := false.B
        when (io.addr.valid) {
          state := s_off
        }
      }
    }

    is (s_cmd) {
      io.link.fmt.proto := insn.cmd.proto
      io.link.tx.bits := insn.cmd.code
      when (io.link.tx.ready) {
        state := s_addr
        cnt := insn.addr.len
      }
    }

    is (s_addr) {
      io.link.tx.bits := Mux1H(cnt_cmp.tail.zipWithIndex.map {
        case (s, i) =>
          val n = i * c.frameBits
          val m = n + (c.frameBits - 1)
          s -> io.addr.bits.hold(m, n)
      })

      cnt_en := true.B
      when (cnt_done) {
        state := s_pad
      }
    }

    is (s_pad) {
      io.link.cnt := insn.pad.cnt
      io.link.tx.bits := insn.pad.code
      io.link.disableOE.foreach(_ := true.B)
      when (io.link.tx.ready) {
        state := s_data_pre
      }
    }

    is (s_data_pre) {
      io.link.fmt.proto := insn.data.proto
      io.link.fmt.iodir := SPIDirection.Rx
      when (io.link.tx.ready) {
        state := s_data_post
      }
    }

    is (s_data_post) {
      io.link.tx.valid := false.B
      io.data.valid := io.link.rx.valid
      when (io.data.fire) {
        state := s_idle
      }
    }

    is (s_off) {
      io.data.valid := true.B
      io.data.bits := 0.U
      when (io.data.ready) {
        state := s_idle
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
