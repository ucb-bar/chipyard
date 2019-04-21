package example

import chisel3._
import chisel3.experimental._
import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp

// -------------------
// Rocket Test Harness
// -------------------

case object BuildRocketTop extends Field[(Clock, Bool, Parameters) => RocketTopModule[RocketTop]]

class RocketTestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildRocketTop)(clock, reset.toBool, p)
  dut.debug := DontCare
  dut.connectSimAXIMem()
  dut.connectSimAXIMMIO()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  dut.l2_frontend_bus_axi4.foreach(axi => {
    axi.tieoff()
    experimental.DataMirror.directionOf(axi.ar.ready) match {
      case core.ActualDirection.Input =>
        axi.r.bits := DontCare
        axi.b.bits := DontCare
      case core.ActualDirection.Output =>
        axi.aw.bits := DontCare
        axi.ar.bits := DontCare
        axi.w.bits := DontCare
    }
  })
  io.success := dut.connectSimSerial()
}

// -----------------
// BOOM Test Harness
// -----------------

case object BuildBoomTop extends Field[(Clock, Bool, Parameters) => BoomTopModule[BoomTop]]

class BoomTestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildBoomTop)(clock, reset.toBool, p)
  dut.debug := DontCare
  dut.connectSimAXIMem()
  dut.connectSimAXIMMIO()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  dut.l2_frontend_bus_axi4.foreach(axi => {
    axi.tieoff()
    experimental.DataMirror.directionOf(axi.ar.ready) match {
      case core.ActualDirection.Input =>
        axi.r.bits := DontCare
        axi.b.bits := DontCare
      case core.ActualDirection.Output =>
        axi.aw.bits := DontCare
        axi.ar.bits := DontCare
        axi.w.bits := DontCare
    }
  })
  io.success := dut.connectSimSerial()
}
