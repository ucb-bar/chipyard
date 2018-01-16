package boomexample

import boom.system.{BoomCoreplex, BoomCoreplexModule}
import freechips.rocketchip.coreplex._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._

class BoomExampleTop(implicit p: Parameters) extends BoomCoreplex
    with HasMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasSystemErrorSlave
    with HasAsyncExtInterrupts
    with HasNoDebug
    with HasPeripherySerial {
  override lazy val module = new BoomExampleTopModule(this)
}

class BoomExampleTopModule[+L <: BoomExampleTop](l: L) extends BoomCoreplexModule(l)
    with HasRTCModuleImp
    with HasMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasExtInterruptsModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with DontTouch

class BoomExampleTopWithBlockDevice(implicit p: Parameters) extends BoomExampleTop
    with HasPeripheryBlockDevice {
  override lazy val module = new BoomExampleTopWithBlockDeviceModule(this)
}

class BoomExampleTopWithBlockDeviceModule(l: BoomExampleTopWithBlockDevice)
  extends BoomExampleTopModule(l)
  with HasPeripheryBlockDeviceModuleImp
