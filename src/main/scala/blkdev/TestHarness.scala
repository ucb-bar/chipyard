package blkdev

import diplomacy.LazyModule
import chisel3._
import config.{Parameters, Field}
import testchipip.GeneratorApp
import example._

case object UseSimBlockDevice extends Field[Boolean]

class TestHarness(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = Module(LazyModule(new ExampleTopWithBlockDevice).module)
  dut.connectSimAXIMem()
  if (p(UseSimBlockDevice))
    dut.connectSimBlockDevice()
  else
    dut.connectBlockDeviceModel()
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  generateFirrtl
}
