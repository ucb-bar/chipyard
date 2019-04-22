package beagle

import chisel3._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._
import example.{HasPeripheryPWMTL, HasPeripheryPWMAXI4, HasPeripheryPWMTLModuleImp, HasPeripheryPWMAXI4ModuleImp}

//---------------------------------------------------------------------------------------------------------

class BeagleRocketTop(implicit p: Parameters) extends ExampleRocketSystem
  with CanHaveMasterAXI4MemPort
  with HasPeripheryBootROM
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BeagleRocketTopModule(this)
}

class BeagleRocketTopModule[+L <: BeagleRocketTop](l: L) extends ExampleRocketSystemModuleImp(l)
  with HasRTCModuleImp
  with CanHaveMasterAXI4MemPortModuleImp
  with HasPeripheryBootROMModuleImp
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

class BeagleBoomTop(implicit p: Parameters) extends boom.system.ExampleBoomSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BeagleBoomTopModule(this)
}

class BeagleBoomTopModule[+L <: BeagleBoomTop](l: L) extends boom.system.ExampleBoomSystemModule(l)
  with HasRTCModuleImp
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch
