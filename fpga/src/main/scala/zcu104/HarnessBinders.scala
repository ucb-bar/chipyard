package chipyard.fpga.zcu104

import chipyard.harness._
import chipyard.iobinders._
import chisel3._
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag

/*** UART ***/
class WithUART extends HarnessBinder({
  case (th: ZCU104FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.zcu104Outer.io_uart_bb.bundle <> port.io
  }
})

/** Connect UART-TSI to PL UART2; the corresponding config disables UART. */
class WithZCU104UARTTSI extends HarnessBinder({
  case (th: ZCU104FPGATestHarnessImp, port: UARTTSIPort, chipId: Int) => {
    th.zcu104Outer.io_uart_bb.bundle <> port.io.uart
  }
})

/*** SPI ***/
class WithSPISDCard extends HarnessBinder({
  case (th: ZCU104FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    th.zcu104Outer.io_spi_bb.get.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends HarnessBinder({
  case (th: ZCU104FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.zcu104Outer.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithJTAG extends HarnessBinder({
  case (th: ZCU104FPGATestHarnessImp, port: JTAGPort, chipId: Int) => {
    val jtag_io = th.zcu104Outer.jtagPlacedOverlay.overlayOutput.jtag.getWrappedValue
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
