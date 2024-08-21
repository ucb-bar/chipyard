// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._
import chisel3.util.{Decoupled}

/**
  * Class which parameterizes the TSIBridge
  *
  * memoryRegionNameOpt, if unset, indicates that firesim-fesvr should not attempt to write a payload into DRAM through the loadmem unit.
  * This is suitable for target designs which do not use the FASED DRAM model.
  * If a FASEDBridge for the backing AXI4 memory is present, then memoryRegionNameOpt should be set to the same memory region name which is passed
  * to the FASEDBridge. This enables fast payload loading in firesim-fesvr through the loadmem unit.
  */
case class TSIBridgeParams(memoryRegionNameOpt: Option[String])

class SerialIO(val w: Int) extends Bundle {
  val in = Flipped(Decoupled(UInt(w.W)))
  val out = Decoupled(UInt(w.W))
}

object TSI {
  val WIDTH = 32 // hardcoded in FESVR
}

class TSIIO extends SerialIO(TSI.WIDTH)

class TSIBridgeTargetIO extends Bundle {
  val tsi = Flipped(new TSIIO)
  val reset = Input(Bool())
  val clock = Input(Clock())
}
