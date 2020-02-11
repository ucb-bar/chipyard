package firesim.firesim

import chisel3._
import freechips.rocketchip._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.config.Parameters
import utilities.{Subsystem, SubsystemModuleImp}
import testchipip._
import firesim.util.DefaultFireSimHarness
import sifive.blocks.devices.uart._

class FireSimCS152DUT(implicit p: Parameters) extends Subsystem
  with HasHierarchicalBusTopology
  with CanHaveMasterAXI4MemPort
  with HasPeripheryBootROM
  with CanHavePeripherySerial
{
  override lazy val module = new FireSimCS152ModuleImp(this)
}

class FireSimCS152ModuleImp[+L <: FireSimCS152DUT](l: L) extends SubsystemModuleImp(l)
  with HasRTCModuleImp
  with CanHaveMasterAXI4MemPortModuleImp
  with HasPeripheryBootROMModuleImp
  with CanHavePeripherySerialModuleImp

class FireSimCS152(implicit p: Parameters) extends DefaultFireSimHarness(() => new FireSimCS152DUT)
