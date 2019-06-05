package beagle

import chisel3._
import chisel3.util.{Cat, ShiftRegister}
import chisel3.experimental.{MultiIOModule, RawModule}

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.JtagDTMKey
import freechips.rocketchip.diplomacy.{LazyModule, SynchronousCrossing, NoCrossing, FlipRendering}
import freechips.rocketchip.util.{ResetCatchAndSync, AsyncResetShiftReg}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._
import sifive.blocks.devices.pinctrl._

import hbwif.{Differential}
import hbwif.tilelink.{HbwifTLKey}

import testchipip._

class BeagleChipTop(implicit val p: Parameters) extends RawModule
  with freechips.rocketchip.util.DontTouch
{
  // setup the system
  val sys_clk = Wire(Clock())
  val sys_rst = Wire(Bool())
  val sys = withClockAndReset(sys_clk, sys_rst) {
    Module(LazyModule(new BeagleTop).module)
  }

  // -----------------------------------------------------------------------

  // base signals
  val reset           = IO(Input(Bool())) // reset from off chip
  val boot            = IO(Input(Bool())) // boot from sdcard or tether

  // input clocks
  val single_clks = IO(Input(Vec(1, Clock()))) // slow clock
  val diff_clks   = IO(Vec(1, new Differential)) // fast clock

  val hbwif_diff_clks = IO(Vec(p(HbwifTLKey).numBanks, new Differential)) // clks for hbwif

  // output clocks
  val bh_clk_out = IO(Output(Clock()))
  val rs_clk_out = IO(Output(Clock()))
  val lbwif_clk_out = IO(Output(Clock())) // clock to sample lbwif

  // clock mux select signals
  val bh_clk_sel = IO(Input(Bool())) // selector to choose fast or slow clk for boom + hwacha
  val rs_clk_sel = IO(Input(Bool())) // selector to choose fast or slow clk for rocket + systolic

  // setup interfaces
  val lbwif_serial  = IO(chiselTypeOf(sys.lbwif_serial)) // lbwif signals
  val hbwif = IO(new Bundle { // hbwif signals
    val tx = chiselTypeOf(sys.hbwif_tx)
    val rx = chiselTypeOf(sys.hbwif_rx)
  })

  // setup external IO
  val gpio            = IO(new GPIOPins(() => new EnhancedPin(), p(PeripheryGPIOKey).head))
  val i2c             = IO(new  I2CPins(() => new BasePin()))
  val spi             = IO(new  SPIPins(() => new BasePin(), p(PeripherySPIKey).head))
  val uart            = IO(new UARTPins(() => new BasePin()))
  val jtag            = IO(new JTAGPins(() => new BasePin(), false))

  // -----------------------------------------------------------------------

  require(sys.auto.elements.isEmpty)

  // other signals
  sys.boot := boot
  sys.rst_async := reset.asBool

  // punch lbwif to top level
  lbwif_serial     <> sys.lbwif_serial
  lbwif_clk_out := sys.lbwif_clk_out

  // punch hbwif to top level
  hbwif.tx <> sys.hbwif_tx
  hbwif.rx <> sys.hbwif_rx

  // setup correct clocking from offchip

  sys.single_clks <> single_clks

  // convert differential clocks into normal clocks
  val diff_clks_single = diff_clks.map { clock_io =>
    val clock_rx = withClockAndReset(sys_clk, sys_rst) {
      Module(new ClockReceiver())
    }
    clock_rx.io.VIP <> clock_io.p
    clock_rx.io.VIN <> clock_io.n
    clock_rx.io.VOBUF
  }
  sys.diff_clks <> diff_clks_single

  bh_clk_out    := sys.bh_clk_out
  rs_clk_out    := sys.rs_clk_out

  sys_clk := sys.uncore_clk_out
  withClockAndReset(sys_clk, reset.asBool) {
    // This is duplicated during synthesis
    sys_rst := AsyncResetShiftReg(ResetCatchAndSync(sys.uncore_clk_out, reset.asBool), depth = p(BeaglePipelineResetDepth), init=1)
  }

  // clock mux select signals
  sys.bh_clk_sel := bh_clk_sel
  sys.rs_clk_sel := rs_clk_sel

  // convert differential clocks into normal clocks
  val hbwif_clks = hbwif_diff_clks.map { clock_io =>
    val clock_rx = withClockAndReset(sys_clk, sys_rst) {
      Module(new ClockReceiver())
    }
    clock_rx.io.VIP <> clock_io.p
    clock_rx.io.VIN <> clock_io.n
    clock_rx.io.VOBUF
  }

  sys.hbwif_clks <> hbwif_clks

  // external io setup

  // jtag setup
  sys.debug.systemjtag.foreach { sj =>
    JTAGPinsFromPort(jtag, sj.jtag)
    sj.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
    sj.reset := ResetCatchAndSync(sj.jtag.TCK, reset.asBool)
  }

  // sifive block peripheral connections
  SPIPinsFromPort(spi, sys.spi.head, sys_clk, sys_rst, 3)
  sys.spi.head.dq(2) := DontCare
  sys.spi.head.dq(3) := DontCare
  I2CPinsFromPort(i2c, sys.i2c.head, sys_clk, sys_rst, 3)
  UARTPinsFromPort(uart, sys.uart.head, sys_clk, sys_rst, 3)
  GPIOPinsFromPort(gpio, sys.gpio.head, sys_clk, sys_rst)
}
