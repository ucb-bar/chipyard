package chipyard.harness

import chisel3._
import chisel3.util._
import chisel3.reflect.DataMirror
import chisel3.experimental.Direction

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.devices.debug.{SimJTAG}
import chipyard.iocell._
import testchipip.dram.{SimDRAM, FastRAM}
import testchipip.tsi.{SimTSI, SerialRAM, TSI, TSIIO}
import testchipip.soc.{TestchipSimDTM}
import testchipip.spi.{SimSPIFlashModel}
import testchipip.uart.{UARTAdapter, UARTToSerial}
import testchipip.serdes._
import testchipip.iceblk.{SimBlockDevice, BlockDeviceModel}
import testchipip.cosim.{SpikeCosim}
import testchipip.ctc.{CTCBridgeIO}
import icenet.{NicLoopback, SimNetwork}
import chipyard._
import chipyard.clocking.{HasChipyardPRCI}
import chipyard.iobinders._
import rivet.sim.GmiiEthernetFileModel
import rivet.sim.GmiiEthernetUdpFileModel
import rivet.sim.RgmiiEthernetFileModel
import rivet.sim.RgmiiEthernetUdpFileModel
import rivet.sim.XgmiiEthernetFileModel
import rivet.sim.XgmiiEthernetUdpFileModel

case object HarnessBinders extends Field[HarnessBinderFunction]({case _ => })

object ApplyHarnessBinders {
  def apply(th: HasHarnessInstantiators, ports: Seq[Port[_]], chipId: Int)(implicit p: Parameters): Unit = {
    ports.foreach(port => p(HarnessBinders)(th, port, chipId))
  }
}

class HarnessBinder[T <: HasHarnessInstantiators, S <: Port[_]](
  fn: => HarnessBinderFunction
) extends Config((site, here, up) => {
  case HarnessBinders => fn orElse up(HarnessBinders)
})


class WithGPIOTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPort, chipId: Int) => {
    port.io <> AnalogConst(0)

  }
})

class WithGPIOPinsTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPinsPort, chipId: Int) => {
    port.io := DontCare

  }
})

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val div = (th.getHarnessBinderClockFreqMHz.toDouble * 1000000 / port.io.c.initBaudRate.toDouble).toInt
    UARTAdapter.connect(Seq(port.io), div, false)
  }
})
// DOC include end: WithUARTAdapter

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SPIFlashPort, chipId: Int) => {
    val spi_mem = Module(new SimSPIFlashModel(port.params.fSize, port.spiId, rdOnly)).suggestName(s"spi_mem${port.spiId}")
    spi_mem.io.sck := port.io.sck
    require(port.params.csWidth == 1, "I don't know what to do with your extra CS bits. Fix me please.")
    spi_mem.io.cs(0) := port.io.cs(0)
    spi_mem.io.dq.zip(port.io.dq).foreach { case (x, y) => x <> y }
    spi_mem.io.reset := th.harnessBinderReset
  }
})

class WithI2CTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: I2CPort, chipId: Int) => {
    port.io <> DontCare
  }
})

class WithSimBlockDevice extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: BlockDevicePort, chipId: Int) => {
    val sim_blkdev = Module(new SimBlockDevice(port.params))
    sim_blkdev.io.bdev <> port.io.bits
    sim_blkdev.io.clock := port.io.clock
    sim_blkdev.io.reset := th.harnessBinderReset
  }
})

class WithBlockDeviceModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: BlockDevicePort, chipId: Int) => {
    val blkdev_model = Module(new BlockDeviceModel(16, port.params))
    blkdev_model.io <> port.io.bits
    blkdev_model.clock := port.io.clock
    blkdev_model.reset := th.harnessBinderReset
  }
})

class WithLoopbackNIC extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: NICPort, chipId: Int) => {
    withClock(port.io.clock) { NicLoopback.connect(port.io.bits, port.params) }
  }
})

