package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{IO, DataMirror}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp}

import testchipip.{HasPeripheryTSIHostWidget, TSIHostWidgetIO}

import chipyard.iobinders.{OverrideIOBinder, Port, TLMemPort}

case class TSIHostWidgetPort(val io: TSIHostWidgetIO)
  extends Port[TSIHostWidgetIO]

class WithTSITLIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryTSIHostWidget) => {
    require(system.tsiTLMem.size == 1)
    val io_tsi_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.tsiTLMem.head)).suggestName("tsi_tl_slave")
    io_tsi_tl_mem_pins_temp <> system.tsiTLMem.head

    require(system.tsiSerial.size == 1)
    val io_tsi_serial_pins_temp = IO(DataMirror.internal.chiselTypeClone[TSIHostWidgetIO](system.tsiSerial.head)).suggestName("tsi_serial")
    io_tsi_serial_pins_temp <> system.tsiSerial.head
    (Seq(TLMemPort(io_tsi_tl_mem_pins_temp), TSIHostWidgetPort(io_tsi_serial_pins_temp)), Nil)
  }
})
