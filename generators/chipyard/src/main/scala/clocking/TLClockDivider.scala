package chipyard.clocking

import chisel3._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.util.ElaborationArtefacts

import testchipip._

// This module adds a TileLink memory-mapped clock divider to the clock graph
// The output clock/reset pairs from this module should be synchronized later
class TLClockDivider(address: BigInt, beatBytes: Int, divBits: Int = 8)(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice(s"clk-div-ctrl", Nil)
  val clockNode = ClockGroupIdentityNode()
  val tlNode = TLRegisterNode(Seq(AddressSet(address, 4096-1)), device, "reg/control", beatBytes=beatBytes)

  lazy val module = new LazyModuleImp(this) {
    require (clockNode.out.size == 1)
    val sources = clockNode.in.head._1.member.data.toSeq
    val sinks = clockNode.out.head._1.member.elements.toSeq
    require (sources.size == sinks.size)
    val nSinks = sinks.size
    // The implicit clock of this module is the clock of the tilelink bus
    // busReset is sync'd to that clock, and will be asserted longer than the
    // resets coming in through the clockNode, since the busReset is derived from
    // the clockNode resets in downstream PRCI nodes
    val busReset = reset

    val regs = (0 until nSinks) .map { i =>
      val sinkName = sinks(i)._1
      val asyncReset = sources(i).reset
      val reg = Module(new AsyncResetRegVec(w=divBits, init=0))

      println(s"${(address+i*4).toString(16)}: Clock domain $sinkName divider")
      val divider = Module(new testchipip.ClockDivideOrPass(divBits, depth = 3, genClockGate = p(ClockGateImpl)))
      divider.io.clockIn := sources(i).clock
      // busReset is expected to be high for a long time, since reset will take a while to propagate
      // to the TL bus. While reset is propagating, make sure we propagate a fast, undivided clock
      // by setting divisor=0. The divisor signal into the ClockDividerOrPass is synchronized internally
      divider.io.divisor := Mux(busReset.asBool, 0.U, reg.io.q)
      divider.io.resetAsync := ResetStretcher(sources(i).clock, asyncReset, 20).asAsyncReset
      sinks(i)._2.clock := divider.io.clockOut

      // Note this is not synchronized to the output clock, which takes time to appear
      // so this is still asyncreset
      // Stretch the reset for 40 cycles, to give enough time to reset any downstream
      // digital logic
      sinks(i)._2.reset := ResetStretcher(sources(i).clock, asyncReset, 40).asAsyncReset
      reg
    }

    tlNode.regmap((0 until nSinks).map { i =>
      i * 4 -> Seq(RegField.rwReg(divBits, regs(i).io))
    }: _*)
  }
}
