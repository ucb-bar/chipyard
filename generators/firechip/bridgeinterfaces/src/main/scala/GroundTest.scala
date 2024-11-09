// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._

class GroundTestBridgeTargetIO extends Bundle {
  val success = Input(Bool())
  val clock = Input(Clock())
}
