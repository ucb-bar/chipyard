// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._

class TracerVBridgeTargetIO(widths: TraceBundleWidths) extends Bundle {
  val tiletrace = Input(new TileTraceIO(widths))
  val triggerCredit = Output(Bool())
  val triggerDebit  = Output(Bool())
}
