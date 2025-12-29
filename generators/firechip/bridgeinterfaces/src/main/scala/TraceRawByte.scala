package firechip.bridgeinterfaces

import chisel3._
import chisel3.util._

class TraceRawBytePortIO extends Bundle {
  val out = Decoupled(UInt(8.W))
}

class TraceRawByteBridgeTargetIO extends Bundle {
  val byte = Flipped(new TraceRawBytePortIO)
  val reset = Input(Bool())
  val clock = Input(Clock())
}

case class TraceRawByteKey() // intentionally empty