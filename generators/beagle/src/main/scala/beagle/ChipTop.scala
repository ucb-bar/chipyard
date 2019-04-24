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

  val sysClock = Wire(Clock())
  val sysReset = Wire(Bool())
  val system = withClockAndReset(sysClock, sysReset) {
    Module(LazyModule(new BeagleRocketTop).module)
  }

  val reset           = IO(Input(Bool()))
  val boot            = IO(Input(Bool()))

  val cclk            = IO(Input(Vec(3, Clock())))
  val clk_sel         = IO(Input(UInt(2.W)))
  //val refClock        = IO(Vec(3, new Differential))

  val tl_serial       = IO(chiselTypeOf(system.tl_serial))
  val tl_serial_clock = IO(Output(Clock()))

  val gpio            = IO(new GPIOPins(() => new EnhancedPin(), p(PeripheryGPIOKey).head))
  val jtag            = IO(new JTAGPins(() => new BasePin(), false))
  val i2c             = IO(new I2CPins(() => new BasePin()))
  val spi             = IO(new SPIPins(() => new BasePin(), p(PeripherySPIKey).head))
  val uart            = IO(new UARTPins(() => new BasePin()))


  require(system.auto.elements.isEmpty)
  //This has built in synchronizer/connection
  SPIPinsFromPort(spi, system.spi.head, sysClock, sysReset, 3)
  system.spi.head.dq(2) := DontCare
  system.spi.head.dq(3) := DontCare
  I2CPinsFromPort(i2c, system.i2c.head, sysClock, sysReset, 3)
  UARTPinsFromPort(uart, system.uart.head, sysClock, sysReset, 3)
  GPIOPinsFromPort(gpio, system.gpio.head, sysClock, sysReset)
  tl_serial   <> system.tl_serial
  tl_serial_clock := system.lbwifClockOut
  system.cclk <> cclk
  system.clk_sel := clk_sel
  system.boot := boot
  system.resetAsync := reset.toBool

  system.debug.systemjtag.foreach { sj =>
    JTAGPinsFromPort(jtag, sj.jtag)
    sj.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
    sj.reset := ResetCatchAndSync(sj.jtag.TCK, reset.toBool)
  }

  sysClock := system.unclusterClockOut
  withClockAndReset(sysClock, reset.toBool) {
    // This is duplicated during synthesis
    sysReset := AsyncResetShiftReg(ResetCatchAndSync(system.unclusterClockOut, reset.toBool), depth = p(BeaglePipelineResetDepth), init=1)
  }
}
