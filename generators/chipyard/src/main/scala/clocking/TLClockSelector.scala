package chipyard.clocking

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.util.ElaborationArtefacts

import testchipip._

case class ClockSelNode()(implicit valName: ValName)
  extends MixedNexusNode(ClockImp, ClockGroupImp)(
     dFn = { d => ClockGroupSourceParameters() },
     uFn = { u => ClockSinkParameters() }
)

// This module adds a TileLink memory-mapped clock mux for each downstream clock domain
// in the clock graph. The output clock/reset should be synchronized downstream
class TLClockSelector(address: BigInt, beatBytes: Int)(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("clk-sel-ctrl", Nil)
  val tlNode = TLRegisterNode(Seq(AddressSet(address, 4096-1)), device, "reg/control", beatBytes=beatBytes)

  val clockNode = ClockSelNode()

  lazy val module = new LazyModuleImp(this) {
    val asyncReset = clockNode.in.map(_._1).map(_.reset).toSeq(0)
    val clocks = clockNode.in.map(_._1).map(_.clock)
    val (outClocks, _) = clockNode.out.head
    val (sinkNames, sinks) = outClocks.member.elements.toSeq.unzip

    val regs = (0 until sinks.size).map { i =>
      val sinkName = sinkNames(i)
      val sel = Wire(UInt(log2Ceil(clocks.size).W))
      val reg = withReset(asyncReset) { Module(new AsyncResetRegVec(w=log2Ceil(clocks.size), init=0)) }
      sel := reg.io.q
      println(s"${(address+i*4).toString(16)}: Clock domain $sinkName clock mux")

      val mux = testchipip.ClockMutexMux(clocks).suggestName(s"${sinkName}_clkmux")
      mux.io.sel        := sel
      mux.io.resetAsync := asyncReset.asAsyncReset
      sinks(i).clock := mux.io.clockOut
      // Stretch the reset for 20 cycles, to give time to reset any downstream digital logic
      sinks(i).reset := ResetStretcher(clocks(0), asyncReset, 20).asAsyncReset

      reg
    }
    tlNode.regmap((0 until sinks.size).map { i =>
      i * 4 -> Seq(RegField.rwReg(log2Ceil(clocks.size), regs(i).io))
    }: _*)
  }
}
