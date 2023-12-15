package chipyard.clocking

import chisel3._

import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

case class ClockTapParams(
  busWhere: TLBusWrapperLocation = SBUS, // by default, tap the sbus clock as a debug clock
  divider: Int = 16, // a fixed clock division ratio for the clock tap
)

case object ClockTapKey extends Field[Option[ClockTapParams]](Some(ClockTapParams()))

trait CanHaveClockTap { this: BaseSubsystem =>
  val clockTapNode = p(ClockTapKey).map { tapParams =>
    val clockTap = ClockSinkNode(Seq(ClockSinkParameters(name=Some("clock_tap"))))
    val clockTapDivider = LazyModule(new ClockDivider(tapParams.divider))
    clockTap := clockTapDivider.node := locateTLBusWrapper(tapParams.busWhere).fixedClockNode
    clockTap
  }
  val clockTapIO = clockTapNode.map { node => InModuleBody {
    val clock_tap = IO(Output(Clock()))
    clock_tap := node.in.head._1.clock
    clock_tap
  }}
}
