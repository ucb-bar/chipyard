package sifive.blocks.devices.mockaon

import chisel3._ 
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.diplomacy._

import sifive.blocks.util.GenericTimer

case class MockAONParams(
    address: BigInt = BigInt(0x10000000),
    nBackupRegs: Int = 16) {
  def size: Int = 0x1000
  def regBytes: Int = 4
  def wdogOffset: Int = 0
  def rtcOffset: Int = 0x40
  def backupRegOffset: Int = 0x80
  def pmuOffset: Int = 0x100
}

class MockAONPMUIO extends Bundle {
  val vddpaden = Output(Bool())
  val dwakeup = Input(Bool())
}

class MockAONMOffRstIO extends Bundle {
  val hfclkrst = Output(Bool())
  val corerst = Output(Bool())
}

trait HasMockAONBundleContents extends Bundle {

  // Output of the Power Management Sequencer
  val moff = new MockAONMOffRstIO

  // This goes out to wrapper
  // to be combined to create aon_rst.
  val wdog_rst = Output(Bool())

  // This goes out to wrapper
  // and comes back as our clk
  val lfclk = Output(Clock())

  val pmu = new MockAONPMUIO

  val lfextclk = Input(Clock())

  val resetCauses = Input(new ResetCauses())
}

class TLMockAON(w: Int, c: MockAONParams)(implicit p: Parameters)
  extends RegisterRouter(RegisterRouterParams("aon", Seq("sifive,aon0"), c.address, beatBytes=w, size=c.size, concurrency=1))(p)
    with HasTLControlRegMap
    with HasInterruptSources {
  override def nInterrupts = 2
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val params = c
    val io = IO(new Bundle with HasMockAONBundleContents)
    // the expectation here is that Chisel's implicit reset is aonrst,
    // which is asynchronous, so don't use synchronous-reset registers.

    val rtc = Module(new RTC)

    val pmu = Module(new PMU(new DevKitPMUConfig))
    io.moff <> pmu.io.control
    io.pmu.vddpaden := pmu.io.control.vddpaden
    pmu.io.wakeup.dwakeup := io.pmu.dwakeup
    pmu.io.wakeup.awakeup := false.B
    pmu.io.wakeup.rtc := rtc.io.ip(0)
    pmu.io.resetCauses := io.resetCauses
    val pmuRegMap = {
      val regs = pmu.io.regs.wakeupProgram ++ pmu.io.regs.sleepProgram ++
      Seq(pmu.io.regs.ie, pmu.io.regs.cause, pmu.io.regs.sleep, pmu.io.regs.key)
      for ((r, i) <- regs.zipWithIndex)
      yield (c.pmuOffset + c.regBytes*i) -> Seq(r.toRegField())
    }
    interrupts(1) := rtc.io.ip(0)

    val wdog = Module(new WatchdogTimer)
    io.wdog_rst := wdog.io.rst
    wdog.io.corerst := pmu.io.control.corerst
    interrupts(0) := wdog.io.ip(0)

    // If there are multiple lfclks to choose from, we can mux them here.
    io.lfclk := io.lfextclk

    val backupRegs = Seq.fill(c.nBackupRegs)(Reg(UInt((c.regBytes * 8).W)))
    val backupRegMap =
      for ((reg, i) <- backupRegs.zipWithIndex)
      yield (c.backupRegOffset + c.regBytes*i) -> Seq(RegField(reg.getWidth, RegReadFn(reg), RegWriteFn(reg)))

    regmap((backupRegMap ++
      GenericTimer.timerRegMap(wdog, c.wdogOffset, c.regBytes) ++
      GenericTimer.timerRegMap(rtc, c.rtcOffset, c.regBytes) ++
      pmuRegMap):_*)


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
