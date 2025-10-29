package sifive.blocks.devices.mockaon

import chisel3._ 
import org.chipsalliance.cde.config.Field
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.devices.tilelink.CanHavePeripheryCLINT
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem.{BaseSubsystem, CBUS}
import freechips.rocketchip.tilelink.{TLAsyncCrossingSource, TLFragmenter}
import freechips.rocketchip.util.{ResetCatchAndSync, SynchronizerShiftReg}

case object PeripheryMockAONKey extends Field[MockAONParams]

trait HasPeripheryMockAON extends CanHavePeripheryCLINT with HasPeripheryDebug { this: BaseSubsystem =>
  // We override the clock & Reset here so that all synchronizers, etc
  // are in the proper clock domain.
  val mockAONParams= p(PeripheryMockAONKey)
  private val tlbus = locateTLBusWrapper(CBUS)
  val aon = LazyModule(new MockAONWrapper(tlbus.beatBytes, mockAONParams))
  aon.node := tlbus.coupleTo("aon") { TLAsyncCrossingSource() := TLFragmenter(tlbus) := _ }
  ibus.fromAsync := aon.intnode
}

trait HasPeripheryMockAONBundle {
  val aon: MockAONWrapperBundle
  def coreResetCatchAndSync(core_clock: Clock) = {
    ResetCatchAndSync(core_clock, aon.rsts.corerst, 20)
  }
}

trait HasPeripheryMockAONModuleImp extends LazyModuleImp with HasPeripheryMockAONBundle {
  val outer: HasPeripheryMockAON
  val aon = IO(new MockAONWrapperBundle)

  aon <> outer.aon.module.io

  // Explicit clock & reset are unused in MockAONWrapper.
  // Tie  to check this assumption.
  outer.aon.module.clock := false.B.asClock
  outer.aon.module.reset := true.B

  // Synchronize the external toggle into the clint
  val rtc_sync = SynchronizerShiftReg(outer.aon.module.io.rtc.asUInt.asBool, 3, Some("rtc"))
  val rtc_last = RegNext(rtc_sync, false.B)
  val rtc_tick = RegNext(rtc_sync & (~rtc_last), false.B)

  outer.clintOpt.foreach { clint =>
    clint.module.io.rtcTick := rtc_tick
  }

  outer.aon.module.io.ndreset := outer.debugOpt.map(d => d.module.io.ctrl.ndreset).getOrElse(false.B)
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
