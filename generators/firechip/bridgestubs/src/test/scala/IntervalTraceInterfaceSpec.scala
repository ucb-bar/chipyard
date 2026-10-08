// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._
import firechip.bridgeinterfaces._
import firesim.lib.bridgeutils.{BridgeAnnotation, PipeBridgeChannel}
import org.scalatest.flatspec.AnyFlatSpec
import pmu.{PMUEvent, PMUManifest}

private class IntervalBridgeHarness(keys: Seq[IntervalTraceBridgeKey]) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))
  keys.foreach { key =>
    val interval_bridge = Module(new IntervalTraceBridge(key))
    interval_bridge.io.tiletrace.clock := clock
    interval_bridge.io.tiletrace.reset := reset
    interval_bridge.io.tiletrace.trace := 0.U.asTypeOf(interval_bridge.io.tiletrace.trace)
    interval_bridge.io.increments.foreach(_ := 0.U)
    // Unsized: width inference sizes each increment from what drives it.
    require(interval_bridge.io.increments.size == key.events.size && interval_bridge.io.increments.forall(_.widthOption.isEmpty))
  }
}

class IntervalTraceInterfaceSpec extends AnyFlatSpec {
  private val events = Seq(
    IntervalPMUEvent(0, "rob.instructions_retired", "instructions", "Architectural retirements"),
    IntervalPMUEvent(1, "tile.cycles", "cycles", "Tile cycles"))
  private val traceWidths = TraceBundleWidths(4, 40, 32, None, 64, 40)
  private val key = IntervalTraceBridgeKey("boom_tile_0", traceWidths, events)

  private def elaborate(keys: Seq[IntervalTraceBridgeKey]): firrtl.AnnotationSeq = {
    val annotations = new chisel3.stage.phases.Elaborate().transform(
      Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new IntervalBridgeHarness(keys))))
    new chisel3.stage.phases.Convert().transform(annotations)
  }

  "Interval trace metadata" should "reject invalid catalogs" in {
    intercept[IllegalArgumentException] { key.copy(events = events.reverse) }
    intercept[IllegalArgumentException] { key.copy(events = events.updated(1, events(1).copy(name = events.head.name))) }
    intercept[IllegalArgumentException] { key.copy(events = Seq.empty) }
    intercept[IllegalArgumentException] { key.copy(traceWidths = traceWidths.copy(retireWidth = 0)) }
    intercept[IllegalArgumentException] { key.copy(traceWidths = traceWidths.copy(iaddrWidth = 65)) }
    intercept[IllegalArgumentException] { key.copy(schemaVersion = 2) }
    intercept[IllegalArgumentException] { key.copy(traceWidths = traceWidths.copy(insnWidth = 16)) }
    intercept[IllegalArgumentException] { key.copy(traceWidths = traceWidths.copy(tvalWidth = 0)) }
    intercept[IllegalArgumentException] { key.copy(traceWidths = traceWidths.copy(wdataWidth = Some(0))) }
  }

  it should "convert a tile's event list into a bridge key" in {
    val manifest = PMUManifest("boom_tile_0", Vector(PMUEvent(0, "retired", "instructions", "Retired")))
    val converted = IntervalTraceBridge.key(manifest, traceWidths, retirementLatency = 1)
    assert(converted.source == "boom_tile_0" && converted.retirementLatency == 1)
    assert(converted.events == Seq(IntervalPMUEvent(0, "retired", "instructions", "Retired")))
    assert(IntervalTraceFormat.manifestJson(converted).contains("\"name\":\"retired\""))
  }

  "Interval trace stub" should "preserve narrow inputs and annotate every field in one clock domain" in {
    val annotations = elaborate(Seq(key))
    val bridges = annotations.collect { case a: BridgeAnnotation => a }
    assert(bridges.size == 1)
    val bridge = bridges.head
    assert(bridge.widgetClass == "firechip.goldengateimplementations.IntervalTraceBridgeModule")
    assert(bridge.widgetConstructorKey.contains(key))
    val channels = bridge.bridgeChannels.map {
      case channel: PipeBridgeChannel => channel
      case other => fail(s"Unexpected channel: $other")
    }
    // Reset, eight fields per slot, CSR time, and independently sized increments.
    assert(channels.size == 2 + 8 * key.traceWidths.retireWidth + key.events.size)
    assert(channels.forall(c => c.sources.size == 1 && c.sinks.isEmpty && c.latency == 1))
    assert(channels.map(_.clock).distinct.size == 1)
    assert(channels.map(_.name).distinct.size == channels.size)
    assert(!channels.exists(c => c.name.contains("ready") || c.name.contains("stall")))

    val encoded = firrtl.annotations.JsonProtocol.serialize(bridges)
    val decoded = firrtl.annotations.JsonProtocol.deserialize(encoded)
    assert(decoded == bridges)
  }

  it should "keep heterogeneous port shapes and same-shape tile catalogs distinct" in {
    val keys = Seq(key, key.copy(source = "boom_tile_1"),
      key.copy(source = "boom_tile_2", traceWidths = traceWidths.copy(retireWidth = 2, iaddrWidth = 64),
        events = Seq(events.head)),
      key.copy(traceWidths = traceWidths.copy(wdataWidth = Some(64))),
      key.copy(traceWidths = traceWidths.copy(tvalWidth = 64)))
    val first = elaborate(keys).collect { case a: BridgeAnnotation => a }
    assert(first.size == keys.size)
    assert(first.map(_.target.module).distinct.size == keys.size)
    assert(first.map(_.widgetConstructorKey).toSet == keys.map(Some(_)).toSet)
    val second = elaborate(keys).collect { case a: BridgeAnnotation => a }
    assert(first.map(_.target.module) == second.map(_.target.module))
  }

}
