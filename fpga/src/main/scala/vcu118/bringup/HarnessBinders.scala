package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{Analog, IO, BaseModule}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp, I2CPort}
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp, GPIOPortIO}

import chipyard.{HasHarnessSignalReferences}
import chipyard.harness.{ComposeHarnessBinder, OverrideHarnessBinder}

/*** UART ***/
class WithBringupUART extends ComposeHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 2)

      vcu118th.bringupOuter.io_fmc_uart_bb.bundle <> ports.last
    } }
  }
})

/*** SPI ***/
class WithBringupSPI extends ComposeHarnessBinder({
  (system: HasPeripherySPI, th: BaseModule with HasHarnessSignalReferences, ports: Seq[SPIPortIO]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 2)

      vcu118th.bringupOuter.io_adi_spi_bb.bundle <> ports.last
    } }
  }
})

/*** I2C ***/
class WithBringupI2C extends OverrideHarnessBinder({
  (system: HasPeripheryI2CModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[I2CPort]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      require(ports.size == 1)

      vcu118th.bringupOuter.io_i2c_bb.bundle <> ports.head
    } }
  }
})

/*** GPIO ***/
class WithBringupGPIO extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[GPIOPortIO]) => {
    th match { case vcu118th: BringupVCU118FPGATestHarnessImp => {
      (vcu118th.bringupOuter.io_gpio_bb zip ports).map { case (bb_io, dut_io) =>
        bb_io.bundle <> dut_io
      }
    } }
  }
})
