package chipyard.clocking

import chisel3._
import chisel3.util._
import chipyard.iobinders.{OverrideLazyIOBinder, GetSystemParameters, IOCellKey}
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import barstools.iocell.chisel._

class ClockWithFreq(val freqMHz: Double) extends Bundle {
  val clock = Clock()
}

// This uses the FakePLL, which uses a ClockAtFreq Verilog blackbox to generate
// the requested clocks. This also adds TileLink ClockDivider and ClockSelector
// blocks, which allow memory-mapped control of clock division, and clock muxing
// between the FakePLL and the slow off-chip clock
// Note: This will not simulate properly with firesim
class WithPLLSelectorDividerClockGenerator extends OverrideLazyIOBinder({
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
    val tlbus = system.asInstanceOf[BaseSubsystem].locateTLBusWrapper(system.prciParams.slaveWhere)
    val baseAddress = system.prciParams.baseAddress
    val clockDivider  = system.prci_ctrl_domain { LazyModule(new TLClockDivider (baseAddress + 0x20000, tlbus.beatBytes)) }
    val clockSelector = system.prci_ctrl_domain { LazyModule(new TLClockSelector(baseAddress + 0x30000, tlbus.beatBytes)) }
    val pllCtrl       = system.prci_ctrl_domain { LazyModule(new FakePLLCtrl    (baseAddress + 0x40000, tlbus.beatBytes)) }

    tlbus.toVariableWidthSlave(Some("clock-div-ctrl")) { clockDivider.tlNode := TLBuffer() }
    tlbus.toVariableWidthSlave(Some("clock-sel-ctrl")) { clockSelector.tlNode := TLBuffer() }
    tlbus.toVariableWidthSlave(Some("pll-ctrl")) { pllCtrl.tlNode := TLBuffer() }

    system.allClockGroupsNode := clockDivider.clockNode := clockSelector.clockNode

    // Connect all other requested clocks
    val slowClockSource = ClockSourceNode(Seq(ClockSourceParameters()))
    val pllClockSource = ClockSourceNode(Seq(ClockSourceParameters()))

    // The order of the connections to clockSelector.clockNode configures the inputs
    // of the clockSelector's clockMux. Default to using the slowClockSource,
    // software should enable the PLL, then switch to the pllClockSource
    clockSelector.clockNode := slowClockSource
    clockSelector.clockNode := pllClockSource

    val pllCtrlSink = BundleBridgeSink[FakePLLCtrlBundle]()
    pllCtrlSink := pllCtrl.ctrlNode

    InModuleBody {
      val clock_wire = Wire(Input(new ClockWithFreq(100)))
      val reset_wire = Wire(Input(AsyncReset()))
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
      val (reset_io, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

      slowClockSource.out.unzip._1.map { o =>
        o.clock := clock_wire.clock
        o.reset := reset_wire
      }

      // For a real chip you should replace this ClockSourceAtFreqFromPlusArg
      // with a blackbox of whatever PLL is being integrated
      val fake_pll = Module(new ClockSourceAtFreqFromPlusArg("pll_freq_mhz"))
      fake_pll.io.power := pllCtrlSink.in(0)._1.power
      fake_pll.io.gate := pllCtrlSink.in(0)._1.gate

      pllClockSource.out.unzip._1.map { o =>
        o.clock := fake_pll.io.clk
        o.reset := reset_wire
      }

      (Seq(clock_io, reset_io), clockIOCell ++ resetIOCell)
    }
  }
})

// This passes all clocks through to the TestHarness
class WithPassthroughClockGenerator extends OverrideLazyIOBinder({
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

    // This aggregate node should do nothing
    val clockGroupAggNode = ClockGroupAggregateNode("fake")
    val clockGroupsSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    system.allClockGroupsNode := clockGroupAggNode := clockGroupsSourceNode

    InModuleBody {
      val reset_io = IO(Input(AsyncReset()))
      require(clockGroupAggNode.out.size == 1)
      val (bundle, edge) = clockGroupAggNode.out(0)

      val clock_ios = (bundle.member.data zip edge.sink.members).map { case (b, m) =>
        val freq = m.take.get.freqMHz
        val clock_io = IO(Input(new ClockWithFreq(freq))).suggestName(s"clock_${m.name.get}")
        b.clock := clock_io.clock
        b.reset := reset_io
        clock_io
      }.toSeq
      ((clock_ios :+ reset_io), Nil)
    }
  }
})
