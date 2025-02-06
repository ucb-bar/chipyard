// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._

case class SpikeCosimConfig(
  isa: String,
  priv: String,
  pmpregions: Int,
  maxpglevels: Int,
  mem0_base: BigInt,
  mem0_size: BigInt,
  nharts: Int,
  bootrom: String,
  has_dtm: Boolean,
  mem1_base: BigInt = 0,
  mem1_size: BigInt = 0,
  mem2_base: BigInt = 0,
  mem2_size: BigInt = 0
)

case class CospikeBridgeParams(
  widths: TraceBundleWidths,
  hartid: Int,
  cfg:    SpikeCosimConfig,
)

class CospikeBridgeTargetIO(widths: TraceBundleWidths) extends Bundle {
  val tiletrace = Input(new TileTraceIO(widths))
}
