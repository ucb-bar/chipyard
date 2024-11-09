// See LICENSE for license details

package firechip.bridgestubs

import chisel3._

import firechip.bridgeinterfaces._

object ConvertTraceBundleWidths {
  def apply(widths: testchipip.cosim.TraceBundleWidths): TraceBundleWidths = {
    TraceBundleWidths(
      retireWidth = widths.retireWidth,
      iaddrWidth = widths.iaddr,
      insnWidth = widths.insn,
      wdataWidth = widths.wdata,
      causeWidth = widths.cause,
      tvalWidth = widths.tval,
    )
  }
}

object ConvertTileTraceIO {
  def apply(tiletrace: testchipip.cosim.TileTraceIO): TileTraceIO = {
    val ttw = Wire(new TileTraceIO(ConvertTraceBundleWidths(tiletrace.traceBundleWidths)))
    ttw.clock := tiletrace.clock
    ttw.reset := tiletrace.reset
    ttw.trace.retiredinsns.zip(tiletrace.trace.insns).map{ case (l, r) =>
      l.valid := r.valid
      l.iaddr := r.iaddr
      l.insn := r.insn
      l.wdata.zip(r.wdata).map { case (l, r) => l := r }
      l.priv := r.priv
      l.exception := r.exception
      l.interrupt := r.interrupt
      l.cause := r.cause
      l.tval := r.tval
    }
    ttw.trace.time := tiletrace.trace.time
    ttw
  }
}
