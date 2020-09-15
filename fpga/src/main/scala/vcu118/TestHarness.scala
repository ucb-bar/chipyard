package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem.{ExtMem, BaseSubsystem}

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import chipyard.fpga.vcu118.bringup.{BringupGPIOs, BringupUARTVCU118ShellPlacer, BringupSPIVCU118ShellPlacer, BringupI2CVCU118ShellPlacer, BringupGPIOVCU118ShellPlacer}

case object DUTFrequencyKey extends Field[Double](100.0)

class VCU118FPGATestHarness(override implicit val p: Parameters) extends ChipyardVCU118Shell {

  def dp = designParameters

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  sys_clock.get() match {
    case Some(x : SysClockVCU118PlacedOverlay) => {
      harnessSysPLL := x.node
    }
  }

  // create and connect to the dutClock
  val dutClock = ClockSinkNode(freqMHz = dp(DUTFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  InModuleBody {
    topDesign.module match { case td: LazyModuleImp => {
        td.clock := dutClock.in.head._1.clock
        td.reset := dutClock.in.head._1.reset
      }
    }
  }

  // connect ref clock to dummy sink node
  ref_clock.get() match {
    case Some(x : RefClockVCU118PlacedOverlay) => {
      val sink = ClockSinkNode(Seq(ClockSinkParameters()))
      sink := x.node
    }
  }

  /*** UART ***/
  require(dp(PeripheryUARTKey).size == 2)

  // 1st UART goes to the VCU118 dedicated UART

  // BundleBridgeSource is a was for Diplomacy to connect something from very deep in the design
  // to somewhere much, much higher. For ex. tunneling trace from the tile to the very top level.
  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_uart_bb.bundle <> dutMod.io_uart.head
    }
  }

  // 2nd UART goes to the FMC UART

  val uart_fmc = Overlay(UARTOverlayKey, new BringupUARTVCU118ShellPlacer(this, UARTShellInput()))

  val io_uart_bb_2 = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).last)))
  dp(UARTOverlayKey).last.place(UARTDesignInput(io_uart_bb_2))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_uart_bb_2.bundle <> dutMod.io_uart.last
    }
  }

  /*** SPI ***/
  require(dp(PeripherySPIKey).size == 2)

  // 1st SPI goes to the VCU118 SDIO port

  val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).head)))
  val sdio_placed = dp(SPIOverlayKey).head.place(SPIDesignInput(dp(PeripherySPIKey).head, io_spi_bb))
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

  val adi = Overlay(SPIOverlayKey, new BringupSPIVCU118ShellPlacer(this, SPIShellInput()))

  val io_spi_bb_2 = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).last)))
  val adi_placed = dp(SPIOverlayKey).last.place(SPIDesignInput(dp(PeripherySPIKey).last, io_spi_bb_2))
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
  require(dp(PeripheryI2CKey).size == 1)

  val i2c = Overlay(I2COverlayKey, new BringupI2CVCU118ShellPlacer(this, I2CShellInput()))

  val io_i2c_bb = BundleBridgeSource(() => (new I2CPort))
  dp(I2COverlayKey).head.place(I2CDesignInput(io_i2c_bb))
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      io_i2c_bb.bundle <> dutMod.io_i2c.head
    }
  }

  /*** GPIO ***/
  val gpio = Seq.tabulate(dp(PeripheryGPIOKey).size)(i => {
    val maxGPIOSupport = 32
    val names = BringupGPIOs.names.slice(maxGPIOSupport*i, maxGPIOSupport*(i+1))
    Overlay(GPIOOverlayKey, new BringupGPIOVCU118ShellPlacer(this, GPIOShellInput(), names))
  })

  val io_gpio_bb = dp(PeripheryGPIOKey).map { p => BundleBridgeSource(() => (new GPIOPortIO(p))) }
  (dp(GPIOOverlayKey) zip dp(PeripheryGPIOKey)).zipWithIndex.map { case ((placer, params), i) =>
    placer.place(GPIODesignInput(params, io_gpio_bb(i)))
  }
  InModuleBody {
    topDesign.module match { case dutMod: HasVCU118PlatformIO =>
      (io_gpio_bb zip dutMod.io_gpio).map { case (bb_io, dut_io) =>
        bb_io.bundle <> dut_io
      }
    }
  }

  /*** Experimental DDR ***/

  //val ddrPlaced = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtMem).get.master.base, dutWrangler.node, harnessSysPLL))

  //topDesign match { case lazyDut: VCU118Platform =>
  //  lazyDut.lazySystem match { case lazyDutWBus: BaseSubsystem =>
  //    lazyDutWBus {
  //      InModuleBody {
  //        ddrPlaced.overlayOutput.ddr := lazyDutWBus.mbus.toDRAMController(Some("xilinxvcu118mig"))()
  //      }
  //    }
  //  }
  //}
}

