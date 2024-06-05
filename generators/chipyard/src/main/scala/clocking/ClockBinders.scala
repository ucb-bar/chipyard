package chipyard.clocking

import chisel3._
import chisel3.util._
import chipyard.iobinders._
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import chipyard.iocell._

// This uses the FakePLL, which uses a ClockAtFreq Verilog blackbox to generate
// the requested clocks. This also adds TileLink ClockDivider and ClockSelector
// blocks, which allow memory-mapped control of clock division, and clock muxing
// between the FakePLL and the slow off-chip clock
// Note: This will not simulate properly with firesim
// Unsetting enable will prevent the divider/selector from actually modifying the clock,
// while preserving the address map. Unsetting enable should only be done for RTL
// simulators (Verilator) which do not model reset properly
class WithPLLSelectorDividerClockGenerator(enable: Boolean = true) extends OverrideLazyIOBinder({
  (system: HasChipyardPRCI) => {
    // Connect the implicit clock
    implicit val p = GetSystemParameters(system)
    val tlbus = system.asInstanceOf[BaseSubsystem].locateTLBusWrapper(system.prciParams.slaveWhere)
    val baseAddress = system.prciParams.baseAddress
    val clockDivider  = system.prci_ctrl_domain { LazyModule(new TLClockDivider (baseAddress + 0x20000, tlbus.beatBytes, enable=enable)) }
    val clockSelector = system.prci_ctrl_domain { LazyModule(new TLClockSelector(baseAddress + 0x30000, tlbus.beatBytes, enable=enable)) }
    val pllCtrl       = system.prci_ctrl_domain { LazyModule(new FakePLLCtrl    (baseAddress + 0x40000, tlbus.beatBytes)) }

    clockDivider.tlNode  := system.prci_ctrl_domain { TLFragmenter(tlbus, Some("ClockDivider")) := system.prci_ctrl_bus.get }
    clockSelector.tlNode := system.prci_ctrl_domain { TLFragmenter(tlbus, Some("ClockSelector")) := system.prci_ctrl_bus.get }
    pllCtrl.tlNode       := system.prci_ctrl_domain { TLFragmenter(tlbus, Some("PLLCtrl")) := system.prci_ctrl_bus.get }

    system.chiptopClockGroupsNode := clockDivider.clockNode := clockSelector.clockNode

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
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = Wire(Input(AsyncReset()))
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
      val (reset_io, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

      slowClockSource.out.unzip._1.map { o =>
        o.clock := clock_wire
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

      (Seq(ClockPort(() => clock_io, 100), ResetPort(() => reset_io)), clockIOCell ++ resetIOCell)
    }
  }
})

// This passes all clocks through to the TestHarness
class WithPassthroughClockGenerator extends OverrideLazyIOBinder({
  (system: HasChipyardPRCI) => {
    implicit val p = GetSystemParameters(system)

    // This aggregate node should do nothing
    val clockGroupAggNode = ClockGroupAggregateNode("fake")
    val clockGroupsSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    system.chiptopClockGroupsNode := clockGroupAggNode := clockGroupsSourceNode

    InModuleBody {
      val reset_io = IO(Input(AsyncReset()))
      require(clockGroupAggNode.out.size == 1)
      val (bundle, edge) = clockGroupAggNode.out(0)

      val clock_ios = (bundle.member.data zip edge.sink.members).map { case (b, m) =>
        require(m.take.isDefined, s"""Clock ${m.name.get} has no requested frequency
                                     |Clocks: ${edge.sink.members.map(_.name.get)}""".stripMargin)
        val freq = m.take.get.freqMHz
        val clock_io = IO(Input(Clock())).suggestName(s"clock_${m.name.get}")
        b.clock := clock_io
        b.reset := reset_io
        ClockPort(() => clock_io, freq)
      }.toSeq
      ((clock_ios :+ ResetPort(() => reset_io)), Nil)
    }
  }
})

// Broadcasts a single clock IO to all clock domains. Ignores all requested frequencies
class WithSingleClockBroadcastClockGenerator(freqMHz: Int = 100) extends OverrideLazyIOBinder({
  (system: HasChipyardPRCI) => {
    implicit val p = GetSystemParameters(system)

    val clockGroupsAggregator = LazyModule(new ClockGroupAggregator("single_clock"))
    val clockGroupsSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    system.chiptopClockGroupsNode :*= clockGroupsAggregator.node := clockGroupsSourceNode

    InModuleBody {
      val clock_wire = Wire(Input(Clock()))
      val reset_wire = Wire(Input(AsyncReset()))
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
      val (reset_io, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

      clockGroupsSourceNode.out.foreach { case (bundle, edge) =>
        bundle.member.data.foreach { b =>
          b.clock := clock_io
          b.reset := reset_io
        }
      }
      (Seq(ClockPort(() => clock_io, freqMHz), ResetPort(() => reset_io)), clockIOCell ++ resetIOCell)
    }
  }
})

class WithClockTapIOCells extends OverrideIOBinder({
  (system: CanHaveClockTap) => {
    system.clockTapIO.map { tap =>
      val (clock_tap_io, clock_tap_cell) = IOCell.generateIOFromSignal(tap.getWrappedValue, "clock_tap")
      (Seq(ClockTapPort(() => clock_tap_io)), clock_tap_cell)
    }.getOrElse((Nil, Nil))
  }
})
