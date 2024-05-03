package chipyard.fpga.vc707

import chisel3._
import chisel3.experimental.{BaseModule}

import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.fpgashells.devices.xilinx.xilinxvc707pciex1.{HasSystemXilinxVC707PCIeX1ModuleImp, XilinxVC707PCIeX1IO}

import chipyard.{CanHaveMasterTLMemPort}
import chipyard.harness.{HarnessBinder}
import chipyard.iobinders._

/*** UART ***/
class WithVC707UARTHarnessBinder extends HarnessBinder({
  case (th: VC707FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vc707Outer.io_uart_bb.bundle <> port.io
  }
})

/*** SPI ***/
class WithVC707SPISDCardHarnessBinder extends HarnessBinder({
  case (th: VC707FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    th.vc707Outer.io_spi_bb.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithVC707DDRMemHarnessBinder extends HarnessBinder({
  case (th: VC707FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.vc707Outer.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})


class WithVC707GPIOHarnessBinder extends HarnessBinder({
  case (th: VC707FPGATestHarnessImp, port: GPIOPinsPort, chipId: Int) => {
    th.vc707Outer.io_gpio_bb(port.gpioId).bundle <> port.io
  }
})
