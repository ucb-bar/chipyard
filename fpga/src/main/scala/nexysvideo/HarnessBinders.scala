// See LICENSE for license details.
package chipyard.fpga.nexysvideo

import chisel3._
import chisel3.experimental.{Analog, attach}
import org.chipsalliance.diplomacy.lazymodule.LazyRawModuleImp
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag
import chipyard.harness._
import chipyard.iobinders._
import freechips.rocketchip.util.ElaborationArtefacts
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx.IOBUF
import testchipip.serdes._

import scala.io.Source

class WithNexysVideoUARTTSI(uartBaudRate: BigInt = 115200) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val nexysvideoth = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    nexysvideoth.io_uart_bb.bundle <> port.io.uart
    nexysvideoth.other_leds(1) := port.io.dropped
    nexysvideoth.other_leds(2) := port.io.tsi2tl_state(0)
    nexysvideoth.other_leds(3) := port.io.tsi2tl_state(1)
    nexysvideoth.other_leds(4) := port.io.tsi2tl_state(2)
    nexysvideoth.other_leds(5) := port.io.tsi2tl_state(3)
  }
})

class WithNexysVideoDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    val bundles = nexysTh.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

// Uses PMOD JA/JB
class WithNexysVideoSerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) => {
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io
    
    harnessIO match {
      case io: DecoupledPhitIO => {
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }
        val packagePinsWithPackageIOs = Seq(
          ("AB22", clkIO),
          ("AB21", IOPin(io.out.valid)),
          ("AB20", IOPin(io.out.ready)),
          ("AB18", IOPin(io.in.valid)),
          ("Y21", IOPin(io.in.ready)),
          ("AA21", IOPin(io.out.bits.phit, 0)),
          ("AA20", IOPin(io.out.bits.phit, 1)),
          ("AA18", IOPin(io.out.bits.phit, 2)),
          ("V9", IOPin(io.out.bits.phit, 3)),
          ("V8", IOPin(io.in.bits.phit, 0)),
          ("V7", IOPin(io.in.bits.phit, 1)),
          ("W7", IOPin(io.in.bits.phit, 2)),
          ("W9", IOPin(io.in.bits.phit, 3))
        )
        packagePinsWithPackageIOs foreach { case (pin, io) => {
          nexysTh.xdc.addPackagePin(io, pin)
          nexysTh.xdc.addIOStandard(io, "LVCMOS33")
        }}

        // Don't add IOB to the clock, if its an input
        io match {
          case io: DecoupledInternalSyncPhitIO => packagePinsWithPackageIOs foreach { case (pin, io) => {
            nexysTh.xdc.addIOB(io)
          }}
          case io: DecoupledExternalSyncPhitIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) => {
            nexysTh.xdc.addIOB(io)
          }}
        }

        val serialTLFreqMHz = port.params.phyParams match {
          case p: DecoupledInternalSyncSerialPhyParams => p.freqMHz.toDouble
          case p: CreditedSourceSyncSerialPhyParams    => p.freqMHz.toDouble
          case _: DecoupledExternalSyncSerialPhyParams => 100.0
        }
        nexysTh.sdc.addClock("ser_tl_clock", clkIO, serialTLFreqMHz)
        nexysTh.sdc.addGroup(pins = Seq(clkIO))
        nexysTh.xdc.clockDedicatedRouteFalse(clkIO)
      }
    }
  }
})

