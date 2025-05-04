package chipyard.clocking

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.prci.{ClockGroupAdapterNode}

/** This adapter takes input synchronous resets and stretch them via a reset pipeline
  *  This is useful for distributing a synchronous reset across the chip
  */
class ResetPipeline(stages: Int)(implicit p: Parameters) extends LazyModule {
  val node = ClockGroupAdapterNode()(ValName(s"reset_pipeline_$stages"))
  override lazy val desiredName = s"ResetPipeline$stages"
  lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
    (node.in zip node.out).foreach { case ((iG, _), (oG, _)) =>
      (oG.member.data zip iG.member.data).foreach { case (out, in) =>
        out.clock := in.clock
        withClock (in.clock) {
          if (stages == 0) {
            out.reset := in.reset
          } else {
            val regs = Seq.fill(stages)(Reg(Bool()))
            regs.head := in.reset
            out.reset := regs.last
              (regs.init zip regs.tail).foreach(t => t._2 := t._1)
          }
        }
      }
    }
  }
}
