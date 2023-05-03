package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import org.chipsalliance.cde.config.{Parameters}

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}

import chipyard._
import chipyard.harness._
import chipyard.iobinders.{HasIOBinders}

class ArtyFPGATestHarness(override implicit val p: Parameters) extends ArtyShell with HasChipyardHarnessInstantiators {

  // Convert harness resets from Bool to Reset type.
  val hReset = Wire(Reset())
  hReset := ~ck_rst

  val dReset = Wire(AsyncReset())
  dReset := reset_core.asAsyncReset

  val buildtopClock = clock_32MHz
  val buildtopReset = hReset
  val success = false.B

  implicitHarnessClockBundle.clock := clock_32MHz
  implicitHarnessClockBundle.reset := hReset

  instantiateChipTops()
}
