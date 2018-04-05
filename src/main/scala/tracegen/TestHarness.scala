package tracegen

import chisel3._
import freechips.rocketchip.coreplex.SimAXIMem
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import testchipip.GeneratorApp

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = Module(LazyModule(new TracegenTop).module)
  val mem = Module(LazyModule(new SimAXIMem(dut.nMemoryChannels)).module)

  mem.io.axi4 <> dut.io.mem_axi4
  io.success := dut.io.success
}

object Generator extends GeneratorApp {
  generateFirrtl
  generateAnno
}
