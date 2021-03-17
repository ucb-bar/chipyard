package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.diplomacy.{NodeHandlePair}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp, I2CPort}
import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.fpgashells.devices.xilinx.xilinxvc709pciex1.{HasSystemXilinxVC709PCIeX1ModuleImp, XilinxVC709PCIeX1IO}

import chipyard.{HasHarnessSignalReferences, CanHaveMasterTLMemPort}
import chipyard.harness.{OverrideHarnessBinder}

/*** I2C ***/
class WithI2C extends OverrideHarnessBinder({
  (system: HasPeripheryI2CModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[I2CPort]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      val io_i2c_bb_s = vc709th.vc709Outer.io_i2c_bb_s
      (io_i2c_bb_s zip ports).map { case (io_i2c_bb, port) => io_i2c_bb.bundle <> port }
    } }
  }
})

/*** UART ***/
class WithUART extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
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
      // connect each ddrClient to port
      (ports zip vc709th.vc709Outer.ddrClients).map { case (port, ddrClient) =>
        val bundles = ddrClient.out.map(_._1)
        val wire = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
        (bundles zip wire) foreach { case (bundle, io) => bundle <> io }
        wire <> port
      }
    } }
  }
})