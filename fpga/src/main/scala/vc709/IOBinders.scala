package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{IO, DataMirror}

import freechips.rocketchip.diplomacy.{ResourceBinding, Resource, ResourceAddress, InModuleBody}
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp}
import sifive.blocks.devices.spi.{HasPeripherySPI, HasPeripherySPIModuleImp, MMCDevice}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp}

import chipyard.{CanHaveMasterTLMemPort}
import chipyard.iobinders.{OverrideIOBinder, OverrideLazyIOBinder}

class WithI2CIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryI2CModuleImp) => {
    val io_i2c_pins_temp = system.i2c.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"i2c_$i") }
    (io_i2c_pins_temp zip system.i2c).map { case (io, sysio) =>
      io <> sysio
    }
    (io_i2c_pins_temp, Nil)
  }
})