package chipyard.fpga.vcu118.bringup

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import chipyard.fpga.vcu118.{VCU118DigitalTop, VCU118DigitalTopModule}

// ------------------------------------
// BringupVCU118 DigitalTop
// ------------------------------------

class BringupVCU118DigitalTop(implicit p: Parameters) extends VCU118DigitalTop
  with sifive.blocks.devices.i2c.HasPeripheryI2C
{
  override lazy val module = new BringupVCU118DigitalTopModule(this)
}

class BringupVCU118DigitalTopModule[+L <: BringupVCU118DigitalTop](l: L) extends VCU118DigitalTopModule(l)
  with sifive.blocks.devices.i2c.HasPeripheryI2CModuleImp
