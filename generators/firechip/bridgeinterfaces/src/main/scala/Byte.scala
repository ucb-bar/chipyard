package firechip.bridgeinterfaces

import chisel3._

class ByteIO extends Bundle {
  val out = Output(UInt(8.W))
}

class ByteBridgeTargetIO extends Bundle {
  val byte = Flipped(new ByteIO)
  val reset = Input(Bool())
  val clock = Input(Clock())
}

case class ByteKey() // intentionally empty