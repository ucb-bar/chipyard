// // See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._
import chisel3.util._

class DecoupledFlitIO(val flitWidth: Int) extends Bundle {
  val in = Flipped(Decoupled(UInt(flitWidth.W)))
  val out = Decoupled(UInt(flitWidth.W))
}

class CTCBridgeIO(val w: Int) extends Bundle {
  val client_flit = new DecoupledFlitIO(w) 
  val manager_flit = new DecoupledFlitIO(w) 
}

// NOTE: CTCBridgeIO in ctc is CTC.INNER_WIDTH=32b
object CTC {
  val WIDTH = 32
}

class CTCBridgeTargetIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val ctc_io = Flipped(new CTCBridgeIO(CTC.WIDTH))
}
