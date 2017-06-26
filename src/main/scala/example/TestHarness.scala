package example

import diplomacy.LazyModule
import rocketchip._
import testchipip._
import chisel3._
import config.{Field, Parameters}

case object BuildTop extends Field[Parameters => ExampleTopModule[ExampleTop]]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(p)
  dut.connectSimAXIMem()
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  generateFirrtl
}
