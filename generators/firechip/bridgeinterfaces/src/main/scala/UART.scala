// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._

// Note: This file is heavily commented as it serves as a bridge walkthrough
// example in the FireSim docs

// Note: All code in this file must be isolated from target-side generators/classes/etc
// since this is also injected into the midas compiler.

// DOC include start: UART Bridge Target-Side Interface
class UARTPortIO extends Bundle {
  val txd = Output(Bool())
  val rxd = Input(Bool())
}

class UARTBridgeTargetIO extends Bundle {
  val clock = Input(Clock())
  val uart = Flipped(new UARTPortIO)
  // Note this reset is optional and used only to reset target-state modeled
  // in the bridge. This reset is just like any other Bool included in your target
  // interface, simply appears as another Bool in the input token.
  val reset = Input(Bool())
}
// DOC include end: UART Bridge Target-Side Interface

// DOC include start: UART Bridge Constructor Arg
// Out bridge module constructor argument. This captures all of the extra
// metadata we'd like to pass to the host-side BridgeModule. Note, we need to
// use a single case class to do so, even if it is simply to wrap a primitive
// type, as is the case for the div Int.
case class UARTKey(div: Int)
// DOC include end: UART Bridge Constructor Arg
