// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._
import chisel3.util.{DecoupledIO}

/** Class which parameterizes the DMIBridge
  *
  * memoryRegionNameOpt, if unset, indicates that firesim-fesvr should not attempt to write a payload into DRAM through
  * the loadmem unit. This is suitable for target designs which do not use the FASED DRAM model. If a FASEDBridge for
  * the backing AXI4 memory is present, then memoryRegionNameOpt should be set to the same memory region name which is
  * passed to the FASEDBridge. This enables fast payload loading in firesim-fesvr through the loadmem unit.
  */
case class DMIBridgeParams(memoryRegionNameOpt: Option[String], addrBits: Int)

//import freechips.rocketchip.devices.debug.{ClockedDMIIO, DMIReq, DMIResp}
object DMIConsts{
  def dmiDataSize = 32
  def dmiOpSize = 2
  def dmiRespSize = 2
}

class DMIReq(addrBits: Int) extends Bundle {
  val addr = UInt(addrBits.W)
  val data = UInt(DMIConsts.dmiDataSize.W)
  val op = UInt(DMIConsts.dmiOpSize.W)
}

class DMIResp extends Bundle {
  val data = UInt(DMIConsts.dmiDataSize.W)
  val resp = UInt(DMIConsts.dmiRespSize.W)
}

class DMIIO(addrBits: Int) extends Bundle {
  val req  = new DecoupledIO(new DMIReq(addrBits))
  val resp = Flipped(new DecoupledIO(new DMIResp))
}

class DMIBridgeTargetIO(addrBits: Int) extends Bundle {
  val debug = new DMIIO(addrBits)
  val reset = Input(Bool())
  val clock = Input(Clock())
}
