package blkdev

import diplomacy.LazyModule
import chisel3._
import config.Parameters
import testchipip.GeneratorApp
import example._

class TestHarness(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = Module(LazyModule(new ExampleTopWithBlockDevice).module)
  dut.connectSimAXIMem()
  dut.connectSimBlockDevice()
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  generateFirrtl
}
