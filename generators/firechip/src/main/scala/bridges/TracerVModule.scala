//See LICENSE for license details.

package firesim.bridges

import chisel3._

import org.chipsalliance.cde.config.{Config, Field, Parameters}
import testchipip.cosim.{SerializableTracedInstruction, TraceBundleWidths}
import midas.targetutils.TriggerSink

class TracerVDUTIO(widths: TraceBundleWidths) extends Bundle {
  val triggerSink = Output(Bool())
  val insns       = Input(Vec(widths.retireWidth, new SerializableTracedInstruction(widths)))
}

class TracerVModuleTestCount1
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      1
    })

class TracerVModuleTestCount5
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      5
    })

class TracerVModuleTestCount6
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      6
    })

class TracerVModuleTestCount7
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      7
    })

class TracerVModuleTestCount9
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      9
    })

class TracerVModuleTestCount14
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      14
    })

class TracerVModuleTestCount15
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      15
    })

class TracerVModuleTestCount32
    extends Config((site, here, up) => { case TracerVModuleInstructionCount =>
      32
    })

case object TracerVModuleInstructionCount extends Field[Int]
case object TracerVModuleInstructionWidth extends Field[Int](40)

class TracerVDUT(implicit val p: Parameters) extends Module {

  val insnCount  = p(TracerVModuleInstructionCount)
  val insnWidth  = p(TracerVModuleInstructionWidth)
  val insnWidths = TraceBundleWidths(insnCount, 40, 32, None, 64, 64, None)

  val io = IO(new TracerVDUTIO(insnWidths))

  val tracerV = TracerVBridge(insnWidths)
  tracerV.io.trace.trace.insns := io.insns
  tracerV.io.trace.trace.time  := 0.U // this test ignores this
  TriggerSink(io.triggerSink)
}

class TracerVModule(implicit p: Parameters) extends PeekPokeMidasExampleHarness(() => new TracerVDUT)
