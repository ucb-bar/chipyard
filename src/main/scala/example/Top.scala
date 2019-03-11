package example

import chisel3._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._

class ExampleTop(implicit p: Parameters) extends ExampleRocketSystem //RocketSubsystem
    with CanHaveMasterAXI4MemPort
    with HasPeripheryBootROM
//  with HasSystemErrorSlave
//    with HasSyncExtInterrupts
    with HasNoDebug
    with HasPeripherySerial {
  override lazy val module = new ExampleTopModule(this)
}

class ExampleTopModule[+L <: ExampleTop](l: L) extends ExampleRocketSystemModuleImp(l) // RocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
//    with HasExtInterruptsModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with DontTouch

class ExampleTopWithPWMTL(implicit p: Parameters) extends ExampleTop
    with HasPeripheryPWMTL {
  override lazy val module = new ExampleTopWithPWMTLModule(this)
}

class ExampleTopWithPWMTLModule(l: ExampleTopWithPWMTL)
  extends ExampleTopModule(l) with HasPeripheryPWMTLModuleImp

class ExampleTopWithPWMAXI4(implicit p: Parameters) extends ExampleTop
    with HasPeripheryPWMAXI4 {
  override lazy val module = new ExampleTopWithPWMAXI4Module(this)
}

class ExampleTopWithPWMAXI4Module(l: ExampleTopWithPWMAXI4)
  extends ExampleTopModule(l) with HasPeripheryPWMAXI4ModuleImp

class ExampleTopWithBlockDevice(implicit p: Parameters) extends ExampleTop
    with HasPeripheryBlockDevice {
  override lazy val module = new ExampleTopWithBlockDeviceModule(this)
}

class ExampleTopWithBlockDeviceModule(l: ExampleTopWithBlockDevice)
  extends ExampleTopModule(l)
  with HasPeripheryBlockDeviceModuleImp
