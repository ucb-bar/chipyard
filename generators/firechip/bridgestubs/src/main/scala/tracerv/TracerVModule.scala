// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._

import org.chipsalliance.cde.config.{Config, Field, Parameters}

import midas.targetutils.TriggerSink

import firechip.bridgeinterfaces._

case object TracerVModuleInstrRetireCount extends Field[Int]

class TracerVModuleInstrRetireCount(n: Int) extends Config((site, here, up) => {
  case TracerVModuleInstrRetireCount => n
})

class TracerVModuleInstrRetireCount1 extends TracerVModuleInstrRetireCount(1)
class TracerVModuleInstrRetireCount5 extends TracerVModuleInstrRetireCount(5)
class TracerVModuleInstrRetireCount6 extends TracerVModuleInstrRetireCount(6)
class TracerVModuleInstrRetireCount7 extends TracerVModuleInstrRetireCount(7)
class TracerVModuleInstrRetireCount9 extends TracerVModuleInstrRetireCount(9)
class TracerVModuleInstrRetireCount14 extends TracerVModuleInstrRetireCount(14)
class TracerVModuleInstrRetireCount15 extends TracerVModuleInstrRetireCount(15)
class TracerVModuleInstrRetireCount32 extends TracerVModuleInstrRetireCount(32)

class TracerVDUTIO(widths: TraceBundleWidths) extends Bundle {
  val triggerSink = Output(Bool())
  val trace = Input(new TraceBundle(widths))
}

class TracerVDUT(implicit val p: Parameters) extends Module {
  val insnWidths = TraceBundleWidths(p(TracerVModuleInstrRetireCount), 40, 32, None, 64, 64)

  val io = IO(new TracerVDUTIO(insnWidths))

  val tracerV = TracerVBridge(insnWidths)
  tracerV.io.tiletrace.trace.retiredinsns := io.trace.retiredinsns
  tracerV.io.tiletrace.trace.time := 0.U // this test ignores this
  TriggerSink(io.triggerSink)
}

class TracerVModule(implicit p: Parameters) extends firesim.lib.testutils.PeekPokeHarness(() => new TracerVDUT)