class WithSimNetwork extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: NICPort, chipId: Int) => {
    withClock(port.io.clock) { SimNetwork.connect(Some(port.io.bits), port.io.clock, th.harnessBinderReset.asBool) }
  }
})

class WithSimAXIMem extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4MemPort, chipId: Int) => {
    val mem = LazyModule(new SimAXIMem(port.edge, size=port.params.master.size)(Parameters.empty))
    withClock(port.io.clock) { Module(mem.module) }
    mem.io_axi4.head <> port.io.bits
  }
})

class WithBlackBoxSimMem(additionalLatency: Int = 0) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4MemPort, chipId: Int) => {
    // TODO FIX: This currently makes each SimDRAM contain the entire memory space
    val memSize = port.params.master.size
    val memBase = port.params.master.base
    val lineSize = 64 // cache block size
    val clockFreq = port.clockFreqMHz
    val mem = Module(new SimDRAM(memSize, lineSize, clockFreq, memBase, port.edge.bundle, chipId)).suggestName("simdram")

    mem.io.clock := port.io.clock
    mem.io.reset := th.harnessBinderReset.asAsyncReset
    mem.io.axi <> port.io.bits
    // Bug in Chisel implementation. See https://github.com/chipsalliance/chisel3/pull/1781
    def Decoupled[T <: Data](irr: IrrevocableIO[T]): DecoupledIO[T] = {
      require(DataMirror.directionOf(irr.bits) == Direction.Output, "Only safe to cast produced Irrevocable bits to Decoupled.")
      val d = Wire(new DecoupledIO(chiselTypeOf(irr.bits)))
      d.bits := irr.bits
      d.valid := irr.valid
      irr.ready := d.ready
      d
    }
    if (additionalLatency > 0) {
      withClock (port.io.clock) {
        mem.io.axi.aw  <> (0 until additionalLatency).foldLeft(Decoupled(port.io.bits.aw))((t, _) => Queue(t, 1, pipe=true))
        mem.io.axi.w   <> (0 until additionalLatency).foldLeft(Decoupled(port.io.bits.w ))((t, _) => Queue(t, 1, pipe=true))
        port.io.bits.b <> (0 until additionalLatency).foldLeft(Decoupled(mem.io.axi.b   ))((t, _) => Queue(t, 1, pipe=true))
        mem.io.axi.ar  <> (0 until additionalLatency).foldLeft(Decoupled(port.io.bits.ar))((t, _) => Queue(t, 1, pipe=true))
        port.io.bits.r <> (0 until additionalLatency).foldLeft(Decoupled(mem.io.axi.r   ))((t, _) => Queue(t, 1, pipe=true))
      }
    }
  }
})

class WithSimAXIMMIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4MMIOPort, chipId: Int) => {
    val mmio_mem = LazyModule(new SimAXIMem(port.edge, size = port.params.size)(Parameters.empty))
    withClock(port.io.clock) { Module(mmio_mem.module).suggestName("mmio_mem") }
    mmio_mem.io_axi4.head <> port.io.bits
  }
})

class WithTieOffInterrupts extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ExtIntPort, chipId: Int) => {
    port.io := 0.U
  }
})

class WithTieOffL2FBusAXI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: AXI4InPort, chipId: Int) => {
    port.io := DontCare
    port.io.bits.aw.valid := false.B
    port.io.bits.w.valid := false.B
    port.io.bits.b.ready := false.B
    port.io.bits.ar.valid := false.B
    port.io.bits.r.ready := false.B
  }
})

class WithSimJTAGDebug extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val dtm_success = WireInit(false.B)
    when (dtm_success) { th.chiptopSuccess(chipId) := true.B }
    val jtag_wire = Wire(new JTAGIO)
    jtag_wire.TDO.data := port.io.TDO
    jtag_wire.TDO.driven := true.B
    port.io.TCK := jtag_wire.TCK
    port.io.TMS := jtag_wire.TMS
    port.io.TDI := jtag_wire.TDI
    port.io.reset.foreach(_ := th.harnessBinderReset.asBool)
    val jtag = Module(new SimJTAG(tickDelay=3))
    jtag.connect(jtag_wire, th.harnessBinderClock, th.harnessBinderReset.asBool, ~(th.harnessBinderReset.asBool), dtm_success)
  }
})

