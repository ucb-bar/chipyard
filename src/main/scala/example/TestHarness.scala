package example

import chisel3._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp

case object BuildTop extends Field[(Clock, Bool, Parameters) => ExampleTopModule[ExampleTop]]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(clock, reset.toBool, p)
  dut.debug := DontCare
  dut.connectSimAXIMem()
  dut.connectSimAXIMMIO()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  dut.l2_frontend_bus_axi4.foreach(axi => {
    axi.tieoff()
    experimental.DataMirror.directionOf(axi.ar.ready) match {
      case core.ActualDirection.Input =>
        axi.r.bits := 0.U.asTypeOf(axi.r.bits)
        axi.b.bits := 0.U.asTypeOf(axi.b.bits)
      case core.ActualDirection.Output =>
        axi.aw.bits := 0.U.asTypeOf(axi.aw.bits)
        axi.ar.bits := 0.U.asTypeOf(axi.ar.bits)
        axi.w.bits := 0.U.asTypeOf(axi.w.bits)
    }
  })
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
  generateFirrtl
  generateAnno
  generateArtefacts
}
