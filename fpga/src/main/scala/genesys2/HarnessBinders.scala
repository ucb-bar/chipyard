// See LICENSE for license details.
package chipyard.fpga.genesys2

import chipyard.harness._
import chipyard.iobinders._
import chisel3._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.util.ElaborationArtefacts
import org.chipsalliance.diplomacy.lazymodule.LazyRawModuleImp
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag
import sifive.fpgashells.ip.xilinx.IOBUF
import sifive.fpgashells.shell._
import testchipip.serdes._

import scala.io.Source

class WithGenesys2UARTTSI(uartBaudRate: BigInt = 115200) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val genesys2th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    genesys2th.io_uart_bb.bundle <> port.io.uart
    genesys2th.other_leds(1) := port.io.dropped
    genesys2th.other_leds(2) := port.io.tsi2tl_state(0)
    genesys2th.other_leds(3) := port.io.tsi2tl_state(1)
    genesys2th.other_leds(4) := port.io.tsi2tl_state(2)
    genesys2th.other_leds(5) := port.io.tsi2tl_state(3)
  }
})

class WithGenesys2DDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val genesys2Th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    val bundles = genesys2Th.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

// Uses PMOD JA/JB
class WithGenesys2SerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) =>
    val genesys2Th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io

    harnessIO match {
      case io: DecoupledPhitIO =>
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }
        val packagePinsWithPackageIOs = Seq(
          ("U27", clkIO),
          ("U28", IOPin(io.out.valid)),
          ("T26", IOPin(io.out.ready)),
          ("T27", IOPin(io.in.valid)),
          ("T22", IOPin(io.in.ready)),
          ("V29", IOPin(io.out.bits.phit, 0)),
          ("V30", IOPin(io.out.bits.phit, 1)),
          ("V25", IOPin(io.out.bits.phit, 2)),
          ("W26", IOPin(io.out.bits.phit, 3)),
          ("T25", IOPin(io.in.bits.phit, 0)),
          ("U25", IOPin(io.in.bits.phit, 1)),
          ("U22", IOPin(io.in.bits.phit, 2)),
          ("U23", IOPin(io.in.bits.phit, 3))
        )
        packagePinsWithPackageIOs foreach { case (pin, io) =>
          genesys2Th.xdc.addPackagePin(io, pin)
          genesys2Th.xdc.addIOStandard(io, "LVCMOS33")
        }

        // Don't add IOB to the clock, if its an input
        io match {
          case io: DecoupledInternalSyncPhitIO => packagePinsWithPackageIOs foreach { case (pin, io) =>
            genesys2Th.xdc.addIOB(io)
          }
          case io: DecoupledExternalSyncPhitIO => packagePinsWithPackageIOs.drop(1).foreach { case (pin, io) =>
            genesys2Th.xdc.addIOB(io)
          }
        }

        val serialTLFreqMHz = port.params.phyParams match {
          case p: DecoupledInternalSyncSerialPhyParams => p.freqMHz.toDouble
          case p: CreditedSourceSyncSerialPhyParams    => p.freqMHz.toDouble
          case _: DecoupledExternalSyncSerialPhyParams => 100.0
        }
        genesys2Th.sdc.addClock("ser_tl_clock", clkIO, serialTLFreqMHz)
        genesys2Th.sdc.addGroup(pins = Seq(clkIO))
        genesys2Th.xdc.clockDedicatedRouteFalse(clkIO)
    }
})

