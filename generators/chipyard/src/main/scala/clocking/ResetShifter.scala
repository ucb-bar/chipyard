package chipyard.clocking

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{ResetCatchAndSync}
import freechips.rocketchip.prci._
import chisel3._
import chisel3.util._

// Adds shift registers to a synchronous reset path
class ClockGroupResetShifter(shiftN: Int)(implicit p: Parameters) extends LazyModule {
  val node = ClockGroupAdapterNode()
  lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
    (node.out zip node.in).map { case ((oG, _), (iG, _)) =>
      (oG.member.data zip iG.member.data).foreach { case (o, i) =>
        o.clock := i.clock
        o.reset := withClock(i.clock) { ShiftRegister(i.reset, shiftN) }
      }
    }
  }
}

object ClockGroupResetShifter {
  def apply(shiftN: Int)(implicit p: Parameters, valName: ValName) = LazyModule(new ClockGroupResetShifter(shiftN)).node
}
