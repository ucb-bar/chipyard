package boomexample

import chisel3._
import freechips.rocketchip.coreplex.{ExtMem, CacheBlockBytes}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import testchipip.{GeneratorApp, SimDRAM}

case object BuildBoomTop extends Field[(Clock, Bool, Parameters) => BoomExampleTopModule[BoomExampleTop]]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildBoomTop)(clock, reset.toBool, p)
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
}
