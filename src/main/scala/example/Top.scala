package example

import chisel3._
import config.Parameters
import testchipip._
import rocketchip._

class ExampleTop(implicit p: Parameters) extends BaseTop()(p)
    with PeripheryMasterAXI4Mem
    with PeripheryBootROM
    with PeripheryZero
    with PeripheryCounter
    with PeripheryDebug
    with HardwiredResetVector
    with RocketPlexMaster
    with PeripherySerial {
  override lazy val module = new ExampleTopModule(this, () => new ExampleTopBundle(this))
}

class ExampleTopBundle[+L <: ExampleTop](l: L) extends BaseTopBundle(l)
    with PeripheryMasterAXI4MemBundle
    with PeripheryBootROMBundle
    with PeripheryZeroBundle
    with PeripheryCounterBundle
    with PeripheryDebugBundle
    with HardwiredResetVectorBundle
    with RocketPlexMasterBundle
    with PeripherySerialBundle

class ExampleTopModule[+L <: ExampleTop, +B <: ExampleTopBundle[L]](l: L, b: () => B)
  extends BaseTopModule(l, b)
    with PeripheryMasterAXI4MemModule
    with PeripheryBootROMModule
    with PeripheryZeroModule
    with PeripheryCounterModule
    with PeripheryDebugModule
    with HardwiredResetVectorModule
    with RocketPlexMasterModule
    with PeripherySerialModule