class WithSimDMI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: DMIPort, chipId: Int) => {
    val dtm_success = WireInit(false.B)
    when (dtm_success) { th.chiptopSuccess(chipId) := true.B }
    val dtm = Module(new TestchipSimDTM()(Parameters.empty)).connect(th.harnessBinderClock, th.harnessBinderReset.asBool, port.io, dtm_success)
  }
})

class WithTiedOffJTAG extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    port.io.TCK := true.B.asClock
    port.io.TMS := true.B
    port.io.TDI := true.B
    port.io.reset.foreach(_ := true.B)
  }
})

class WithTiedOffDMI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: DMIPort, chipId: Int) => {
    port.io.dmi.req.valid := false.B
    port.io.dmi.req.bits := DontCare
    port.io.dmi.resp.ready := true.B
    port.io.dmiClock := false.B.asClock
    port.io.dmiReset := true.B
  }
})

// If tieoffs is specified, a list of serial portIds to tie off
// If tieoffs is unspecified, ties off all serial ports
class WithSerialTLTiedOff(tieoffs: Option[Seq[Int]] = None) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) if (tieoffs.map(_.contains(port.portId)).getOrElse(true)) => {
    port.io match {
      case io: DecoupledPhitIO => io.out.ready := false.B; io.in.valid := false.B; io.in.bits := DontCare;
      case io: CreditedSourceSyncPhitIO => {
        io.clock_in := false.B.asClock
        io.reset_in := false.B.asAsyncReset
        io.in := DontCare
      }
    }
    port.io match {
      case io: HasClockOut =>
      case io: HasClockIn => io.clock_in := false.B.asClock
      case io: CreditedSourceSyncPhitIO =>
      case _ =>
    }
  }
})

class WithSimTSIOverSerialTL(fast: Boolean = false) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) if (port.portId == 0) => {
    port.io match {
      case io: HasClockOut =>
      case io: HasClockIn => io.clock_in := th.harnessBinderClock
      case io: CreditedSourceSyncPhitIO => io.clock_in := th.harnessBinderClock; io.reset_in := th.harnessBinderReset
    }

    port.io match {
      case io: DecoupledPhitIO => {
        // If the port is locally synchronous (provides a clock), drive everything with that clock
        // Else, drive everything with the harnes clock
        val clock = port.io match {
          case io: HasClockOut => io.clock_out
          case io: HasClockIn => th.harnessBinderClock
        }
        withClock(clock) {
          if (fast) {
            val ram = Module(LazyModule(new FastRAM(port.serdesser, port.params, chipId = chipId)(port.serdesser.p)).module)
            ram.io.ser.in <> io.out
            io.in <> ram.io.ser.out
            val success = SimTSI.connect(ram.io.tsi, clock, th.harnessBinderReset, chipId)
            when (success) { th.chiptopSuccess(chipId) := true.B }
          } else {
            val ram = Module(LazyModule(new SerialRAM(port.serdesser, port.params)(port.serdesser.p)).module)
            ram.io.ser.in <> io.out
            io.in <> ram.io.ser.out
            val success = SimTSI.connect(ram.io.tsi, clock, th.harnessBinderReset, chipId)
            when (success) { th.chiptopSuccess(chipId) := true.B }
          }
        }
      }
    }
  }
})

class WithDriveChipIdPin extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ChipIdPort, chipId: Int) => {
    require(chipId < math.pow(2, port.io.getWidth), "ID Pin is not wide enough")
    port.io := chipId.U
  }
})

class WithSimUARTToUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    UARTAdapter.connect(Seq(port.io.uart),
      baudrate=port.io.uart.c.initBaudRate,
      clockFrequency=th.getHarnessBinderClockFreqHz.toInt,
      forcePty=true)
  }
})

class WithSimTSIToUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val freq = th.getHarnessBinderClockFreqHz.toInt
    val uart_to_serial = Module(new UARTToSerial(freq, port.io.uart.c))
    val serial_width_adapter = Module(new SerialWidthAdapter(8, TSI.WIDTH))
    val success = SimTSI.connect(Some(TSIIO(serial_width_adapter.io.wide)), th.harnessBinderClock, th.harnessBinderReset)
    when (success) { th.chiptopSuccess(chipId) := true.B }
    assert(!uart_to_serial.io.dropped)
    serial_width_adapter.io.narrow.flipConnect(uart_to_serial.io.serial)
    uart_to_serial.io.uart.rxd := port.io.uart.txd
    port.io.uart.rxd := uart_to_serial.io.uart.txd
  }
})

class WithTraceGenSuccess extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SuccessPort, chipId: Int) => {
    when (port.io) { th.chiptopSuccess(chipId) := true.B }
  }
})

class WithCospike extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TracePort, chipId: Int) => {
    port.io.traces.zipWithIndex.map(t => SpikeCosim(t._1, t._2, port.cosimCfg))
  }
})


class WithCustomBootPinPlusArg extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: CustomBootPort, chipId: Int) => {
    val pin = PlusArg("custom_boot_pin", width=1)
    port.io := pin
  }
})

class WithClockFromHarness extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ClockPort, chipId: Int) => {
// DOC include start: HarnessClockInstantiatorEx
    port.io := th.harnessClockInstantiator.requestClockMHz(s"clock_${port.freqMHz}MHz", port.freqMHz)
// DOC include end: HarnessClockInstantiatorEx
  }
})

class WithResetFromHarness extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ResetPort, chipId: Int) => {
    port.io := th.referenceReset.asAsyncReset
  }
})

class WithOffchipBusSelPlusArg extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: OffchipSelPort, chipId: Int) => {
    val pin = PlusArg("offchip_sel", width=port.io.getWidth)
    port.io := pin
  }
})

class WithCTCTiedOff extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: CTCPort, chipId: Int) => {
    port.io match {
      case io: CreditedSourceSyncPhitIO => {
        io.clock_in := false.B.asClock
        io.reset_in := false.B.asAsyncReset
        io.in := DontCare
      }
      case io: CTCBridgeIO => {
        io.manager_flit := DontCare
        io.manager_flit.in.valid := false.B
        io.manager_flit.out.ready := false.B
        io.client_flit := DontCare
        io.client_flit.in.valid := false.B
        io.client_flit.out.ready := false.B
      }
    }
  }
})

class WithCTCLoopback extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: CTCPort, chipId: Int) => {
    port.io match {
      case io: CreditedSourceSyncPhitIO => {
        io.clock_in := io.clock_out
        io.reset_in := io.reset_out
        io.in := io.out
      }
      case io: CTCBridgeIO => {
        io.client_flit.in <> io.manager_flit.out
        io.manager_flit.in <> io.client_flit.out
      }
    }
  }
})

// Simulation-only clock source for the RGMII MAC.
// A 125 MHz gtx_clk plus a 90-degree lagging gtx_clk90.
// Uses #delays (supported by the chipyard Verilator --timing flow, same as ClockSourceAtFreqMHz).
class EthClockSource extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val gtx_clk   = Output(Clock())
    val gtx_clk90 = Output(Clock())
  })
  setInline("EthClockSource.v",
    s"""
      |module EthClockSource (
      |    output gtx_clk,
      |    output gtx_clk90
      |);
      |  timeunit 1ns/1ps;
      |  reg clk_i   = 1'b0;
      |  reg clk90_i = 1'b0;
      |  // 125 MHz: 8 ns period, 4 ns half-period. clk90 lags clk by 2 ns (= 90 degrees),
      |  // which centers rgmii_tx_clk in the data eye for a clean TX->RX loopback.
      |  always #4.0 clk_i = ~clk_i;
      |  initial begin
      |    #2.0;
      |    forever #4.0 clk90_i = ~clk90_i;
      |  end
      |  assign gtx_clk   = clk_i;
      |  assign gtx_clk90 = clk90_i;
      |endmodule
      |""".stripMargin)
}

