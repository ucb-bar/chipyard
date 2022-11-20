package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{Analog, IO, BaseModule}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp, I2CPort}
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp, GPIOPortIO}

import testchipip.{HasPeripheryTSIHostWidget, TSIHostWidgetIO}

import chipyard.{HasHarnessSignalReferences}
import chipyard.harness.{ComposeHarnessBinder, OverrideHarnessBinder}

/*** UART ***/
class WithBringupUART extends ComposeHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BringupVCU118FPGATestHarness, ports: Seq[UARTPortIO]) => {
    require(ports.size == 2)

    th.io_fmc_uart_bb.bundle <> ports.last
  }
})

/*** I2C ***/
class WithBringupI2C extends OverrideHarnessBinder({
  (system: HasPeripheryI2CModuleImp, th: BringupVCU118FPGATestHarness, ports: Seq[I2CPort]) => {
    require(ports.size == 1)

    th.io_i2c_bb.bundle <> ports.head
  }
})

/*** GPIO ***/
class WithBringupGPIO extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: BringupVCU118FPGATestHarness, ports: Seq[GPIOPortIO]) => {
    (th.io_gpio_bb zip ports).map { case (bb_io, dut_io) =>
      bb_io.bundle <> dut_io
    }
  }
})

/*** TSI Host Widget ***/
class WithBringupTSIHost extends OverrideHarnessBinder({
  (system: HasPeripheryTSIHostWidget, th: BringupVCU118FPGATestHarness, ports: Seq[Data]) => {
    require(ports.size == 2) // 1st goes to the TL mem, 2nd goes to the serial link

    ports.head match { case tlPort: HeterogeneousBag[TLBundle] =>
      val tsiBundles = th.tsiDdrClient.out.map(_._1)
      val tsiDdrClientBundle = Wire(new HeterogeneousBag(tsiBundles.map(_.cloneType)))
      tsiBundles.zip(tsiDdrClientBundle).foreach { case (bundle, io) => bundle <> io }
      tsiDdrClientBundle <> tlPort
    }

    ports.last match { case serialPort: TSIHostWidgetIO =>
      th.io_tsi_serial_bb.bundle <> serialPort
    }
  }
})
