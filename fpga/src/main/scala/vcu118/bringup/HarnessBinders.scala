package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{Analog, IO, BaseModule}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp, I2CPort}
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp, GPIOPortIO}

import testchipip.tsi.{HasPeripheryTSIHostWidget, TSIHostWidgetIO}

import chipyard.harness._
import chipyard.iobinders._

/*** UART ***/
class WithBringupUART extends HarnessBinder({
  case (th: BringupVCU118FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.bringupOuter.io_fmc_uart_bb.bundle <> port.io
  }
})

/*** I2C ***/
class WithBringupI2C extends HarnessBinder({
  case (th: BringupVCU118FPGATestHarnessImp, port: chipyard.iobinders.I2CPort, chipId: Int) => {
    th.bringupOuter.io_i2c_bb.bundle <> port.io
  }
})

/*** GPIO ***/
class WithBringupGPIO extends HarnessBinder({
  case (th: BringupVCU118FPGATestHarnessImp, port: GPIOPort, chipId: Int) => {
    th.bringupOuter.io_gpio_bb(port.pinId).bundle <> port.io
  }
})

/*** TSI Host Widget ***/
class WithBringupTSIHost extends HarnessBinder({
  case (th: BringupVCU118FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val tsiBundles = th.bringupOuter.tsiDdrClient.out.map(_._1)
    val tsiDdrClientBundle = Wire(new HeterogeneousBag(tsiBundles.map(_.cloneType)))
    tsiBundles.zip(tsiDdrClientBundle).foreach { case (bundle, io) => bundle <> io }
    tsiDdrClientBundle <> port.io
  }
  case (th: BringupVCU118FPGATestHarnessImp, port: TSIHostWidgetPort, chipId: Int) => {
    th.bringupOuter.io_tsi_serial_bb.bundle <> port.io
  }
})
