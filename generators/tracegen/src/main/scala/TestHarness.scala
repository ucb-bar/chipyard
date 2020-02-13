package tracegen

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.util.GeneratorApp

class TestHarness(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = Module(LazyModule(new TraceGenSystem).module)
  io.success := dut.success
  dut.connectSimAXIMem()
}

object Generator extends GeneratorApp {
  // specify the name that the generator outputs files as
  override lazy val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs

  // generate files
  generateFirrtl
  generateAnno
  generateTestSuiteMakefrags
  generateArtefacts
}
