// See LICENSE for license details.

package firechip.bridgeinterfaces

import chisel3._
import chisel3.util.MixedVec
import firrtl.annotations.HasSerializationHints

/** Event metadata crossing the Chipyard / Golden Gate JVM boundary.
  * This is a bridge constructor value, not a second event-registration API.
  */
case class IntervalPMUEvent(id: Int, name: String, unit: String, description: String) {
  require(id >= 0, "Interval PMU event ID must be nonnegative")
  require(name.trim.nonEmpty, "Interval PMU event name must be nonempty")
}

/** One tile, one observation clock/reset, one ordered PMU catalog.
  * No Chisel handles or PMU hardware types may be serialized in this key.
  */
case class IntervalTraceBridgeKey(
  source: String,
  traceWidths: TraceBundleWidths,
  events: Seq[IntervalPMUEvent],
  schemaVersion: Int = 1,
  retirementLatency: Int = 0,
) extends HasSerializationHints {
  require(retirementLatency == 0 || retirementLatency == 1, "Interval trace supports zero or one cycle of retirement latency")
  require(schemaVersion == 1, "Unsupported interval bridge interface schema")
  require(source.trim.nonEmpty, "Interval bridge source must be nonempty")
  require(traceWidths.retireWidth > 0, "Interval bridge requires retirement slots")
  require(traceWidths.iaddrWidth > 0 && traceWidths.iaddrWidth <= 64,
    "Interval bridge PC width must be in [1, 64]")
  require(traceWidths.insnWidth == 32, "Interval bridge requires original 32-bit instruction fields")
  require(traceWidths.causeWidth > 0 && traceWidths.tvalWidth > 0,
    "Interval bridge requires nonempty trap fields")
  require(traceWidths.wdataWidth.forall(_ > 0), "Interval bridge writeback width must be positive")
  require(events.nonEmpty, "Interval bridge requires a PMU catalog")
  require(events.map(_.id) == events.indices, "Interval PMU IDs must be dense and ordered")
  require(events.map(_.name).distinct.size == events.size, "Duplicate interval PMU event names")

  override def typeHints: Seq[Class[_]] = Seq(classOf[TraceBundleWidths], classOf[IntervalPMUEvent])
}

/** PMU increments/reset describe the current target cycle; retirement may lag by
  * key.retirementLatency accepted cycles (BOOM uses one). The FPGA widget aligns
  * observations before accounting, including cycles with no retirement.
  * Increment ports are unsized: FIRRTL width inference sizes them from the tile's
  * increments on the target side, and from the target channels on the host side.
  * The host widget must consume trace, increments, and reset atomically through
  * HostPort. Simulation backpressure belongs to HostPort, not a DUT stall output.
  * PMU inputs are raw increments, independent of the tile PMU's counting controls.
  *
  * Reuse the tile trace also consumed by TracerV: valid means architectural
  * retirement; exception/interrupt are separate observations and may occur with
  * valid=false. Preserve lane holes and original instruction bits. The widget
  * decodes instruction size/control flow and counts accepted target cycles;
  * tiletrace.trace.time is a CSR timestamp, not the interval cycle counter.
  */
class IntervalTraceBridgeTargetIO(key: IntervalTraceBridgeKey) extends Bundle {
  val tiletrace = Input(new TileTraceIO(key.traceWidths))
  val increments = Input(MixedVec(key.events.map(_ => UInt())))
}
