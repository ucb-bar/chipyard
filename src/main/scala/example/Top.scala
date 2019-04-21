package example

import chisel3._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._

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

// ----------------------
// BOOM Top Level Systems
// ----------------------

class BoomTop(implicit p: Parameters) extends boom.system.BoomSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomTopModule(this)
}

class BoomTopModule[+L <: BoomTop](l: L) extends boom.system.BoomSystemModule(l)
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
