package testchipip.tsi

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam}

import org.chipsalliance.cde.config.{Parameters, Field}

object SimTSI {
  def connect(tsi: Option[TSIIO], clock: Clock, reset: Reset, chipId: Int = 0): Bool = {
    val exit = tsi.map { s =>
      val sim = Module(new SimTSI(chipId))
      sim.io.clock := clock
      sim.io.reset := reset
      sim.io.tsi <> s
      sim.io.exit
    }.getOrElse(0.U)

    val success = exit === 1.U
    val error = exit >= 2.U
    assert(!error, "*** FAILED *** (exit code = %d)\n", exit >> 1.U)
    success
  }
}

class SimTSI(chipId: Int) extends BlackBox(Map("CHIPID" -> IntParam(chipId))) with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val tsi = Flipped(new TSIIO)
    val exit = Output(UInt(32.W))
  })

  addResource("/testchipip/vsrc/SimTSI.v")
  addResource("/testchipip/csrc/SimTSI.cc")
  addResource("/testchipip/csrc/testchip_htif.cc")
  addResource("/testchipip/csrc/testchip_htif.h")
  addResource("/testchipip/csrc/testchip_tsi.cc")
  addResource("/testchipip/csrc/testchip_tsi.h")
}
