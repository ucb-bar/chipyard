package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.diplomacy.{NodeHandlePair}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}

import chipyard.{HasHarnessSignalReferences, CanHaveMasterTLMemPort}
import chipyard.harness.{OverrideHarnessBinder}

/*** UART ***/
class WithUART extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      // println("WithUART:ports.size = " + ports.size)
      val io_uart_bb_s = vc709th.vc709Outer.io_uart_bb_s
      (io_uart_bb_s zip ports).map { case (io_uart_bb, port) => io_uart_bb.bundle <> port }
    } }
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      require(ports.size > 0, "There must be at least one port.") // all ports go to the TL mem

      (vc709th.vc709Outer.ddrClients zip ports).map({ case (ddrClient, port) => 
        val bundles = ddrClient.out.map(_._1)
        val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
        bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
        ddrClientBundle <> port
      })
    } }
  }
})