// Drives the RGMII Ethernet peripheral for RTL simulation.
// Generates gtx_clk/gtx_clk90, synchronizes gtx_rst, and loops the RGMII transmit pins straight back to the receive pins.
// Pair with rivet.wrapper.WithEthernetRGMIISim (which builds the MAC with target="SIM").
class WithEthernetRGMIILoopback extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetRGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource)
    port.io.gtx_clk   := ethClk.io.gtx_clk
    port.io.gtx_clk90 := ethClk.io.gtx_clk90
    port.io.gtx_rst   := ResetCatchAndSync(ethClk.io.gtx_clk, th.harnessBinderReset.asBool)

    // RGMII TX -> RX loopback.
    // rgmii_tx_clk is generated off gtx_clk90, so its edges land
    // in the middle of the data driven off gtx_clk -> the RX iddr samples stable data.
    port.io.phy.rgmii_rx_clk := port.io.phy.rgmii_tx_clk
    port.io.phy.rgmii_rxd    := port.io.phy.rgmii_txd
    port.io.phy.rgmii_rx_ctl := port.io.phy.rgmii_tx_ctl
    // phy_reset_n is a chip output, nothing to drive in sim.
  }
})

// Simulation-only 125 MHz single-phase clock for the GMII MAC.
// GMII is SDR (no DDR), so unlike RGMII there is no 90-degree companion clock.
class EthClockSource125 extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk = Output(Clock())
  })
  setInline("EthClockSource125.v",
    s"""
      |module EthClockSource125 (
      |    output clk
      |);
      |  timeunit 1ns/1ps;
      |  reg clk_i = 1'b0;
      |  always #4.0 clk_i = ~clk_i;  // 125 MHz: 8 ns period
      |  assign clk = clk_i;
      |endmodule
      |""".stripMargin)
}

// Drives the GMII Ethernet peripheral for RTL simulation.
// Generates gtx_clk, synchronizes gtx_rst, and loops the GMII transmit signals straight back to the receive signals.
// Pair with rivet.wrapper.WithEthernetGMIISim.
class WithEthernetGMIILoopback extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource125)
    port.io.gtx_clk := ethClk.io.clk
    port.io.gtx_rst := ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)

    // GMII TX -> RX loopback.
    // The MAC forwards gtx_clk onto gmii_tx_clk, so feeding it back as gmii_rx_clk samples the looped tx data/control cleanly.
    port.io.phy.gmii_rx_clk := port.io.phy.gmii_tx_clk
    port.io.phy.gmii_rxd    := port.io.phy.gmii_txd
    port.io.phy.gmii_rx_dv  := port.io.phy.gmii_tx_en
    port.io.phy.gmii_rx_er  := port.io.phy.gmii_tx_er
    port.io.phy.mii_tx_clk  := port.io.phy.gmii_tx_clk
  }
})

// Simulation-only 156.25 MHz single-phase clock for the 10G MAC (~6.4 ns period).
class EthClockSource156 extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk = Output(Clock())
  })
  setInline("EthClockSource156.v",
    s"""
      |module EthClockSource156 (
      |    output clk
      |);
      |  timeunit 1ns/1ps;
      |  reg clk_i = 1'b0;
      |  always #3.2 clk_i = ~clk_i;  // 156.25 MHz: 6.4 ns period
      |  assign clk = clk_i;
      |endmodule
      |""".stripMargin)
}

