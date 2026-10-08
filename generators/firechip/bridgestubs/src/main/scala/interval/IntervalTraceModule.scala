// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._
import chisel3.util.MixedVec
import org.chipsalliance.cde.config.{Config, Field, Parameters}

import firechip.bridgeinterfaces._

case class IntervalTraceModuleParams(retireWidth: Int, events: Int)
case object IntervalTraceModuleKey extends Field[IntervalTraceModuleParams]

class IntervalTraceModuleConfig(retireWidth: Int, events: Int) extends Config((site, here, up) => {
  case IntervalTraceModuleKey => IntervalTraceModuleParams(retireWidth, events)
})

class IntervalTraceModuleWidth1Events3 extends IntervalTraceModuleConfig(1, 3)
class IntervalTraceModuleWidth4Events19 extends IntervalTraceModuleConfig(4, 19)

object IntervalTraceModule {
  def traceWidths(retireWidth: Int): TraceBundleWidths = TraceBundleWidths(retireWidth, 40, 32, None, 64, 40)
  // Mixed increment widths exercise width inference; event 0 is wide enough to wrap.
  def incrementBits(event: Int): Int = Seq(64, 1, 4, 8)(event % 4)
  def events(count: Int): Seq[IntervalPMUEvent] =
    Seq.tabulate(count)(i => IntervalPMUEvent(i, f"event_$i%02d", "events", s"Test event $i"))
}

class IntervalTraceDUTIO(widths: TraceBundleWidths, events: Int) extends Bundle {
  val trace_reset = Input(Bool())
  val trace = Input(new TraceBundle(widths))
  val increments = Input(MixedVec(Seq.tabulate(events)(i => UInt(IntervalTraceModule.incrementBits(i).W))))
}

/** Two interval trace bridges observe the same host-driven trace and increments.
  * Bridge 0 takes every event and pairs each cycle's increments with the next
  * cycle's retirement; bridge 1 takes a prefix of the events, already aligned.
  */
class IntervalTraceDUT(implicit p: Parameters) extends Module {
  val params = p(IntervalTraceModuleKey)
  val widths = IntervalTraceModule.traceWidths(params.retireWidth)
  val io = IO(new IntervalTraceDUTIO(widths, params.events))
  Seq((params.events, 1), (params.events min 2, 0)).zipWithIndex.foreach { case ((events, latency), i) =>
    val key = IntervalTraceBridgeKey(s"test_tile_$i", widths, IntervalTraceModule.events(events),
      retirementLatency = latency)
    val interval_bridge = Module(new IntervalTraceBridge(key))
    interval_bridge.io.tiletrace.clock := clock
    interval_bridge.io.tiletrace.reset := io.trace_reset
    interval_bridge.io.tiletrace.trace := io.trace
    interval_bridge.io.increments.zip(io.increments).foreach { case (sink, source) => sink := source }
  }
}

class IntervalTraceModule(implicit p: Parameters) extends firesim.lib.testutils.PeekPokeHarness(() => new IntervalTraceDUT)
