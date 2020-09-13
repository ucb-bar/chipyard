package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp}
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy.{InModuleBody, BundleBridgeSource}

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._

class VCU118FPGATestHarness(override implicit val p: Parameters) extends VCU118Shell {


  /*** UART ***/
  require(p(PeripheryUARTKey).size == 2)

  // 1st UART goes to the VCU118 dedicated UART

  // BundleBridgeSource is a was for Diplomacy to connect something from very deep in the design
  // to somewhere much, much higher. For ex. tunneling trace from the tile to the very top level.
  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(p(PeripheryUARTKey).head)))
  designParameters(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_uart_bb.bundle <> dutMod.io_uart.head
    }
  }

  // 2nd UART goes to the FMC UART

  val uart_fmc = Overlay(UARTOverlayKey, new chipyard.fpga.vcu118.bringup.BringupUARTVCU118ShellPlacer(this, UARTShellInput()))

  val io_uart_bb_2 = BundleBridgeSource(() => (new UARTPortIO(p(PeripheryUARTKey).last)))
  designParameters(UARTOverlayKey).last.place(UARTDesignInput(io_uart_bb_2))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_uart_bb_2.bundle <> dutMod.io_uart.last
    }
  }

  /*** SPI ***/
  require(p(PeripherySPIKey).size >= 1)

  val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(p(PeripherySPIKey).head)))
  designParameters(SPIOverlayKey).head.place(SPIDesignInput(p(PeripherySPIKey).head, io_spi_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_spi_bb.bundle <> dutMod.io_spi.head
    }
  }

  /*** I2C ***/
  require(p(PeripheryI2CKey).size >= 1)

  val i2c = Overlay(I2COverlayKey, new chipyard.fpga.vcu118.bringup.BringupI2CVCU118ShellPlacer(this, I2CShellInput()))

  val io_i2c_bb = BundleBridgeSource(() => (new I2CPort))
  designParameters(I2COverlayKey).head.place(I2CDesignInput(io_i2c_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_i2c_bb.bundle <> dutMod.io_i2c.head
    }
  }
}

