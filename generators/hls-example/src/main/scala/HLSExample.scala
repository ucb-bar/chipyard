package hlsaccel

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, PBUS}
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

case class HLSAccelParams(
  address: BigInt = 0x4000,
  width: Int = 32
)

case object HLSAccelKey extends Field[Option[HLSAccelParams]](None) 

class HLSAccelIO(val w: Int) extends Bundle {
  val ap_clk = Input(Clock())
  val ap_rst = Input(Reset())
  val ap_start = Input(Bool())
  val ap_done = Output(Bool())
  val ap_idle = Output(Bool())
  val ap_ready = Output(Bool())
  val x = Input(UInt(w.W))
  val y = Input(UInt(w.W))
  val ap_return = Output(UInt(w.W))
}

// DOC include start: HLS blackbox
class HLSAccelBlackBox(val w: Int) extends BlackBox with HasBlackBoxPath {
  val io = IO(new HLSAccelIO(w))

  val chipyardDir = System.getProperty("user.dir")
  val hlsDir = s"$chipyardDir/generators/hls-example"
  
  // Run HLS command
  val make = s"make -C ${hlsDir} default"
  require (make.! == 0, "Failed to run HLS")

  // Add each vlog file
  addPath(s"$hlsDir/src/main/resources/vsrc/HLSAccelBlackBox.v")
  addPath(s"$hlsDir/src/main/resources/vsrc/HLSAccelBlackBox_flow_control_loop_pipe.v")
}
// DOC include end: HLS blackbox

class HLSAccel(params: HLSAccelParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("hlsaccel", Seq("ucbbar,hlsaccel")) 
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=beatBytes)

  override lazy val module = new HLSAccelImpl
  class HLSAccelImpl extends Impl {
    withClockAndReset(clock, reset) {
      val x = Reg(UInt(params.width.W))
      val y = Wire(new DecoupledIO(UInt(params.width.W)))
      val y_reg = Reg(UInt(params.width.W))
      val gcd = Wire(new DecoupledIO(UInt(params.width.W)))
      val gcd_reg = Reg(UInt(params.width.W))
      val status = Wire(UInt(2.W))

      val impl = Module(new HLSAccelBlackBox(params.width))

      impl.io.ap_clk := clock
      impl.io.ap_rst := reset

      val s_idle :: s_busy :: Nil = Enum(2)
      val state = RegInit(s_idle)
      val result_valid = RegInit(false.B)
      when (state === s_idle && y.valid) { 
        state := s_busy
        result_valid := false.B
        y_reg := y.bits 
      } .elsewhen (state === s_busy && impl.io.ap_done) {
        state := s_idle
        result_valid := true.B
        gcd_reg := impl.io.ap_return
      }

      impl.io.ap_start := state === s_busy

      gcd.valid := result_valid
      status := Cat(impl.io.ap_idle, result_valid)
      
      impl.io.x := x
      impl.io.y := y_reg
      y.ready := impl.io.ap_idle
      gcd.bits := gcd_reg

      node.regmap(
        0x00 -> Seq(
          RegField.r(2, status)), // a read-only register capturing current status
        0x04 -> Seq(
          RegField.w(params.width, x)), // a plain, write-only register
        0x08 -> Seq(
          RegField.w(params.width, y)), // write-only, y.valid is set on write
        0x0C -> Seq(
          RegField.r(params.width, gcd))) // read-only, gcd.ready is set on read
    }
  }
}

trait CanHavePeripheryHLSAccel { this: BaseSubsystem =>
  private val portName = "hlsaccel"
  private val pbus = locateTLBusWrapper(PBUS)

  val hlsacc = p(HLSAccelKey) match {
    case Some(params) => {
      val acc = LazyModule(new HLSAccel(params, pbus.beatBytes)(p))
      acc.clockNode := pbus.fixedClockNode
      pbus.coupleTo(portName) { acc.node := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }
      acc
    }
    case None => None
  }
}

class WithHLSAccel(address: BigInt = 0x4000) extends Config((site, here, up) => {
  case HLSAccelKey => Some(HLSAccelParams(address = address))
})