class WithGenesys2Ethernet extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetRGMIIPort, chipId: Int) =>
    val genesys2Th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]
    val io = IO(chiselTypeOf(port.io.phy)).suggestName("ethernet_io")
    io <> port.io.phy

    port.io.gtx_clk := genesys2Th.ethClock_125.get.in.head._1.clock
    port.io.gtx_clk90 := genesys2Th.ethClock_125_90.get.in.head._1.clock
    port.io.gtx_rst := genesys2Th.ethClock_125.get.in.head._1.reset.asBool
    val packagePinsWithPackageIOs = Seq(
      ("AJ14", IOPin(io.rgmii_rxd(0))), // Sch=ETH_RXD0
      ("AH14", IOPin(io.rgmii_rxd(1))), // Sch=ETH_RXD1
      ("AK13", IOPin(io.rgmii_rxd(2))), // Sch=ETH_RXD2
      ("AJ13", IOPin(io.rgmii_rxd(3))), // Sch=ETH_RXD3
      ("AJ12", IOPin(io.rgmii_txd(0))), // Sch=ETH_TXD0
      ("AK11", IOPin(io.rgmii_txd(1))), // Sch=ETH_TXD1
      ("AJ11", IOPin(io.rgmii_txd(2))), // Sch=ETH_TXD2
      ("AK10", IOPin(io.rgmii_txd(3))), // Sch=ETH_TXD3
      ("AE10", IOPin(io.rgmii_tx_clk)), // Sch=ETH_TX_CLK
      ("AK14", IOPin(io.rgmii_tx_ctl)), // Sch=ETH_TX_EN
      ("AG10", IOPin(io.rgmii_rx_clk)), // Sch=ETH_RX_CLK
      ("AH11", IOPin(io.rgmii_rx_ctl))  // Sch=ETH_RX_CTL
    )
    packagePinsWithPackageIOs foreach { case (pin, io) =>
      genesys2Th.xdc.addPackagePin(io, pin)
      genesys2Th.xdc.addIOStandard(io, "LVCMOS15")
    }

    // PHY reset is in a 3.3 V bank; RGMII data pins are 1.5 V.
    val phyResetIO = IOPin(io.phy_reset_n)
    genesys2Th.xdc.addPackagePin(phyResetIO, "AH24")
    genesys2Th.xdc.addIOStandard(phyResetIO, "LVCMOS33")

    // Ethernet clock
    genesys2Th.sdc.addClock("rgmii_rx_clk", IOPin(io.rgmii_rx_clk), 125)
    genesys2Th.sdc.addGroup(clocks = Seq("rgmii_rx_clk"))

    val rgmiiXdcResource = "genesys2/ethernet-rgmii.xdc"
    val rgmiiXdcStream = Option(Thread.currentThread.getContextClassLoader.getResourceAsStream(rgmiiXdcResource))
      .getOrElse(throw new RuntimeException(s"Missing resource: $rgmiiXdcResource"))
    val rgmiiXdcSource = Source.fromInputStream(rgmiiXdcStream)
    val rgmiiXdc = try {
      rgmiiXdcSource.mkString
    } finally {
      rgmiiXdcSource.close()
    }

    ElaborationArtefacts.add("genesys2_ethernet_rgmii.xdc", rgmiiXdc)
    ElaborationArtefacts.add(
      "genesys2_ethernet_rgmii.vivado.tcl",
      """set rgmii_vivado_tcl [file normalize [info script]]
        |set rgmii_xdc [string map {.genesys2_ethernet_rgmii.vivado.tcl .genesys2_ethernet_rgmii.xdc} $rgmii_vivado_tcl]
        |add_files -quiet -norecurse -fileset [current_fileset -constrset] $rgmii_xdc
        |""".stripMargin
    )
})

// MDIO PHY-management pins.
// MDC is a plain output (Sch=eth_mdc, AF12).
// MDIO is a bidirectional open-drain pad driven through an IOBUF (Sch=eth_mdio, AG12).
class WithGenesys2MDIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: EthernetMDIOPort, chipId: Int) =>
    val genesys2Th = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Genesys2Harness]

    val mdcIO = IO(Output(Bool())).suggestName("eth_mdc")
    mdcIO := port.io.mdc
    val mdcPin = IOPin(mdcIO)
    genesys2Th.xdc.addPackagePin(mdcPin, "AF12")
    genesys2Th.xdc.addIOStandard(mdcPin, "LVCMOS15")

    val mdioPad = IO(Analog(1.W)).suggestName("eth_mdio")
    val iobuf = Module(new IOBUF())
    iobuf.io.I := port.io.mdio_o
    iobuf.io.T := port.io.mdio_t
    port.io.mdio_i := iobuf.io.O
    attach(iobuf.io.IO, mdioPad)
    val mdioPin = IOPin(mdioPad)
    genesys2Th.xdc.addPackagePin(mdioPin, "AG12")
    genesys2Th.xdc.addIOStandard(mdioPin, "LVCMOS15")
})
