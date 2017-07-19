package tlserdes

import chisel3._
import chisel3.util._
import freechips.rocketchip.chip._
import freechips.rocketchip.config.Parameters
import testchipip._

class SerdesTop(implicit p: Parameters) extends BaseSystem
    with HasPeripheryTLSerdesMemPort
    with HasPeripheryErrorSlave
    with HasPeripheryZeroSlave
    with HasPeripheryBootROM
    with HasPeripheryRTCCounter
    with HasRocketPlexMaster
    with HasNoDebug
    with HasPeripherySerial {
  override lazy val module = new SerdesTopModule(this)
}

class SerdesTopModule(outer: SerdesTop) extends BaseSystemModule(outer)
    with HasPeripheryTLSerdesMemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasPeripheryRTCCounterModuleImp
    with HasRocketPlexMasterModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
