package example

import util.GeneratorApp
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
  val ser = Module(new SimSerialWrapper(p(SerialInterfaceWidth)))

  dut.io.debug.map { dbg =>
    dbg.req.valid := false.B
    dbg.resp.ready := false.B
  }

  val nMemChannels = p(coreplex.BankedL2Config).nMemoryChannels
  val mem = Module(LazyModule(new SimAXIMem(nMemChannels)).module)
  mem.io.axi4 <> dut.io.mem_axi4
  ser.io.serial <> dut.io.serial
  io.success := ser.io.exit
}

object Generator extends GeneratorApp {
  val longName = names.topModuleProject + "." +
                 names.topModuleClass + "." +
                 names.configs
  generateFirrtl
}
