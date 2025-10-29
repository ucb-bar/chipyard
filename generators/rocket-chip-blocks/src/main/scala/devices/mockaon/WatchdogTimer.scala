package sifive.blocks.devices.mockaon

import chisel3._
import chisel3.util._
import freechips.rocketchip.util.AsyncResetReg
import freechips.rocketchip.regmapper.{RegFieldDesc}

import sifive.blocks.util.{SlaveRegIF, GenericTimer, GenericTimerIO, GenericTimerCfgRegIFC, DefaultGenericTimerCfgDescs}

object WatchdogTimer {
  def writeAnyExceptKey(regs: Bundle, keyReg: SlaveRegIF): Bool = {
    regs.elements.values.filter(_ ne keyReg).map({
      case c: GenericTimerCfgRegIFC => c.anyWriteValid
      case v: Vec[SlaveRegIF] @unchecked => v.map(_.write.valid).reduce(_||_)
      case s: SlaveRegIF => s.write.valid
    }).reduce(_||_)
  }

  val key = 0x51F15E
}

class WatchdogTimer extends Module with GenericTimer {
  protected def prefix = "wdog"
  protected def countWidth = 31
  protected def cmpWidth = 16
  protected def ncmp = 1
  override protected def maxcmp: Int = 1
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.countAlways, io.regs.cfg.write_countAlways && unlocked)(0)
  override protected lazy val countAwake = AsyncResetReg(io.regs.cfg.write.running, io.regs.cfg.write_running && unlocked)(0)
  protected lazy val countEn = {
    val corerstSynchronized = RegNext(RegNext(io.corerst))
    countAlways || (countAwake && !corerstSynchronized)
  }
  override protected lazy val rsten = AsyncResetReg(io.regs.cfg.write.sticky, io.regs.cfg.write_sticky && unlocked)(0.U)
  protected lazy val ip = RegEnable(VecInit(Seq(io.regs.cfg.write.ip(0) || elapsed(0))), (io.regs.cfg.write_ip(0) && unlocked) || elapsed(0))
  override protected lazy val unlocked = {
    val writeAny = WatchdogTimer.writeAnyExceptKey(io.regs, io.regs.key)
    AsyncResetReg(io.regs.key.write.bits === WatchdogTimer.key.U && !writeAny, io.regs.key.write.valid || writeAny)(0)
  }
  protected lazy val feed = {
    val food = 0xD09F00D
    unlocked && io.regs.feed.write.valid && io.regs.feed.write.bits === food.U
  }

  // The Scala Type-Chekcher seems to have a bug and I get a null pointer during the Scala compilation
  // if I don't do this temporary assignment.
  val tmpStickyDesc =  RegFieldDesc("wdogrsten", "Controls whether the comparator output can set the wdogrst bit and hence cause a full reset.",
      reset = Some(0))

  override protected lazy val cfg_desc = DefaultGenericTimerCfgDescs("wdog", ncmp).copy(
    sticky = tmpStickyDesc,
    deglitch = RegFieldDesc.reserved,
    running = RegFieldDesc("wdogcoreawake", "Increment the watchdog counter if the processor is not asleep", reset=Some(0)),
    center = Seq.fill(ncmp){RegFieldDesc.reserved},
    extra = Seq.fill(ncmp){RegFieldDesc.reserved},
    gang = Seq.fill(ncmp){RegFieldDesc.reserved}
  )

  class WatchDogTimerIO extends GenericTimerIO(regWidth, ncmp, maxcmp, scaleWidth, countWidth, cmpWidth) {
    val corerst = Input(Bool())
    val rst = Output(Bool())
  }
  lazy val io = IO(new WatchDogTimerIO)
  io.rst := AsyncResetReg(true.B, rsten && elapsed(0))
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
