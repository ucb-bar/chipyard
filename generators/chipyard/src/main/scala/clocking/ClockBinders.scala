package chipyard.clocking

import chisel3._
import chisel3.util._
import chipyard.iobinders.{OverrideLazyIOBinder, GetSystemParameters, IOCellKey}
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import barstools.iocell.chisel._

class ClockWithFreq(val freqMHz: Double) extends Bundle {
  val clock = Clock()
}

class WithDividerOnlyClockGenerator extends OverrideLazyIOBinder({
  (system: HasChipyardPRCI) => {
    // Connect the implicit clock
    implicit val p = GetSystemParameters(system)
    val implicitClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters(name = Some("implicit_clock"))))
    system.connectImplicitClockSinkNode(implicitClockSinkNode)
    InModuleBody {
      val implicit_clock = implicitClockSinkNode.in.head._1.clock
      val implicit_reset = implicitClockSinkNode.in.head._1.reset
      system.asInstanceOf[BaseSubsystem].module match { case l: LazyModuleImp => {
        l.clock := implicit_clock
        l.reset := implicit_reset
      }}
    }

    // Connect all other requested clocks
    val referenceClockSource = ClockSourceNode(Seq(ClockSourceParameters()))
    val dividerOnlyClockGen = LazyModule(new DividerOnlyClockGenerator("buildTopClockGenerator"))

    (system.allClockGroupsNode
      := dividerOnlyClockGen.node
      := referenceClockSource)

    InModuleBody {
      val clock_wire = Wire(Input(new ClockWithFreq(dividerOnlyClockGen.module.referenceFreq)))
      val reset_wire = Wire(Input(AsyncReset()))
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
      val (reset_io, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

      referenceClockSource.out.unzip._1.map { o =>
        o.clock := clock_wire.clock
        o.reset := reset_wire
      }

      (Seq(clock_io, reset_io), clockIOCell ++ resetIOCell)
    }
  }
})
