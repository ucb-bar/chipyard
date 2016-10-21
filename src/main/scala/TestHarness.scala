package example

import util.GeneratorApp
import diplomacy.LazyModule
import rocketchip._
import testchipip._
import chisel3._
import cde.{Parameters, Field}

case object BuildExampleTop extends Field[Parameters => ExampleTop]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = new Bundle {
    val success = Bool(OUTPUT)
  }

  def buildTop(p: Parameters): ExampleTop = LazyModule(new ExampleTop(p))

  val dut = p(BuildExampleTop)(p).module
  val ser = Module(new SimSerialWrapper(p(SerialInterfaceWidth)))

  val nMemChannels = dut.io.mem_axi.size
  for (axi <- dut.io.mem_axi) {
    val mem = Module(new SimAXIMem(BigInt(p(ExtMemSize) / nMemChannels)))
    mem.io.axi <> axi
  }

  ser.io.serial <> dut.io.serial
  io.success := ser.io.exit
}

object Generator extends GeneratorApp {
  val longName = names.topModuleProject + "." +
                 names.topModuleClass + "." +
                 names.configs
  generateFirrtl
}
