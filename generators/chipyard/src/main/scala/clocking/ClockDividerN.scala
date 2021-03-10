// See LICENSE for license details.

package chipyard.clocking

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.{Config, Field, Parameters}

//case object ClockDividerStyleKey extends Field[midas.widgets.ClockDividerImplStyle](midas.widgets.BaselineDivider)
case object ClockDividerStyleKey extends Field[midas.widgets.ClockDividerImplStyle](midas.widgets.GenericDivider)

class ClockDividerN(div: Int)(implicit p: Parameters) extends BlackBox(Map("DIV" -> div)) with HasBlackBoxResource {
  require(div > 0);
  val io = IO(new Bundle {
    val clk_out = Output(Clock())
    val clk_in  = Input(Clock())
  })
  addResource("/vsrc/ClockDividerN.sv")
  midas.widgets.BridgeableClockDivider(this, io.clk_in, io.clk_out, div, p(ClockDividerStyleKey))
}

object ClockDivideByN {
  def apply(clockIn: Clock, div: Int)(implicit p: Parameters): Clock = {
    val clockDivider = Module(new ClockDividerN(div))
    clockDivider.io.clk_in := clockIn
    clockDivider.io.clk_out
  }
}
