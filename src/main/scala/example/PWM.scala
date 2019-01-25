package example

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

case class PWMParams(address: BigInt, beatBytes: Int)

class PWMBase(w: Int) extends Module {
  val io = IO(new Bundle {
    val pwmout = Output(Bool())
    val period = Input(UInt(w.W))
    val duty = Input(UInt(w.W))
    val enable = Input(Bool())
  })

  // The counter should count up until period is reached
  val counter = Reg(UInt(w.W))

  when (counter >= (io.period - 1.U)) {
    counter := 0.U
  } .otherwise {
    counter := counter + 1.U
  }

  // If PWM is enabled, pwmout is high when counter < duty
  // If PWM is not enabled, it will always be low
  io.pwmout := io.enable && (counter < io.duty)
}

trait PWMBundle extends Bundle {
  val pwmout = Output(Bool())
}

trait PWMModule extends HasRegMap {
  val io: PWMBundle
  implicit val p: Parameters
  def params: PWMParams

  // How many clock cycles in a PWM cycle?
  val period = Reg(UInt(32.W))
  // For how many cycles should the clock be high?
  val duty = Reg(UInt(32.W))
  // Is the PWM even running at all?
  val enable = RegInit(false.B)

  val base = Module(new PWMBase(32))
  io.pwmout := base.io.pwmout
  base.io.period := period
  base.io.duty := duty
  base.io.enable := enable

  regmap(
    0x00 -> Seq(
      RegField(32, period)),
    0x04 -> Seq(
      RegField(32, duty)),
    0x08 -> Seq(
      RegField(1, enable)))
}

class PWMTL(c: PWMParams)(implicit p: Parameters)
  extends TLRegisterRouter(
    c.address, "pwm", Seq("ucbbar,pwm"),
    beatBytes = c.beatBytes)(
      new TLRegBundle(c, _) with PWMBundle)(
      new TLRegModule(c, _, _) with PWMModule)

class PWMAXI4(c: PWMParams)(implicit p: Parameters)
  extends AXI4RegisterRouter(c.address, beatBytes = c.beatBytes)(
      new AXI4RegBundle(c, _) with PWMBundle)(
      new AXI4RegModule(c, _, _) with PWMModule)

trait HasPeripheryPWMTL { this: BaseSubsystem =>
  implicit val p: Parameters

  private val address = 0x2000
  private val portName = "pwm"

  val pwm = LazyModule(new PWMTL(
    PWMParams(address, pbus.beatBytes))(p))

  pbus.toVariableWidthSlave(Some(portName)) { pwm.node }
}

trait HasPeripheryPWMTLModuleImp extends LazyModuleImp {
  implicit val p: Parameters
  val outer: HasPeripheryPWMTL

  val pwmout = IO(Output(Bool()))

  pwmout := outer.pwm.module.io.pwmout
}

trait HasPeripheryPWMAXI4 { this: BaseSubsystem =>
  implicit val p: Parameters

  private val address = 0x2000
  private val portName = "pwm"

  val pwm = LazyModule(new PWMAXI4(
    PWMParams(address, 8 * pbus.beatBytes))(p))

  pbus.toFixedWidthSlave(Some(portName)) { pwm.node := TLToAXI4() }
}

trait HasPeripheryPWMAXI4ModuleImp extends LazyModuleImp {
  implicit val p: Parameters
  val outer: HasPeripheryPWMAXI4

  val pwmout = IO(Output(Bool()))

  pwmout := outer.pwm.module.io.pwmout
}
