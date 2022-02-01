package chipyard.fpga.arty100t

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}

import chipyard.{HasHarnessSignalReferences, CanHaveMasterTLMemPort}
import chipyard.harness.{OverrideHarnessBinder}

/*** UART ***/
class WithUART extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case arty100tth: Arty100TFPGATestHarnessImp => {
      arty100tth.arty100tOuter.io_uart_bb.bundle <> ports.head
    } }
  }
})

/*** SPI ***/
class WithSPISDCard extends OverrideHarnessBinder({
  (system: HasPeripherySPI, th: BaseModule with HasHarnessSignalReferences, ports: Seq[SPIPortIO]) => {
    th match { case arty100tth: Arty100TFPGATestHarnessImp => {
      arty100tth.arty100tOuter.io_spi_bb.bundle <> ports.head
    } }
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case arty100tth: Arty100TFPGATestHarnessImp => {
      require(ports.size == 1)

      val bundles = arty100tth.arty100tOuter.ddrClient.out.map(_._1)
      val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
      bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
      ddrClientBundle <> ports.head
    } }
  }
})
