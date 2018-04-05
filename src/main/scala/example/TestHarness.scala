package example

import chisel3._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.coreplex.{ExtMem, CacheBlockBytes}
import testchipip.{GeneratorApp, SimDRAM}

case object BuildTop extends Field[(Clock, Bool, Parameters) => ExampleTopModule[ExampleTop]]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(clock, reset.toBool, p)
  dut.debug := DontCare
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  io.success := dut.connectSimSerial()

  val memSize = p(ExtMem).size / dut.mem_axi4.size
  val lineSize = p(CacheBlockBytes)

  dut.mem_axi4.foreach { case mem =>
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
