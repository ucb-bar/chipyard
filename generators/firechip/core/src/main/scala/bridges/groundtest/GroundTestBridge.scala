// See LICENSE for license details

package firechip.core.bridges

import chisel3._

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces.compat._

class GroundTestBridge extends BlackBox
    with Bridge[HostPortIO[GroundTestBridgeTargetIO]] {
  val moduleName = "firechip.firesimonly.bridges.GroundTestBridgeModule"
  val io = IO(new GroundTestBridgeTargetIO)
  val bridgeIO = HostPort(io)
  val constructorArg = None
  generateAnnotations()
}

object GroundTestBridge {
  def apply(clock: Clock, success: Bool): GroundTestBridge = {
    val bridge = Module(new GroundTestBridge)
    bridge.io.success := success
    bridge.io.clock := clock
    bridge
  }
}
