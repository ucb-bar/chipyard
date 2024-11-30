package chipyard.example


import chisel3._
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem.{PBUS, HasTileLinkLocations}
import freechips.rocketchip.devices.debug.{ExportDebug, JtagDTMKey, Debug}
import freechips.rocketchip.tilelink.{TLBuffer, TLFragmenter}
import chipyard.{BuildSystem, DigitalTop}
import chipyard.harness.{BuildTop}
import chipyard.clocking._
import chipyard.iobinders._
import chipyard.iocell._
import testchipip.serdes.{SerialTLKey}

class WithFlatChipTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new FlatChipTop()(p)
})

// This "FlatChipTop" uses no IOBinders, so all the IO have
// to be explicitly constructed.
// This only supports the base "DigitalTop"
class FlatChipTop(implicit p: Parameters) extends LazyModule with HasChipyardPorts {
  override lazy val desiredName = "ChipTop"
  val system = LazyModule(p(BuildSystem)(p)).suggestName("system").asInstanceOf[DigitalTop]

  //========================
  // Diplomatic clock stuff
  //========================
  val tlbus = system.locateTLBusWrapper(system.prciParams.slaveWhere)
  val baseAddress = system.prciParams.baseAddress
  val clockDivider  = system.prci_ctrl_domain { LazyModule(new TLClockDivider (baseAddress + 0x20000, tlbus.beatBytes)) }
  val clockSelector = system.prci_ctrl_domain { LazyModule(new TLClockSelector(baseAddress + 0x30000, tlbus.beatBytes)) }
  val pllCtrl       = system.prci_ctrl_domain { LazyModule(new FakePLLCtrl    (baseAddress + 0x40000, tlbus.beatBytes)) }

  tlbus.coupleTo("clock-div-ctrl") { clockDivider.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := TLBuffer() := _ }
  tlbus.coupleTo("clock-sel-ctrl") { clockSelector.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := TLBuffer() := _ }
  tlbus.coupleTo("pll-ctrl") { pllCtrl.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := TLBuffer() := _ }

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

  val debugClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters()))
  debugClockSinkNode := system.locateTLBusWrapper(p(ExportDebug).slaveWhere).fixedClockNode
  def debugClockBundle = debugClockSinkNode.in.head._1

  var ports: Seq[Port[_]] = Nil

  override lazy val module = new FlatChipTopImpl
  class FlatChipTopImpl extends LazyRawModuleImp(this) {
    //=========================
    // Clock/reset
    //=========================
    val clock_wire = Wire(Input(Clock()))
    val reset_wire = Wire(Input(AsyncReset()))
    val (clock_pad, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
    val (reset_pad, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

    slowClockSource.out.unzip._1.map { o =>
      o.clock := clock_wire
      o.reset := reset_wire
    }

    ports = ports :+ ClockPort(() => clock_pad, 100.0)
    ports = ports :+ ResetPort(() => reset_pad)

    // For a real chip you should replace this ClockSourceAtFreqFromPlusArg
    // with a blackbox of whatever PLL is being integrated
    val fake_pll = Module(new ClockSourceAtFreqFromPlusArg("pll_freq_mhz"))
    fake_pll.io.power := pllCtrlSink.in(0)._1.power
    fake_pll.io.gate := pllCtrlSink.in(0)._1.gate

    pllClockSource.out.unzip._1.map { o =>
      o.clock := fake_pll.io.clk
      o.reset := reset_wire
    }

    //=========================
    // Custom Boot
    //=========================
    val (custom_boot_pad, customBootIOCell) = IOCell.generateIOFromSignal(system.custom_boot_pin.get.getWrappedValue, "custom_boot", p(IOCellKey))
    ports = ports :+ CustomBootPort(() => custom_boot_pad)

    //=========================
    // Serialized TileLink
    //=========================
    val (serial_tl_pad, serialTLIOCells) = IOCell.generateIOFromSignal(system.serial_tls(0).getWrappedValue, "serial_tl", p(IOCellKey))
    ports = ports :+ SerialTLPort(() => serial_tl_pad, p(SerialTLKey)(0), system.serdessers(0), 0)

    //=========================
    // JTAG/Debug
    //=========================
    val debug = system.debug.get
    // We never use the PSDIO, so tie it off on-chip
    system.psd.psd.foreach { _ <> 0.U.asTypeOf(new PSDTestMode) }
    system.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := false.B } }

    // Tie off extTrigger
    debug.extTrigger.foreach { t =>
      t.in.req := false.B
      t.out.ack := t.out.req
    }
    // Tie off disableDebug
    debug.disableDebug.foreach { d => d := false.B }
    // Drive JTAG on-chip IOs
    debug.systemjtag.map { j =>
      j.reset := ResetCatchAndSync(j.jtag.TCK, debugClockBundle.reset.asBool)
      j.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
      j.part_number := p(JtagDTMKey).idcodePartNum.U(16.W)
      j.version := p(JtagDTMKey).idcodeVersion.U(4.W)
    }

    Debug.connectDebugClockAndReset(Some(debug), debugClockBundle.clock)

    // Add IOCells for the DMI/JTAG/APB ports
    require(!debug.clockeddmi.isDefined)
    require(!debug.apb.isDefined)
    val (jtag_pad, jtagIOCells) = debug.systemjtag.map { j =>
      val jtag_wire = Wire(new JTAGChipIO(false))
      j.jtag.TCK := jtag_wire.TCK
      j.jtag.TMS := jtag_wire.TMS
      j.jtag.TDI := jtag_wire.TDI
      jtag_wire.TDO := j.jtag.TDO.data
      IOCell.generateIOFromSignal(jtag_wire, "jtag", p(IOCellKey), abstractResetAsAsync = true)
    }.get

    ports = ports :+ JTAGPort(() => jtag_pad)

    //==========================
    // UART
    //==========================
    require(system.uarts.size == 1)
    val (uart_pad, uartIOCells) = IOCell.generateIOFromSignal(system.uart.head, "uart_0", p(IOCellKey))
    val where = PBUS // TODO fix
    val bus = system.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(where)
    val freqMHz = bus.dtsFrequency.get / 1000000
    ports = ports :+ UARTPort(() => uart_pad, 0, freqMHz.toInt)

    //==========================
    // External interrupts (tie off)
    //==========================
    system.module.interrupts := DontCare
  }
}
