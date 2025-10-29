package sifive.blocks.devices.mockaon

import chisel3._
import chisel3.util._
import freechips.rocketchip.util._
import sifive.blocks.util.SRLatch

import sifive.blocks.util.{SlaveRegIF}

class WakeupCauses extends Bundle {
  val awakeup = Bool()
  val dwakeup = Bool()
  val rtc = Bool()
  val reset = Bool()
}

class ResetCauses extends Bundle {
  val wdogrst = Bool()
  val erst = Bool()
  val porrst = Bool()
}

class PMUSignals extends Bundle {
  val hfclkrst = Bool()
  val corerst = Bool()
  val reserved1 = Bool()
  val vddpaden = Bool()
  val reserved0 = Bool()
}

class PMUInstruction extends Bundle {
  val sigs = new PMUSignals
  val dt = UInt(4.W)
}

class PMUConfig(wakeupProgramIn: Seq[Int],
                sleepProgramIn: Seq[Int]) {
  val programLength = 8
  val nWakeupCauses = new WakeupCauses().elements.size
  val wakeupProgram = wakeupProgramIn.padTo(programLength, wakeupProgramIn.last)
  val sleepProgram = sleepProgramIn.padTo(programLength, sleepProgramIn.last)
  require(wakeupProgram.length == programLength)
  require(sleepProgram.length == programLength)
}

class DevKitPMUConfig extends PMUConfig( // TODO
  Seq(0x1f0, 0x0f8, 0x030),
  Seq(0x0f0, 0x1f0, 0x1d0, 0x1c0))

class PMURegs(c: PMUConfig) extends Bundle {
  val ie = new SlaveRegIF(c.nWakeupCauses)
  val cause = new SlaveRegIF(32)
  val sleep = new SlaveRegIF(32)
  val key = new SlaveRegIF(32)
  val wakeupProgram = Vec(c.programLength, new SlaveRegIF(32))
  val sleepProgram = Vec(c.programLength, new SlaveRegIF(32))
}

class PMUCore(c: PMUConfig)(resetIn: Bool) extends Module() {
  val io = new Bundle {
    val wakeup = Input(new WakeupCauses())
    val control = Valid(new PMUSignals)
    val resetCause = Input(UInt(log2Ceil(new ResetCauses().getWidth).W))
    val regs = new PMURegs(c)
  }
  withReset(resetIn) {
    val run = RegInit(true.B)
    val awake = RegInit(true.B)
    val unlocked = {
      val writeAny = WatchdogTimer.writeAnyExceptKey(io.regs, io.regs.key)
      RegEnable(io.regs.key.write.bits === WatchdogTimer.key.U && !writeAny, false.B, io.regs.key.write.valid || writeAny)
    }
    val wantSleep = RegEnable(true.B, false.B, io.regs.sleep.write.valid && unlocked)
    val pc = RegInit(0.U(log2Ceil(c.programLength).W))
    val wakeupCause = RegInit(0.U(log2Ceil(c.nWakeupCauses).W))
    val ie = RegEnable(io.regs.ie.write.bits, io.regs.ie.write.valid && unlocked) | 1.U /* POR always enabled */

    val insnWidth = new PMUInstruction().getWidth
    val wakeupProgram = c.wakeupProgram.map(v => RegInit(v.U(insnWidth.W)))
    val sleepProgram = c.sleepProgram.map(v => RegInit(v.U(insnWidth.W)))
    val insnBits = Mux(awake, wakeupProgram(pc), sleepProgram(pc))
    val insn = insnBits.asTypeOf(new PMUInstruction())

    val count = RegInit(0.U((1 << insn.dt.getWidth).W))
    val tick = (count ^ (count + 1.U))(insn.dt)
    val npc = pc +& 1.U
    val last = npc >= c.programLength.U
    io.control.valid := run && !last && tick
    io.control.bits := insn.sigs

    when (run) {
      count := count + 1.U
      when (tick) {
        count := 0.U

        require(isPow2(c.programLength))
        run := !last
        pc := npc
      }
    }.otherwise {
      val maskedWakeupCauses = ie & io.wakeup.asUInt
      when (!awake && maskedWakeupCauses.orR) {
        run := true.B
        awake := true.B
        wakeupCause := PriorityEncoder(maskedWakeupCauses)
      }
      when (awake && wantSleep) {
        run := true.B
        awake := false.B
        wantSleep := false.B
      }
    }

    io.regs.cause.read := wakeupCause | (io.resetCause << 8)
    io.regs.ie.read := ie
    io.regs.key.read := unlocked
    io.regs.sleep.read := 0.U

    for ((port, reg) <- (io.regs.wakeupProgram ++ io.regs.sleepProgram) zip (wakeupProgram ++ sleepProgram)) {
      port.read := reg
      when (port.write.valid && unlocked) { reg := port.write.bits }
    }
  }
}

class PMU(val c: PMUConfig) extends Module {
  val io = new Bundle {
    val wakeup = Input(new WakeupCauses())
    val control = Output(new PMUSignals())
    val regs = new PMURegs(c)
    val resetCauses = Input(new ResetCauses())
  }

  val coreReset = RegNext(RegNext(reset))
  val core = Module(new PMUCore(c)(resetIn = coreReset.asBool))

  io <> core.io
  core.io.wakeup.reset := false.B // this is implied by resetting the PMU

  // during aonrst, hold all control signals high
  val latch = ~AsyncResetReg(~core.io.control.bits.asUInt, core.io.control.valid)
  io.control := latch.asTypeOf(io.control)

  core.io.resetCause := {
    val cause = io.resetCauses.asUInt
    val latches = for (i <- 0 until cause.getWidth) yield {
      val latch = Module(new SRLatch)
      latch.io.set := cause(i)
      latch.io.reset := (0 until cause.getWidth).filter(_ != i).map(cause(_)).reduce(_||_)
      latch.io.q
    }
    OHToUInt(latches)
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