class WithNexysVideoEthernet extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetRGMIIPort, chipId: Int) =>
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    val harnessIO = IO(chiselTypeOf(port.io.phy)).suggestName("ethernet_io")
    harnessIO <> port.io.phy

    port.io.gtx_clk := nexysTh.ethClock_125.get.in.head._1.clock
    port.io.gtx_clk90 := nexysTh.ethClock_125_90.get.in.head._1.clock
    port.io.gtx_rst := nexysTh.ethClock_125.get.in.head._1.reset.asBool
    val packagePinsWithPackageIOs = Seq(
      ("AB16", IOPin(harnessIO.rgmii_rxd(0))), // Sch=ETH_RXD0
      ("AA15", IOPin(harnessIO.rgmii_rxd(1))), // Sch=ETH_RXD1
      ("AB15", IOPin(harnessIO.rgmii_rxd(2))), // Sch=ETH_RXD2
      ("AB11", IOPin(harnessIO.rgmii_rxd(3))), // Sch=ETH_RXD3
      ("Y12",  IOPin(harnessIO.rgmii_txd(0))), // Sch=ETH_TXD0
      ("W12",  IOPin(harnessIO.rgmii_txd(1))), // Sch=ETH_TXD1
      ("W11",  IOPin(harnessIO.rgmii_txd(2))), // Sch=ETH_TXD2
      ("Y11",  IOPin(harnessIO.rgmii_txd(3))), // Sch=ETH_TXD3
      ("AA14", IOPin(harnessIO.rgmii_tx_clk)), // Sch=ETH_TXCK
      ("V10",  IOPin(harnessIO.rgmii_tx_ctl)), // Sch=ETH_TXCTL
      ("V13",  IOPin(harnessIO.rgmii_rx_clk)), // Sch=ETH_RXCK
      ("W10",  IOPin(harnessIO.rgmii_rx_ctl))  // Sch=ETH_RXCTL
    )
    packagePinsWithPackageIOs foreach { case (pin, io) =>
      nexysTh.xdc.addPackagePin(io, pin)
      nexysTh.xdc.addIOStandard(io, "LVCMOS25")
    }

    // PHY reset is in a 3.3 V bank; RGMII data pins are 2.5 V.
    val phyResetIO = IOPin(harnessIO.phy_reset_n)
    nexysTh.xdc.addPackagePin(phyResetIO, "U7")
    nexysTh.xdc.addIOStandard(phyResetIO, "LVCMOS33")

    // Ethernet clock
    nexysTh.sdc.addClock("rgmii_rx_clk", IOPin(harnessIO.rgmii_rx_clk), 125)
    nexysTh.sdc.addGroup(clocks = Seq("rgmii_rx_clk"))

    // Capture RX data directly from rgmii_rx_clk through BUFIO/IDDR.
    // No FPGA input delay is applied.
    val rgmiiXdcResource = "nexysvideo/ethernet-rgmii.xdc"
    val rgmiiXdcStream = Option(Thread.currentThread.getContextClassLoader.getResourceAsStream(rgmiiXdcResource))
      .getOrElse(throw new RuntimeException(s"Missing resource: $rgmiiXdcResource"))
    val rgmiiXdcSource = Source.fromInputStream(rgmiiXdcStream)
    val rgmiiXdc = try {
      rgmiiXdcSource.mkString
    } finally {
      rgmiiXdcSource.close()
    }

    ElaborationArtefacts.add("nexysvideo_ethernet_rgmii.xdc", rgmiiXdc)
    ElaborationArtefacts.add(
      "nexysvideo_ethernet_rgmii.vivado.tcl",
      """set rgmii_vivado_tcl [file normalize [info script]]
        |set rgmii_xdc [string map {.nexysvideo_ethernet_rgmii.vivado.tcl .nexysvideo_ethernet_rgmii.xdc} $rgmii_vivado_tcl]
        |add_files -quiet -norecurse -fileset [current_fileset -constrset] $rgmii_xdc
        |""".stripMargin
    )
})

// MDIO PHY-management pins.
// MDC is a plain output (Sch=eth_mdc, AA16).
// MDIO is a bidirectional open-drain pad driven through an IOBUF (Sch=eth_mdio, Y16).
class WithNexysVideoMDIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetMDIOPort, chipId: Int) =>
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]

    val mdcIO = IO(Output(Bool())).suggestName("eth_mdc")
    mdcIO := port.io.mdc
    val mdcPin = IOPin(mdcIO)
    nexysTh.xdc.addPackagePin(mdcPin, "AA16")
    nexysTh.xdc.addIOStandard(mdcPin, "LVCMOS25")

    val mdioPad = IO(Analog(1.W)).suggestName("eth_mdio")
    val iobuf = Module(new IOBUF())
    iobuf.io.I := port.io.mdio_o
    iobuf.io.T := port.io.mdio_t
    port.io.mdio_i := iobuf.io.O
    attach(iobuf.io.IO, mdioPad)
    val mdioPin = IOPin(mdioPad)
    nexysTh.xdc.addPackagePin(mdioPin, "Y16")
    nexysTh.xdc.addIOStandard(mdioPin, "LVCMOS25")
})
