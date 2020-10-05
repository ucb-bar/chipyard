package chipyard.fpga.arty.e300

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._

import chipyard.{DigitalTop, DigitalTopModule}

// ------------------------------------
// E300 DigitalTop
// ------------------------------------

class E300DigitalTop(implicit p: Parameters) extends DigitalTop
{
  override lazy val module = new E300DigitalTopModule(this)
}

class E300DigitalTopModule[+L <: E300DigitalTop](l: L) extends DigitalTopModule(l)
