package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters}

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import chipyard.fpga.vcu118.bringup._

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
  require(p(PeripherySPIKey).size == 2)

  // 1st SPI goes to the VCU118 SDIO port

  val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(p(PeripherySPIKey).head)))
  val sdio_placed = designParameters(SPIOverlayKey).head.place(SPIDesignInput(p(PeripherySPIKey).head, io_spi_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_spi_bb.bundle <> dutMod.io_spi.head
    }
  }

  // TODO: No access to the TLSPI node...
  //val mmcDev = new MMCDevice(sdio_placed.device, 1)
  //ResourceBinding {
  //  Resource(mmcDev, "reg").bind(ResourceAddress(0))
  //}

  // 2nd SPI goes to the ADI port

  val adi = Overlay(SPIOverlayKey, new chipyard.fpga.vcu118.bringup.BringupSPIVCU118ShellPlacer(this, SPIShellInput()))

  val io_spi_bb_2 = BundleBridgeSource(() => (new SPIPortIO(p(PeripherySPIKey).last)))
  val adi_placed = designParameters(SPIOverlayKey).last.place(SPIDesignInput(p(PeripherySPIKey).last, io_spi_bb_2))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_spi_bb_2.bundle <> dutMod.io_spi.last
    }
  }

  // TODO: No access to the TLSPI node...
  //val adiDev = new chipyard.fpga.vcu118.bringup.ADISPIDevice(adi_placed.device, 1)
  //ResourceBinding {
  //  Resource(adiDev, "reg").bind(ResourceAddress(0))
  //}

  /*** I2C ***/
  require(p(PeripheryI2CKey).size == 1)

  val i2c = Overlay(I2COverlayKey, new chipyard.fpga.vcu118.bringup.BringupI2CVCU118ShellPlacer(this, I2CShellInput()))

  val io_i2c_bb = BundleBridgeSource(() => (new I2CPort))
  designParameters(I2COverlayKey).head.place(I2CDesignInput(io_i2c_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_i2c_bb.bundle <> dutMod.io_i2c.head
    }
  }

  /*** GPIO ***/
  val gpio = Seq.tabulate(p(PeripheryGPIOKey).size)(i => {
    val maxGPIOSupport = 32
    val names = BringupGPIOs.names.slice(maxGPIOSupport*i, maxGPIOSupport*(i+1))
    Overlay(GPIOOverlayKey, new chipyard.fpga.vcu118.bringup.BringupGPIOVCU118ShellPlacer(this, GPIOShellInput(), names))
  })

  val io_gpio_bb = p(PeripheryGPIOKey).map { p => BundleBridgeSource(() => (new GPIOPortIO(p))) }
  (designParameters(GPIOOverlayKey) zip p(PeripheryGPIOKey)).zipWithIndex.map { case ((placer, params), i) =>
    placer.place(GPIODesignInput(params, io_gpio_bb(i)))
  }
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      (io_gpio_bb zip dutMod.io_gpio).map { case (bb_io, dut_io) =>
        bb_io.bundle <> dut_io
      }
    }
  }
}

