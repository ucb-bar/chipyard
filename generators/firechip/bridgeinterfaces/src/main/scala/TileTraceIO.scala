// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._

case class TraceBundleWidths(
  retireWidth: Int,
  iaddrWidth: Int,
  insnWidth: Int,
  wdataWidth: Option[Int],
  causeWidth: Int,
  tvalWidth: Int,
)

class TracedInstruction(traceBundleWidths: TraceBundleWidths) extends Bundle {
  val valid = Bool()
  val iaddr = UInt(traceBundleWidths.iaddrWidth.W)
  val insn = UInt(traceBundleWidths.insnWidth.W)
  val wdata = traceBundleWidths.wdataWidth.map { w => UInt(w.W) }
  val priv = UInt(3.W)
  val exception = Bool()
  val interrupt = Bool()
  val cause = UInt(traceBundleWidths.causeWidth.W)
  val tval = UInt(traceBundleWidths.tvalWidth.W)
}

class TraceBundle(traceBundleWidths: TraceBundleWidths) extends Bundle {
  val retiredinsns = Vec(traceBundleWidths.retireWidth, new TracedInstruction(traceBundleWidths))
  val time = UInt(64.W)
}

class TileTraceIO(traceBundleWidths: TraceBundleWidths) extends Bundle {
  val clock = Clock()
  val reset = Bool()
  val trace = new TraceBundle(traceBundleWidths)
}