// Drives the XGMII Ethernet peripheral for RTL simulation.
// A single 156.25 MHz clock feeds both tx_clk and rx_clk, and the XGMII transmit buses loop straight back to the receive buses.
// Pair with rivet.wrapper.WithEthernetXGMIISim.
class WithEthernetXGMIILoopback extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetXGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource156)
    port.io.tx_clk := ethClk.io.clk
    port.io.rx_clk := ethClk.io.clk
    port.io.tx_rst := ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)
    port.io.rx_rst := ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)

    // XGMII TX -> RX loopback.
    port.io.phy.xgmii_rxd := port.io.phy.xgmii_txd
    port.io.phy.xgmii_rxc := port.io.phy.xgmii_txc
  }
})

/** Connects an RGMII Ethernet peripheral to a file-backed, full-duplex simulation peer. */
class WithEthernetRGMIIFileModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetRGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource)
    val peer = Module(new RgmiiEthernetFileModel(chipId))
    val gtxReset = ResetCatchAndSync(ethClk.io.gtx_clk, th.harnessBinderReset.asBool)

    port.io.gtx_clk := ethClk.io.gtx_clk
    port.io.gtx_clk90 := ethClk.io.gtx_clk90
    port.io.gtx_rst := gtxReset
    peer.io.gtx_clk := ethClk.io.gtx_clk
    peer.io.gtx_clk90 := ethClk.io.gtx_clk90
    peer.io.gtx_rst := gtxReset

    peer.io.phy.rgmii_rx_clk := port.io.phy.rgmii_tx_clk
    peer.io.phy.rgmii_rxd := port.io.phy.rgmii_txd
    peer.io.phy.rgmii_rx_ctl := port.io.phy.rgmii_tx_ctl
    port.io.phy.rgmii_rx_clk := peer.io.phy.rgmii_tx_clk
    port.io.phy.rgmii_rxd := peer.io.phy.rgmii_txd
    port.io.phy.rgmii_rx_ctl := peer.io.phy.rgmii_tx_ctl
  }
})

/** Connects a GMII Ethernet peripheral to a file-backed, full-duplex simulation peer. */
class WithEthernetGMIIFileModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource125)
    val peer = Module(new GmiiEthernetFileModel(chipId))
    val gtxReset = ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)

    port.io.gtx_clk := ethClk.io.clk
    port.io.gtx_rst := gtxReset
    peer.io.gtx_clk := ethClk.io.clk
    peer.io.gtx_rst := gtxReset

    peer.io.phy.gmii_rx_clk := port.io.phy.gmii_tx_clk
    peer.io.phy.gmii_rxd := port.io.phy.gmii_txd
    peer.io.phy.gmii_rx_dv := port.io.phy.gmii_tx_en
    peer.io.phy.gmii_rx_er := port.io.phy.gmii_tx_er
    peer.io.phy.mii_tx_clk := port.io.phy.gmii_tx_clk

    port.io.phy.gmii_rx_clk := peer.io.phy.gmii_tx_clk
    port.io.phy.gmii_rxd := peer.io.phy.gmii_txd
    port.io.phy.gmii_rx_dv := peer.io.phy.gmii_tx_en
    port.io.phy.gmii_rx_er := peer.io.phy.gmii_tx_er
    port.io.phy.mii_tx_clk := peer.io.phy.gmii_tx_clk
  }
})

/** Connects an XGMII Ethernet peripheral to a file-backed, full-duplex simulation peer. */
class WithEthernetXGMIIFileModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetXGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource156)
    val peer = Module(new XgmiiEthernetFileModel(chipId))
    val lineReset = ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)

    port.io.tx_clk := ethClk.io.clk
    port.io.rx_clk := ethClk.io.clk
    port.io.tx_rst := lineReset
    port.io.rx_rst := lineReset
    peer.io.tx_clk := ethClk.io.clk
    peer.io.rx_clk := ethClk.io.clk
    peer.io.tx_rst := lineReset
    peer.io.rx_rst := lineReset

    peer.io.phy.xgmii_rxd := port.io.phy.xgmii_txd
    peer.io.phy.xgmii_rxc := port.io.phy.xgmii_txc
    port.io.phy.xgmii_rxd := peer.io.phy.xgmii_txd
    port.io.phy.xgmii_rxc := peer.io.phy.xgmii_txc
  }
})

