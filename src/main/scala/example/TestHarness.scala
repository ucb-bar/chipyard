package example

import diplomacy.LazyModule
import rocketchip._
import testchipip._
import chisel3._
import config.Parameters

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  def buildTop(p: Parameters): ExampleTop = LazyModule(new ExampleTop()(p))

  val dut = Module(buildTop(p).module)
  dut.connectSimAXIMem()
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  generateFirrtl
}
