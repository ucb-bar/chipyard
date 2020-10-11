package chipyard.fpga.arty

import chisel3._
import chisel3.experimental.{Analog}
import scala.collection.mutable.{ArrayBuffer}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Field, Parameters}
import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}
import chipyard.{BuildTop, HasHarnessSignalReferences, HasTestHarnessFunctions}
import chipyard.harness.{ApplyHarnessBinders, HarnessBinders}

class ArtyFPGATestHarness(override implicit val p: Parameters) extends ArtyShell with HasHarnessSignalReferences {

  val ldut = LazyModule(p(BuildTop)(p)).suggestName("chiptop")

  // turn IO clock into Reset type
  val hReset = Wire(Reset())
  hReset := ck_rst

  // default to 32MHz clock
  withClockAndReset(clock_32MHz, hReset) {
    val dut = Module(ldut.module)
  }

  val harnessClock = clock_32MHz
  val harnessReset = hReset
  val success = false.B
  val dutReset = reset_core

  // must be after HasHarnessSignalReferences assignments
  ldut match { case d: HasTestHarnessFunctions =>
    d.harnessFunctions.foreach(_(this))
    ApplyHarnessBinders(this, d.lazySystem, p(HarnessBinders), d.portMap.toMap)
  }
}