/** Connects RGMII to a UDP/IPv4 file-backed, full-duplex simulation peer. */
class WithEthernetRGMIIUdpFileModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetRGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource)
    val peer = Module(new RgmiiEthernetUdpFileModel(chipId))
    val gtxReset = ResetCatchAndSync(ethClk.io.gtx_clk, th.harnessBinderReset.asBool)

    port.io.gtx_clk := ethClk.io.gtx_clk
    port.io.gtx_clk90 := ethClk.io.gtx_clk90
    port.io.gtx_rst := gtxReset
    peer.io.gtx_clk := ethClk.io.gtx_clk
    peer.io.gtx_clk90 := ethClk.io.gtx_clk90
    peer.io.gtx_rst := gtxReset

    peer.io.phy.rgmii_rx_clk := port.io.phy.rgmii_tx_clk
    peer.io.phy.rgmii_rxd := port.io.phy.rgmii_txd
    peer.io.phy.rgmii_rx_ctl := port.io.phy.rgmii_tx_ctl
    port.io.phy.rgmii_rx_clk := peer.io.phy.rgmii_tx_clk
    port.io.phy.rgmii_rxd := peer.io.phy.rgmii_txd
    port.io.phy.rgmii_rx_ctl := peer.io.phy.rgmii_tx_ctl
  }
})

/** Connects GMII to a UDP/IPv4 file-backed, full-duplex simulation peer. */
class WithEthernetGMIIUdpFileModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource125)
    val peer = Module(new GmiiEthernetUdpFileModel(chipId))
    val gtxReset = ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)

    port.io.gtx_clk := ethClk.io.clk
    port.io.gtx_rst := gtxReset
    peer.io.gtx_clk := ethClk.io.clk
    peer.io.gtx_rst := gtxReset

    peer.io.phy.gmii_rx_clk := port.io.phy.gmii_tx_clk
    peer.io.phy.gmii_rxd := port.io.phy.gmii_txd
    peer.io.phy.gmii_rx_dv := port.io.phy.gmii_tx_en
    peer.io.phy.gmii_rx_er := port.io.phy.gmii_tx_er
    peer.io.phy.mii_tx_clk := port.io.phy.gmii_tx_clk

    port.io.phy.gmii_rx_clk := peer.io.phy.gmii_tx_clk
    port.io.phy.gmii_rxd := peer.io.phy.gmii_txd
    port.io.phy.gmii_rx_dv := peer.io.phy.gmii_tx_en
    port.io.phy.gmii_rx_er := peer.io.phy.gmii_tx_er
    port.io.phy.mii_tx_clk := peer.io.phy.gmii_tx_clk
  }
})

/** Connects XGMII to a UDP/IPv4 file-backed, full-duplex simulation peer. */
class WithEthernetXGMIIUdpFileModel extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetXGMIIPort, chipId: Int) => {
    val ethClk = Module(new EthClockSource156)
    val peer = Module(new XgmiiEthernetUdpFileModel(chipId))
    val lineReset = ResetCatchAndSync(ethClk.io.clk, th.harnessBinderReset.asBool)

    port.io.tx_clk := ethClk.io.clk
    port.io.rx_clk := ethClk.io.clk
    port.io.tx_rst := lineReset
    port.io.rx_rst := lineReset
    peer.io.tx_clk := ethClk.io.clk
    peer.io.rx_clk := ethClk.io.clk
    peer.io.tx_rst := lineReset
    peer.io.rx_rst := lineReset

    peer.io.phy.xgmii_rxd := port.io.phy.xgmii_txd
    peer.io.phy.xgmii_rxc := port.io.phy.xgmii_txc
    port.io.phy.xgmii_rxd := peer.io.phy.xgmii_txd
    port.io.phy.xgmii_rxc := peer.io.phy.xgmii_txc
  }
})
