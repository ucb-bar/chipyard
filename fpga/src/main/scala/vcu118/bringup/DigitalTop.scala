package chipyard.fpga.vcu118.bringup

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import chipyard.{DigitalTop, DigitalTopModule}

// ------------------------------------
// Bringup VCU118 DigitalTop
// ------------------------------------

class BringupVCU118DigitalTop(implicit p: Parameters) extends DigitalTop
  with sifive.blocks.devices.i2c.HasPeripheryI2C
  with testchipip.HasPeripheryTSIHostWidget
{
  override lazy val module = new BringupVCU118DigitalTopModule(this)
}

class BringupVCU118DigitalTopModule[+L <: BringupVCU118DigitalTop](l: L) extends DigitalTopModule(l)
  with sifive.blocks.devices.i2c.HasPeripheryI2CModuleImp
