package example

import chisel3._
import chisel3.util._
import chisel3.core.{IntParam, Reset}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

// DOC include start: GCD blackbox
class GCDMMIOBlackBox(w: Int) extends BlackBox(Map("WIDTH" -> IntParam(w))) with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val input_ready = Output(Bool())
    val input_valid = Input(Bool())
    val x = Input(UInt(w.W))
    val y = Input(UInt(w.W))
    val output_ready = Input(Bool())
    val output_valid = Output(Bool())
    val gcd = Output(UInt(w.W))
  })

  addResource("/vsrc/GCDMMIOBlackBox.v")
}
// DOC include end: GCD blackbox

// DOC include start: GCD instance regmap
case class GCDParams(address: BigInt, beatBytes: Int, width: Int)

trait GCDModule extends HasRegMap {
  implicit val p: Parameters
  def params: GCDParams
  val clock: Clock
  val reset: Reset

  val impl = Module(new GCDMMIOBlackBox(params.width))

  // How many clock cycles in a PWM cycle?
  val x = Reg(UInt(params.width.W))
  val y = Wire(new DecoupledIO(impl.io.y))
  val gcd = Wire(new DecoupledIO(impl.io.gcd))
  val status = Cat(impl.io.input_ready, impl.io.output_valid)

  impl.io.clock := clock
  impl.io.reset := reset.asBool
  impl.io.x := x
  impl.io.y := y.bits
  impl.io.input_valid := y.valid
  y.ready := impl.io.input_ready

  gcd.bits := impl.io.gcd
  gcd.valid := impl.io.output_valid
  impl.io.output_ready := gcd.ready

  regmap(
    0x00 -> Seq(
      RegField.r(2, status)), // a read-only register capturing current status
    0x04 -> Seq(
      RegField.w(params.width, x)), // a plain, write-only register
    0x08 -> Seq(
      RegField.w(params.width, y)), // write-only, y.valid is set on write
    0x0C -> Seq(
      RegField.r(params.width, gcd))) // read-only, gcd.ready is set on read
}
// DOC include end: GCD instance regmap

// DOC include start: GCD cake
class GCD(c: GCDParams)(implicit p: Parameters)
  extends TLRegisterRouter(
    c.address, "gcd", Seq("ucbbar,gcd"),
    beatBytes = c.beatBytes)(
      new TLRegBundle(c, _))(
      new TLRegModule(c, _, _) with GCDModule)

trait HasPeripheryGCD { this: BaseSubsystem =>
  implicit val p: Parameters

  private val address = 0x2000
  private val portName = "gcd"
  private val gcdWidth = 32

  val gcd = LazyModule(new GCD(
    GCDParams(address, pbus.beatBytes, gcdWidth))(p))

  pbus.toVariableWidthSlave(Some(portName)) { gcd.node }
}

trait HasPeripheryGCDModuleImp extends LazyModuleImp {
  implicit val p: Parameters
  val outer: HasPeripheryGCD
}

// DOC include end: GCD cake
