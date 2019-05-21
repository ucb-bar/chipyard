package example

import chisel3._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._
import sifive.blocks.devices.gpio._

// ------------------------
// Rocket Top Level Systems
// ------------------------

class RocketTop(implicit p: Parameters) extends ExampleRocketSystem
    with CanHaveMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial {
  override lazy val module = new RocketTopModule(this)
}

class RocketTopModule[+L <: RocketTop](l: L) extends ExampleRocketSystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with DontTouch

//---------------------------------------------------------------------------------------------------------

class RocketTopWithPWMTL(implicit p: Parameters) extends RocketTop
    with HasPeripheryPWMTL {
  override lazy val module = new RocketTopWithPWMTLModule(this)
}

class RocketTopWithPWMTLModule(l: RocketTopWithPWMTL)
  extends RocketTopModule(l) with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

class RocketTopWithPWMAXI4(implicit p: Parameters) extends RocketTop
    with HasPeripheryPWMAXI4 {
  override lazy val module = new RocketTopWithPWMAXI4Module(this)
}

class RocketTopWithPWMAXI4Module(l: RocketTopWithPWMAXI4)
  extends RocketTopModule(l) with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

class RocketTopWithBlockDevice(implicit p: Parameters) extends RocketTop
    with HasPeripheryBlockDevice {
  override lazy val module = new RocketTopWithBlockDeviceModule(this)
}

class RocketTopWithBlockDeviceModule(l: RocketTopWithBlockDevice)
  extends RocketTopModule(l)
  with HasPeripheryBlockDeviceModuleImp

//---------------------------------------------------------------------------------------------------------

class RocketTopWithGPIO(implicit p: Parameters) extends RocketTop
    with HasPeripheryGPIO {
  override lazy val module = new RocketTopWithGPIOModule(this)
}

class RocketTopWithGPIOModule(l: RocketTopWithGPIO)
  extends RocketTopModule(l)
  with HasPeripheryGPIOModuleImp

// ----------------------
// BOOM Top Level Systems
// ----------------------

class BoomTop(implicit p: Parameters) extends boom.system.ExampleBoomSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomTopModule(this)
}

class BoomTopModule[+L <: BoomTop](l: L) extends boom.system.ExampleBoomSystemModule(l)
  with HasRTCModuleImp
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

class BoomTopWithPWMTL(implicit p: Parameters) extends BoomTop
  with HasPeripheryPWMTL {
  override lazy val module = new BoomTopWithPWMTLModule(this)
}

class BoomTopWithPWMTLModule(l: BoomTopWithPWMTL) extends BoomTopModule(l)
  with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomTopWithPWMAXI4(implicit p: Parameters) extends BoomTop
  with HasPeripheryPWMAXI4 {
  override lazy val module = new BoomTopWithPWMAXI4Module(this)
}

class BoomTopWithPWMAXI4Module(l: BoomTopWithPWMAXI4) extends BoomTopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomTopWithBlockDevice(implicit p: Parameters) extends BoomTop
  with HasPeripheryBlockDevice {
  override lazy val module = new BoomTopWithBlockDeviceModule(this)
}

class BoomTopWithBlockDeviceModule(l: BoomTopWithBlockDevice) extends BoomTopModule(l)
  with HasPeripheryBlockDeviceModuleImp

//---------------------------------------------------------------------------------------------------------

class BoomTopWithGPIO(implicit p: Parameters) extends BoomTop
    with HasPeripheryGPIO {
  override lazy val module = new BoomTopWithGPIOModule(this)
}

class BoomTopWithGPIOModule(l: BoomTopWithGPIO)
  extends BoomTopModule(l)
  with HasPeripheryGPIOModuleImp

// -------------------------------
// BOOM + Rocket Top Level Systems
// -------------------------------

class BoomAndRocketTop(implicit p: Parameters) extends ExampleBoomAndRocketSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomAndRocketTopModule(this)
}

class BoomAndRocketTopModule[+L <: BoomAndRocketTop](l: L) extends ExampleBoomAndRocketSystemModule(l)
  with HasRTCModuleImp
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
