package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{IO, DataMirror}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp}

import testchipip.{HasPeripheryTSIHostWidget, TSIHostWidgetIO}

import chipyard.iobinders.{OverrideIOBinder}

class WithGPIOIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    val io_gpio_pins_temp = system.gpio.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"gpio_$i") }
    (io_gpio_pins_temp zip system.gpio).map { case (io, sysio) =>
      io <> sysio
    }
    (io_gpio_pins_temp, Nil)
  }
})

class WithI2CIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryI2CModuleImp) => {
    val io_i2c_pins_temp = system.i2c.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"i2c_$i") }
    (io_i2c_pins_temp zip system.i2c).map { case (io, sysio) =>
      io <> sysio
    }
    (io_i2c_pins_temp, Nil)
  }
})

class WithTSITLIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryTSIHostWidget) => {
    require(system.tsiTLMem.size == 1)
    val io_tsi_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.tsiTLMem.head)).suggestName("tsi_tl_slave")
    io_tsi_tl_mem_pins_temp <> system.tsiTLMem.head

    require(system.tsiSerial.size == 1)
    val io_tsi_serial_pins_temp = IO(DataMirror.internal.chiselTypeClone[TSIHostWidgetIO](system.tsiSerial.head)).suggestName("tsi_serial")
    io_tsi_serial_pins_temp <> system.tsiSerial.head
    (Seq(io_tsi_tl_mem_pins_temp, io_tsi_serial_pins_temp), Nil)
  }
})
