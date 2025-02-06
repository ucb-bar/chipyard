// See LICENSE for license details

package firechip.bridgestubs

import chisel3._

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

/** Blackbox that is instantiated in the target
  */
class CospikeBridge(params: CospikeBridgeParams)
    extends BlackBox
    with Bridge[HostPortIO[CospikeBridgeTargetIO]] {
  val moduleName = "firechip.goldengateimplementations.CospikeBridgeModule"
  val io       = IO(new CospikeBridgeTargetIO(params.widths))
  val bridgeIO = HostPort(io)

  // give the Cospike params to the GG module
  val constructorArg = Some(params)

  // generate annotations to pass to GG
  generateAnnotations()
}

object ConvertSpikeCosimConfig {
  def apply(widths: testchipip.cosim.SpikeCosimConfig): SpikeCosimConfig = {
    SpikeCosimConfig(
      isa = widths.isa,
      priv = widths.priv,
      pmpregions = widths.pmpregions,
      maxpglevels = widths.maxpglevels,
      mem0_base = widths.mem0_base,
      mem0_size = widths.mem0_size,
      nharts = widths.nharts,
      bootrom = widths.bootrom,
      has_dtm = widths.has_dtm,
      mem1_base = widths.mem1_base,
      mem1_size = widths.mem1_size,
      mem2_base = widths.mem2_base,
      mem2_size = widths.mem2_size,
    )
  }
}

/** Helper function to connect blackbox
  */
object CospikeBridge {
  def apply(tracedInsns: testchipip.cosim.TileTraceIO, hartid: Int, cfg: testchipip.cosim.SpikeCosimConfig) = {
    val params = new CospikeBridgeParams(
      ConvertTraceBundleWidths(tracedInsns.traceBundleWidths),
      hartid,
      ConvertSpikeCosimConfig(cfg))
    val cosim  = withClockAndReset(tracedInsns.clock, tracedInsns.reset) {
      Module(new CospikeBridge(params))
    }
    cosim.io.tiletrace.trace.retiredinsns.map(t => {
      t       := DontCare
      t.valid := false.B
    })
    cosim.io.tiletrace <> ConvertTileTraceIO(tracedInsns)
    cosim
  }
}
