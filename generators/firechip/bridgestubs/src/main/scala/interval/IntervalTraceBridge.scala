// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._
import firesim.lib.bridgeutils._
import firechip.bridgeinterfaces._
import pmu.PMUManifest

/** Target endpoint for one tile's trace and PMU increments. Increment ports are
  * unsized; width inference sizes them from the connected increments.
  */
class IntervalTraceBridge(key: IntervalTraceBridgeKey) extends BlackBox
    with Bridge[HostPortIO[IntervalTraceBridgeTargetIO]] {
  val moduleName = "firechip.goldengateimplementations.IntervalTraceBridgeModule"
  val io = IO(new IntervalTraceBridgeTargetIO(key))
  val bridgeIO = HostPort(io)
  val constructorArg = Some(key)
  generateAnnotations()

  // Different port shapes must not share an extmodule defname. Include source
  // and catalog identity too, so distinct tile catalogs retain their annotation.
  private val identityFields = Seq(key.schemaVersion.toString, key.source, key.retirementLatency.toString,
    key.traceWidths.retireWidth.toString, key.traceWidths.iaddrWidth.toString,
    key.traceWidths.insnWidth.toString, key.traceWidths.wdataWidth.toString,
    key.traceWidths.causeWidth.toString, key.traceWidths.tvalWidth.toString) ++ key.events.flatMap { event =>
    Seq(event.id.toString, event.name, event.unit, event.description)
  }
  private val identity = identityFields.map(s => s"${s.length}:$s").mkString
  private val digest = java.security.MessageDigest.getInstance("SHA-256")
    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    .map(b => f"${b & 0xff}%02x").mkString
  override def desiredName: String = s"IntervalTraceBridge_$digest"
}

object IntervalTraceBridge {
  /** Key for a tile's event list. */
  def key(manifest: PMUManifest, traceWidths: TraceBundleWidths, retirementLatency: Int = 0): IntervalTraceBridgeKey =
    IntervalTraceBridgeKey(manifest.source, traceWidths,
      manifest.events.map(e => IntervalPMUEvent(e.id, e.name, e.unit, e.description)),
      retirementLatency = retirementLatency)

  /** Attach to the existing exported tile trace without instantiating TracerV.
    * Increments are in event-ID order, in the tile trace's clock domain.
    * retirementLatency is the number of cycles by which the trace's retirement
    * lags the PMU increments; the FPGA widget aligns them.
    */
  def apply(tile_trace: testchipip.cosim.TileTraceIO, manifest: PMUManifest, increments: Seq[UInt],
            retirementLatency: Int): IntervalTraceBridge = {
    val bridgeKey = key(manifest, ConvertTraceBundleWidths(tile_trace.traceBundleWidths), retirementLatency)
    require(increments.size == bridgeKey.events.size, "Interval PMU input count mismatch")
    val interval_bridge = withClockAndReset(tile_trace.clock, tile_trace.reset) {
      Module(new IntervalTraceBridge(bridgeKey))
    }
    interval_bridge.io.tiletrace := ConvertTileTraceIO(tile_trace)
    interval_bridge.io.increments.zip(increments).foreach { case (sink, source) => sink := source }
    interval_bridge
  }
}
