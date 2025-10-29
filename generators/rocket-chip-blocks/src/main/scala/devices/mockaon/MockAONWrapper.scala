package sifive.blocks.devices.mockaon

import chisel3._ 
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.interrupts._
import sifive.blocks.devices.pinctrl.{EnhancedPin}
import sifive.blocks.util.{DeglitchShiftRegister}

/* The wrapper handles the Clock and Reset Generation for The AON block itself,
 and instantiates real pad controls (aka pull-ups)*/

class MockAONWrapperPMUIO extends Bundle {
  val dwakeup_n = new EnhancedPin()
  val vddpaden = new  EnhancedPin()
}

class MockAONWrapperPins extends Bundle {
  val erst_n   = new EnhancedPin()
  val lfextclk = new EnhancedPin()
  val pmu = new MockAONWrapperPMUIO()
}

class MockAONWrapperBundle extends Bundle {
  val pins = new MockAONWrapperPins()
  val rsts = new MockAONMOffRstIO()
}

class MockAONWrapper(w: Int, c: MockAONParams)(implicit p: Parameters) extends LazyModule {

  val aon = LazyModule(new TLMockAON(w, c))

  // We only need to isolate the signals
  // coming from MOFF to AON,
  // since AON is never off while MOFF is on.
  // The MOFF is on the "in" side of the Isolation.
  // AON is on the "out" side of the Isolation.

  def isoOut(iso: Bool, x: UInt): UInt = IsoZero(iso, x)
  def isoIn(iso: Bool, x: UInt): UInt = x
  val isolation = LazyModule(new TLIsolation(fOut = isoOut, fIn = isoIn))
  val crossing = LazyModule(new TLAsyncCrossingSink(AsyncQueueParams.singleton()))

  val node = aon.node := crossing.node := isolation.node

  // crossing lives outside in Periphery
  val intnode = aon.intXing(alreadyRegistered = true)

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new MockAONWrapperBundle {
      val rtc  = Output(Clock())
      val ndreset = Input(Bool())
    })

    val aon_io = aon.module.io
    val pins = io.pins

    // -----------------------------------------------
    // Generation of aonrst
    // -----------------------------------------------

    // ERST
    val erst = ~pins.erst_n.inputPin(pue = true.B)
    aon_io.resetCauses.erst := erst
    aon_io.resetCauses.wdogrst := aon_io.wdog_rst

    // PORRST
    val porrst = false.B // TODO
    aon_io.resetCauses.porrst := porrst

    //--------------------------------------------------
    // Drive "Mostly Off" Reset Signals (these
    // are synchronized inside MOFF as needed)
    //--------------------------------------------------

    io.rsts.hfclkrst := aon_io.moff.hfclkrst
    io.rsts.corerst  := aon_io.moff.corerst

    //--------------------------------------------------
    // Generate the LFCLK input to AON
    // This is the same clock that is driven to this
    // block as 'clock'.
    //--------------------------------------------------

    // LFCLK Override
    // Note that the actual mux lives inside AON itself.
    // Therefore, the lfclk which comes out of AON is the
    // true clock that AON and AONWrapper are running off of.
    val lfextclk = pins.lfextclk.inputPin(pue=true.B)
    aon_io.lfextclk := lfextclk.asClock

    // Drive AON's clock and Reset
    val lfclk = aon_io.lfclk

    val aonrst_catch = Module (new ResetCatchAndSync(3))
    aonrst_catch.reset := erst | aon_io.wdog_rst | io.ndreset
    aonrst_catch.clock := lfclk
    aon.module.reset := aonrst_catch.io.sync_reset

    aon.module.clock := lfclk

    //--------------------------------------------------
    // TL2 Register Access Interface
    //--------------------------------------------------

    // Safely cross TL2 into AON Domain
    // Ensure that both are reset and clocked
    // at the same time.
    // Note that aon.moff.corerst is synchronous
    // to aon.module.clock, so this is safe.
    val crossing_slave_reset  = ResetCatchAndSync(lfclk,
      aon.module.io.moff.corerst | aon.module.reset.asBool)

    crossing.module.clock := lfclk
    crossing.module.reset := crossing_slave_reset

    // Note that aon.moff.corerst is synchronous
    // to aon.module.clock, so this is safe.
    isolation.module.io.iso_out := aon.module.io.moff.corerst
    isolation.module.io.iso_in  := true.B

    //--------------------------------------------------
    // PMU <--> pins Interface
    //--------------------------------------------------

    val dwakeup_n_async = pins.pmu.dwakeup_n.inputPin(pue=true.B)

    val dwakeup_deglitch = Module (new DeglitchShiftRegister(3))
    dwakeup_deglitch.clock := lfclk
    dwakeup_deglitch.io.d := ~dwakeup_n_async
    aon.module.io.pmu.dwakeup := dwakeup_deglitch.io.q

    pins.pmu.vddpaden.outputPin(aon.module.io.pmu.vddpaden)

    //--------------------------------------------------
    // Connect signals to MOFF
    //--------------------------------------------------

    io.rtc := aon_io.lfclk
  }

}

// -----------------------------------------------
// Isolation Cells
// -----------------------------------------------

class IsoZero extends Module {
  val io = new Bundle {
    val in = Input(Bool())
    val iso = Input(Bool())
    val out = Output(Bool())
  }
  io.out := io.in & ~io.iso
}

object IsoZero {
  def apply (iso: Bool, in: UInt): UInt = {

    val w = in.getWidth
    val isos: List[IsoZero] = List.tabulate(in.getWidth)(
      x => Module(new IsoZero).suggestName(s"iso_$x")
    )
    for ((z, i) <- isos.zipWithIndex) {
      z.io.in := in(i)
      z.io.iso := iso
    }
    isos.map(_.io.out).asUInt
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
