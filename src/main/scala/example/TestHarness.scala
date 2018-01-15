package example

import chisel3._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import testchipip.GeneratorApp

case object BuildTop extends Field[(Clock, Bool, Parameters) => ExampleTopModule[ExampleTop]]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(clock, reset.toBool, p)
  dut.debug := DontCare
  dut.connectSimAXIMem()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  generateFirrtl
}
