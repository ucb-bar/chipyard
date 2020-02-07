package chipyard

import chisel3._
import chisel3.experimental._

import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}

import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp
import freechips.rocketchip.devices.debug.{Debug}

import chipyard.config.ConfigValName._
import chipyard.iobinders.{IOBinders}

// -------------------------------
// BOOM and/or Rocket Test Harness
// -------------------------------

case object BuildTop extends Field[Parameters => Any]((p: Parameters) => Module(LazyModule(new Top()(p)).suggestName("top").module))

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(p)
  io.success := false.B
  p(IOBinders).values.map(fn => fn(clock, reset.asBool, io.success, dut))
}
