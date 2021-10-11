package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Parameters}

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}

import chipyard.{BuildTop, HasHarnessSignalReferences, HasTestHarnessFunctions}
import chipyard.harness.{ApplyHarnessBinders}
import chipyard.iobinders.{HasIOBinders}

import testchipip.{SerialTLKey}

class ArtyFPGATestHarness(override implicit val p: Parameters) extends ArtyShell with HasHarnessSignalReferences {

  val lazyDut = LazyModule(p(BuildTop)(p)).suggestName("chiptop")

  // Convert harness resets from Bool to Reset type.
  val hReset = Wire(Reset())
  hReset := ck_rst

  val dReset = Wire(AsyncReset())
  dReset := reset_core.asAsyncReset

  // default to 32MHz clock
  withClockAndReset(clock_32MHz, hReset) {
    val dut = Module(lazyDut.module)
  }

  // set SRST_n (JTAG reset, active-low) to true unless overridden in the JTAG
  // harness binder. This is necessary because the Xilinx reset IP depends on it
  // in fpga-shells, and the simulation config does not include JTAG.
  SRST_n := true.B

  val harnessClock = clock_32MHz
  val harnessReset = hReset
  val success = Wire(Bool())
  val dutReset = dReset
  val io_success = IO(Output(Bool()))
  io_success := success
  success := false.B

  // must be after HasHarnessSignalReferences assignments
  lazyDut match { case d: HasTestHarnessFunctions =>
    d.harnessFunctions.foreach(_(this))
  }
  lazyDut match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
}

