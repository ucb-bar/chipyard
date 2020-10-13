package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO, DataMirror}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import chipyard.{BuildSystem}

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

trait HasVCU118PlatformIO {
  val io_uart: Seq[UARTPortIO]
  val io_spi: Seq[SPIPortIO]
  val io_i2c: Seq[I2CPort]
  val io_gpio: Seq[GPIOPortIO]
  val io_tl_mem: HeterogeneousBag[TLBundle]
}

class VCU118Platform(override implicit val p: Parameters) extends LazyModule with LazyScope with BindingScope {

  val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("system")

  // add MMC to the DTS
  lazySystem match { case lsys: HasPeripherySPI =>
    val mmcDev = new MMCDevice(lsys.tlspi.head.device, 1)
    ResourceBinding {
      Resource(mmcDev, "reg").bind(ResourceAddress(0))
    }
  }

  override lazy val module = new VCU118PlatformModule(this)
}

class VCU118PlatformModule[+L <: VCU118Platform](_outer: L) extends LazyModuleImp(_outer)
  with HasVCU118PlatformIO {

  val io_uart = _outer.lazySystem.module match { case sys: HasPeripheryUARTModuleImp =>
    val io_uart_pins_temp = p(PeripheryUARTKey).zipWithIndex.map { case (p, i) => IO(new UARTPortIO(p)).suggestName(s"uart_$i") }
    (io_uart_pins_temp zip sys.uart).map { case (io, sysio) =>
      io <> sysio
    }
    io_uart_pins_temp
  }

  val io_spi = _outer.lazySystem.module match { case sys: HasPeripherySPIModuleImp =>
    val io_spi_pins_temp = p(PeripherySPIKey).zipWithIndex.map { case (p, i) => IO(new SPIPortIO(p)).suggestName(s"spi_$i") }
    (io_spi_pins_temp zip sys.spi).map { case (io, sysio) =>
      io <> sysio
    }
    io_spi_pins_temp
  }

  val io_i2c = _outer.lazySystem.module match { case sys: HasPeripheryI2CModuleImp =>
    val io_i2c_pins_temp = p(PeripheryI2CKey).zipWithIndex.map { case (p, i) => IO(new I2CPort).suggestName(s"i2c_$i") }
    (io_i2c_pins_temp zip sys.i2c).map { case (io, sysio) =>
      io <> sysio
    }
    io_i2c_pins_temp
  }

  val io_gpio = _outer.lazySystem.module match { case sys: HasPeripheryGPIOModuleImp =>
    val io_gpio_pins_temp = p(PeripheryGPIOKey).zipWithIndex.map { case (p, i) => IO(new GPIOPortIO(p)).suggestName(s"gpio_$i") }
    (io_gpio_pins_temp zip sys.gpio).map { case (io, sysio) =>
      io <> sysio
    }
    io_gpio_pins_temp
  }

  val io_tl_mem = _outer.lazySystem match { case sys: chipyard.CanHaveMasterTLMemPort =>
    val io_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](sys.mem_tl)).suggestName("tl_slave")
    io_tl_mem_pins_temp <> sys.mem_tl
    io_tl_mem_pins_temp
  }
}
