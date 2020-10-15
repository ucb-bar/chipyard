package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem.{ExtMem, BaseSubsystem}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import chipyard.fpga.vcu118.bringup.{BringupGPIOs, BringupUARTVCU118ShellPlacer, BringupSPIVCU118ShellPlacer, BringupI2CVCU118ShellPlacer, BringupGPIOVCU118ShellPlacer}
import chipyard.{CanHaveMasterTLMemPort, HasHarnessSignalReferences}
import chipyard.harness._

/*** UART ***/
class WithBringupUART extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 2)

      vcu118th.outer.io_uart_bb.bundle <> ports.head
      vcu118th.outer.io_uart_bb_2.bundle <> ports.last
    } }

    Nil
  }
})

/*** SPI ***/
class WithBringupSPI extends OverrideHarnessBinder({
  (system: HasPeripherySPIModuleImp, th: HasHarnessSignalReferences, ports: Seq[SPIPortIO]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 2)

      vcu118th.outer.io_spi_bb.bundle <> ports.head
      vcu118th.outer.io_spi_bb_2.bundle <> ports.last
    } }

    Nil
  }
})

/*** I2C ***/
class WithBringupI2C extends OverrideHarnessBinder({
  (system: HasPeripheryI2CModuleImp, th: HasHarnessSignalReferences, ports: Seq[I2CPort]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 1)

      vcu118th.outer.io_i2c_bb.bundle <> ports.head
    } }

    Nil
  }
})

/*** GPIO ***/
class WithBringupGPIO extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[GPIOPortIO]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      (vcu118th.outer.io_gpio_bb zip ports).map { case (bb_io, dut_io) =>
        bb_io.bundle <> dut_io
      }
    } }

    Nil
  }
})

/*** Experimental DDR ***/
class WithBringupDDR extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 1)

      val bundles = vcu118th.outer.ddrClient.out.map(_._1)
      val ddrClientBundle = Wire(new freechips.rocketchip.util.HeterogeneousBag(bundles.map(_.cloneType)))
      bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
      ddrClientBundle <> ports.head
    } }

    Nil
  }
})
