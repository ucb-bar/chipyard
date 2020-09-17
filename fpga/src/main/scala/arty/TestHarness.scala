package chipyard.fpga.arty

import chisel3._
import chisel3.experimental.{Analog}

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Parameters}

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}

import chipyard.{BuildTop, HasHarnessSignalReferences}

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
  ldut.harnessFunctions.foreach(_(this))
}

