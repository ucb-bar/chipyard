package testchipip.soc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.devices.debug.{DMIIO, ClockedDMIIO}

/** BlackBox to export DMI interface */
class TestchipSimDTM(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val reset = Input(Bool())
    val debug = new DMIIO
    val exit = Output(UInt(32.W))
  })

  def connect(tbclk: Clock, tbreset: Bool, dutio: ClockedDMIIO, tbsuccess: Bool) = {
    io.clk := tbclk
    io.reset := tbreset
    dutio.dmi <> io.debug
    dutio.dmiClock := tbclk
    dutio.dmiReset := tbreset

    tbsuccess := io.exit === 1.U
    assert(io.exit < 2.U, "*** FAILED *** (exit code = %d)\n", io.exit >> 1.U)
  }

  addResource("/testchipip/vsrc/TestchipSimDTM.v")
  addResource("/testchipip/csrc/testchip_htif.cc")
  addResource("/testchipip/csrc/testchip_htif.h")
  addResource("/testchipip/csrc/testchip_dtm.cc")
  addResource("/testchipip/csrc/testchip_dtm.h")
  addResource("/testchipip/csrc/TestchipSimDTM.cc")
}
