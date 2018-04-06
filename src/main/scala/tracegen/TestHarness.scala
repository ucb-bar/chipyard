package tracegen

import chisel3._
import freechips.rocketchip.coreplex.SimAXIMem
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.coreplex.{ExtMem, CacheBlockBytes}
import testchipip.{GeneratorApp, SimDRAM}

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = Module(LazyModule(new TracegenTop).module)

  io.success := dut.io.success

  val memSize = p(ExtMem).size / dut.io.mem_axi4.size
  val lineSize = p(CacheBlockBytes)

  dut.io.mem_axi4.foreach { case mem =>
    val sim = Module(new SimDRAM(memSize, lineSize, mem.params))
    sim.io.axi <> mem
    sim.io.reset := reset
    sim.io.clock := clock
  }
}

object Generator extends GeneratorApp {
  generateFirrtl
  generateAnno
}
