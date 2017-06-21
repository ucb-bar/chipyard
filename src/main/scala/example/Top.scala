package example

import chisel3._
import config.Parameters
import testchipip._
import rocketchip._

class ExampleTop(implicit p: Parameters) extends BaseSystem
    with HasPeripheryMasterAXI4MemPort
    with HasPeripheryErrorSlave
    with HasPeripheryZeroSlave
    with HasPeripheryBootROM
    with HasPeripheryRTCCounter
    with HasRocketPlexMaster
    with HasNoDebug
    with HasPeripherySerial {
  override lazy val module = new ExampleTopModule(this)
}

class ExampleTopModule[+L <: ExampleTop](l: L) extends BaseSystemModule(l)
    with HasPeripheryMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasPeripheryRTCCounterModuleImp
    with HasRocketPlexMasterModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
