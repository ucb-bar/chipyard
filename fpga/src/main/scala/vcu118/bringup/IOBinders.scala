package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.util.experimental.{BoringUtils}
import chisel3.experimental.{Analog, IO, DataMirror}

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike, ResourceBinding, Resource, ResourceAddress}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.util._
import freechips.rocketchip.groundtest.{GroundTestSubsystemModuleImp, GroundTestSubsystem}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import tracegen.{TraceGenSystemModuleImp}

import barstools.iocell.chisel._

import testchipip._
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import chipyard.{GlobalResetSchemeKey, CanHaveMasterTLMemPort}
import chipyard.iobinders.{OverrideIOBinder}

class WithUARTIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val io_uart_pins_temp = system.uart.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"uart_$i") }
    (io_uart_pins_temp zip system.uart).map { case (io, sysio) =>
      io <> sysio
    }
    (io_uart_pins_temp, Nil)
  }
})

class WithGPIOIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    val io_gpio_pins_temp = system.gpio.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"gpio_$i") }
    (io_gpio_pins_temp zip system.gpio).map { case (io, sysio) =>
      io <> sysio
    }
    (io_gpio_pins_temp, Nil)
  }
})

class WithSPIIOPassthrough extends OverrideIOBinder({
  (system: HasPeripherySPIModuleImp) => {
    val io_spi_pins_temp = system.spi.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"spi_$i") }
    (io_spi_pins_temp zip system.spi).map { case (io, sysio) =>
      io <> sysio
    }
    (io_spi_pins_temp, Nil)
  }
})

//class WithMMCSPIDTS extends OverrideIOBinder({
//  (system: HasPeripherySPI) => {
//
//    val mmcDev = new MMCDevice(system.tlspi.head.device, 1)
//    ResourceBinding {
//      Resource(mmcDev, "reg").bind(ResourceAddress(0))
//    }
//
//    (Nil, Nil)
//  }
//})

class WithI2CIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryI2CModuleImp) => {
    val io_i2c_pins_temp = system.i2c.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"i2c_$i") }
    (io_i2c_pins_temp zip system.i2c).map { case (io, sysio) =>
      io <> sysio
    }
    (io_i2c_pins_temp, Nil)
  }
})

class WithTLIOPassthrough extends OverrideIOBinder({
  (system: CanHaveMasterTLMemPort) => {
    val io_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.mem_tl)).suggestName("tl_slave")
    io_tl_mem_pins_temp <> system.mem_tl
    (Seq(io_tl_mem_pins_temp), Nil)
  }
})
