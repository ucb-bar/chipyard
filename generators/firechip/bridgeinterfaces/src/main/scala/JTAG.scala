// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._

// TCK is a Bool (not a Clock) so that HostPort can channelize it as a regular
// data signal. The bridge stub casts it to Clock when connecting to the
// target's JTAGChipIO.
class JTAGBridgeTargetIO extends Bundle {
  val jtag = new JTAGBridgePortIO
  val clock = Input(Clock())
  val reset = Input(Bool())
}

class JTAGBridgePortIO extends Bundle {
  val TCK = Output(Bool())
  val TMS = Output(Bool())
  val TDI = Output(Bool())
  val TDO = Input(Bool())
}

case class JTAGBridgeParams()
