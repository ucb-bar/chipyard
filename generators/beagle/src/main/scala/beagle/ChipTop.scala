package beagle

import chisel3._
import chisel3.util.{Cat, ShiftRegister}
import chisel3.experimental.{MultiIOModule, RawModule, withClockAndReset}

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

import testchipip._

class BeagleChipTop(implicit val p: Parameters) extends RawModule
  with freechips.rocketchip.util.DontTouch {

  val sys_clk = Wire(Clock())
  val sys_rst = Wire(Bool())
  val sys = withClockAndReset(sys_clk, sys_rst) {
    Module(LazyModule(new BeagleRocketTop).module)
  }

  val reset           = IO(Input(Bool())) // reset from off chip
  val boot            = IO(Input(Bool())) // boot from sdcard or tether

  val alt_clks        = IO(Input(Vec(1, Clock()))) // extra "chicken bit" clocks
  val alt_clk_sel     = IO(Input(UInt(1.W)))

  val tl_serial       = IO(chiselTypeOf(sys.tl_serial))
  val tl_serial_clock = IO(Output(Clock()))

  val gpio            = IO(new GPIOPins(() => new EnhancedPin(), p(PeripheryGPIOKey).head))
  val i2c             = IO(new I2CPins(() => new BasePin()))
  val spi             = IO(new SPIPins(() => new BasePin(), p(PeripherySPIKey).head))
  val uart            = IO(new UARTPins(() => new BasePin()))

  val jtag            = IO(new JTAGPins(() => new BasePin(), false))

  // -----------------------------------------------------------------------

  require(sys.auto.elements.isEmpty)

  //This has built in synchronizer/connection
  tl_serial       <> sys.tl_serial
  tl_serial_clock := sys.lbwif_clk_out

  // pass in alternate clocks coming from offchip
  sys.alt_clks <> alt_clks
  sys.alt_clk_sel := alt_clk_sel

  sys.boot := boot
  sys.rst_async := reset.asBool

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

  // setup system clocks and reset
  sys_clk := sys.clk_out
  withClockAndReset(sys_clk, reset.asBool) {
    // This is duplicated during synthesis
    sys_rst := AsyncResetShiftReg(ResetCatchAndSync(sys.clk_out, reset.asBool), depth = p(BeaglePipelineResetDepth), init=1)
  }
}
