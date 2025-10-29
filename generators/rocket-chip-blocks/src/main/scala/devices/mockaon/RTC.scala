package sifive.blocks.devices.mockaon

import chisel3._ 
import freechips.rocketchip.util.AsyncResetReg
import freechips.rocketchip.regmapper.RegFieldDesc

import sifive.blocks.util.{SlaveRegIF, GenericTimer, GenericTimerIO, DefaultGenericTimerCfgDescs}

class RTC extends Module with GenericTimer {

  protected def prefix = "rtc"
  protected def countWidth = 48
  protected def cmpWidth = 32
  protected def ncmp = 1
  protected def countEn = countAlways
  override protected lazy val ip = RegNext(elapsed)
  override protected lazy val zerocmp = false.B
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.countAlways, io.regs.cfg.write_countAlways && unlocked)(0)
  protected lazy val feed = false.B

  override protected lazy val feed_desc = RegFieldDesc.reserved
  override protected lazy val key_desc = RegFieldDesc.reserved
  override protected lazy val cfg_desc = DefaultGenericTimerCfgDescs("rtc", ncmp).copy(
    sticky = RegFieldDesc.reserved,
    zerocmp = RegFieldDesc.reserved,
    deglitch = RegFieldDesc.reserved,
    running = RegFieldDesc.reserved,
    center = Seq.fill(ncmp){ RegFieldDesc.reserved },
    extra = Seq.fill(ncmp){ RegFieldDesc.reserved },
    gang = Seq.fill(ncmp){ RegFieldDesc.reserved }
  )

  lazy val io = IO(new GenericTimerIO(regWidth, ncmp, maxcmp, scaleWidth, countWidth, cmpWidth))

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
