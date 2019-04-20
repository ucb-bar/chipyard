package boomexample

import chisel3._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._
import example.{HasPeripheryPWMTL, HasPeripheryPWMAXI4, HasPeripheryPWMTLModuleImp, HasPeripheryPWMAXI4ModuleImp}

//---------------------------------------------------------------------------------------------------------

class BoomExampleTop(implicit p: Parameters) extends boom.system.ExampleBoomSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomExampleTopModule(this)
}

class BoomExampleTopModule[+L <: BoomExampleTop](l: L) extends boom.system.ExampleBoomSystemModule(l)
  with HasRTCModuleImp
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

class BoomExampleTopWithPWMTL(implicit p: Parameters) extends BoomExampleTop
  with HasPeripheryPWMTL {
  override lazy val module = new BoomExampleTopWithPWMTLModule(this)
}

class BoomExampleTopWithPWMTLModule(l: BoomExampleTopWithPWMTL) extends BoomExampleTopModule(l)
  with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomExampleTopWithPWMAXI4(implicit p: Parameters) extends BoomExampleTop
  with HasPeripheryPWMAXI4 {
  override lazy val module = new BoomExampleTopWithPWMAXI4Module(this)
}

class BoomExampleTopWithPWMAXI4Module(l: BoomExampleTopWithPWMAXI4) extends BoomExampleTopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomExampleTopWithBlockDevice(implicit p: Parameters) extends BoomExampleTop
  with HasPeripheryBlockDevice {
  override lazy val module = new BoomExampleTopWithBlockDeviceModule(this)
}

class BoomExampleTopWithBlockDeviceModule(l: BoomExampleTopWithBlockDevice) extends BoomExampleTopModule(l)
  with HasPeripheryBlockDeviceModuleImp
