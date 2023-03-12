package chipyard.fpga.vc707

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.fpgashells.devices.xilinx.xilinxvc707pciex1.{HasSystemXilinxVC707PCIeX1ModuleImp, XilinxVC707PCIeX1IO}

import chipyard.{CanHaveMasterTLMemPort}
import chipyard.harness.{OverrideHarnessBinder}

/*** UART ***/
class WithVC707UARTHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule, ports: Seq[UARTPortIO]) => {
    th match { case vc707th: VC707FPGATestHarnessImp => {
      vc707th.vc707Outer.io_uart_bb.bundle <> ports.head
    }}
  }
})

/*** SPI ***/
class WithVC707SPISDCardHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripherySPI, th: BaseModule, ports: Seq[SPIPortIO]) => {
    th match { case vc707th: VC707FPGATestHarnessImp => {
      vc707th.vc707Outer.io_spi_bb.bundle <> ports.head
    }}
  }
})

/*** Experimental DDR ***/
class WithVC707DDRMemHarnessBinder extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: BaseModule, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case vc707th: VC707FPGATestHarnessImp => {
      require(ports.size == 1)

      val bundles = vc707th.vc707Outer.ddrClient.out.map(_._1)
      val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
      bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
      ddrClientBundle <> ports.head
    }}
  }
})
