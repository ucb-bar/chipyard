package chipyard.clocking

import chisel3._

import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

case object ClockTapKey extends Field[Boolean](true)

trait CanHaveClockTap { this: BaseSubsystem =>
  require(!p(SubsystemDriveClockGroupsFromIO), "Subsystem must not drive clocks from IO")
  val clockTapNode = Option.when(p(ClockTapKey)) {
    val clockTap = ClockSinkNode(Seq(ClockSinkParameters(name=Some("clock_tap"))))
    clockTap := ClockGroup() := allClockGroupsNode
    clockTap
  }
  val clockTapIO = clockTapNode.map { node => InModuleBody {
    val clock_tap = IO(Output(Clock()))
    clock_tap := node.in.head._1.clock
    clock_tap
  }}
}
