package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{BaseModule}

import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.fpgashells.shell._
import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO, UARTParams}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}

import freechips.rocketchip.diplomacy.{LazyRawModuleImp}

import chipyard._
import chipyard.harness._
import chipyard.iobinders._

/*** UART ***/
class WithUART extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vcu118Outer.io_uart_bb.bundle <> port.io
  }
})

/*** SPI ***/
class WithSPISDCard extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    th.vcu118Outer.io_spi_bb.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.vcu118Outer.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

//Bare Metal Extension
class WithVCU118UARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val rawModule = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[VCU118FPGATestHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("AW25" , IOPin(harnessIO.rxd)),
      ("BB21", IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      rawModule.xdc.addPackagePin(io, pin)
      rawModule.xdc.addIOStandard(io, "LVCMOS18")
      rawModule.xdc.addIOB(io)
    } }

    rawModule.all_leds(0) := port.io.dropped
    rawModule.all_leds(1) := port.io.dropped
    rawModule.all_leds(2) := port.io.dropped
    rawModule.all_leds(3) := port.io.dropped
    rawModule.all_leds(4) := port.io.tsi2tl_state(0)
    rawModule.all_leds(5) := port.io.tsi2tl_state(1)
    rawModule.all_leds(6) := port.io.tsi2tl_state(2)
    rawModule.all_leds(7) := port.io.tsi2tl_state(3)
  }
})

class WithJTAG extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: JTAGPort, chipId: Int) => {
    val jtag_io = th.vcu118Outer.jtagPlacedOverlay.overlayOutput.jtag.getWrappedValue
    port.io.TCK := jtag_io.TCK
    port.io.TMS := jtag_io.TMS
    port.io.TDI := jtag_io.TDI
    port.io.reset.foreach(_ := th.referenceReset)
    jtag_io.TDO.data := port.io.TDO
    jtag_io.TDO.driven := true.B
    // ignore srst_n
    jtag_io.srst_n := DontCare
  }
})
