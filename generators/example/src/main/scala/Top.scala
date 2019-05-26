package example

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch

import testchipip._

import sifive.blocks.devices.gpio._

// -------------------------------
// BOOM + Rocket Top Level Systems
// -------------------------------

class BoomAndRocketTop(implicit p: Parameters) extends boom.system.ExampleBoomAndRocketSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomAndRocketTopModule(this)
}

class BoomAndRocketTopModule[+L <: BoomAndRocketTop](l: L) extends boom.system.ExampleBoomAndRocketSystemModule(l)
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

class BoomAndRocketTopWithPWMTL(implicit p: Parameters) extends BoomAndRocketTop
  with HasPeripheryPWMTL {
  override lazy val module = new BoomAndRocketTopWithPWMTLModule(this)
}

class BoomAndRocketTopWithPWMTLModule(l: BoomAndRocketTopWithPWMTL) extends BoomAndRocketTopModule(l)
  with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomAndRocketTopWithPWMAXI4(implicit p: Parameters) extends BoomAndRocketTop
  with HasPeripheryPWMAXI4 {
  override lazy val module = new BoomAndRocketTopWithPWMAXI4Module(this)
}

class BoomAndRocketTopWithPWMAXI4Module(l: BoomAndRocketTopWithPWMAXI4) extends BoomAndRocketTopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomAndRocketTopWithBlockDevice(implicit p: Parameters) extends BoomAndRocketTop
  with HasPeripheryBlockDevice {
  override lazy val module = new BoomAndRocketTopWithBlockDeviceModule(this)
}

class BoomAndRocketTopWithBlockDeviceModule(l: BoomAndRocketTopWithBlockDevice) extends BoomAndRocketTopModule(l)
  with HasPeripheryBlockDeviceModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomAndRocketTopWithGPIO(implicit p: Parameters) extends BoomAndRocketTop
    with HasPeripheryGPIO {
  override lazy val module = new BoomAndRocketTopWithGPIOModule(this)
}

class BoomAndRocketTopWithGPIOModule(l: BoomAndRocketTopWithGPIO)
  extends BoomAndRocketTopModule(l)
  with HasPeripheryGPIOModuleImp
