package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Parameters}

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}

import chipyard.{BuildTop, HasHarnessSignalReferences}
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

  // Default to 32MHz clock
  withClockAndReset(clock_32MHz, hReset) {
    val dut = Module(lazyDut.module)
  }

  // Set SRST_n (JTAG reset, active-low) to true unless overridden in the JTAG
  // harness binder. This is necessary because the Xilinx reset IP depends on it
  // in fpga-shells, and the simulation config does not include JTAG.
  SRST_n := true.B

  val buildtopClock = clock_32MHz
  val buildtopReset = dReset
  val dutReset = dReset
  val success = IO(Output(Bool()))

  // This will be overridden by the WithFPGASimSerial harness binder to set
  // success to the output of the sim serial module.
  success := false.B

  // must be after HasHarnessSignalReferences assignments
  lazyDut match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
}

