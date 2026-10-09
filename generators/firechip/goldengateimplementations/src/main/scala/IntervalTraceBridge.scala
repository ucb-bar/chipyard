// See LICENSE for license details.
package firechip.goldengateimplementations

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import midas.widgets._
import firesim.lib.bridgeutils._
import firechip.bridgeinterfaces._

class IntervalTraceBridgeModule(key: IntervalTraceBridgeKey)(implicit p: Parameters)
    extends BridgeModule[HostPortIO[IntervalTraceBridgeTargetIO]]()(p) with StreamToHostCPU {
  val toHostCPUQueueDepth = 6144

  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO)
    val hPort = IO(HostPort(new IntervalTraceBridgeTargetIO(key)))
    val datapath = Module(new IntervalTraceDatapath(key))
    val aligner = Module(new IntervalTraceAligner(key))
    datapath.io.target <> aligner.io.out

    // Register order is shared with INTERVALTRACEBRIDGEMODULE_struct.
    val init_done = genWORegInit(Wire(Bool()), "init_done", false.B)
    val capture_enable = genWORegInit(Wire(Bool()), "capture_enable", false.B)
    val interval_low = genWORegInit(Wire(UInt(32.W)), "interval_low", 1000000.U(32.W))
    val interval_high = genWORegInit(Wire(UInt(32.W)), "interval_high", 0.U(32.W))
    genROReg(datapath.io.active, "active")
    genROReg(datapath.io.drained, "drained")
    genROReg(datapath.io.config_error, "config_error")
    val trigger_selector = genWORegInit(Wire(UInt(32.W)), "trigger_selector", 0.U(32.W))
    val trigger_start_low = genWORegInit(Wire(UInt(32.W)), "trigger_start_low", 0.U(32.W))
    val trigger_start_high = genWORegInit(Wire(UInt(32.W)), "trigger_start_high", 0.U(32.W))
    val trigger_end_low = genWORegInit(Wire(UInt(32.W)), "trigger_end_low", "hffffffff".U(32.W))
    val trigger_end_high = genWORegInit(Wire(UInt(32.W)), "trigger_end_high", "hffffffff".U(32.W))
    datapath.io.trigger_selector := trigger_selector
    datapath.io.trigger_start := Cat(trigger_start_high, trigger_start_low)
    datapath.io.trigger_end := Cat(trigger_end_high, trigger_end_low)
    datapath.io.capture_enable := init_done && capture_enable
    datapath.io.nominal_interval := Cat(interval_high, interval_low)

    aligner.io.in.bits.target_reset := hPort.hBits.tiletrace.reset
    aligner.io.in.bits.trace := hPort.hBits.tiletrace.trace
    aligner.io.in.bits.increments := hPort.hBits.increments
    // Both token directions must agree on the same target step. Host-side
    // serializers continue running whenever target acceptance is paused.
    aligner.io.in.valid := init_done && hPort.toHost.hValid && hPort.fromHost.hReady
    hPort.toHost.hReady := init_done && aligner.io.in.ready && hPort.fromHost.hReady
    hPort.fromHost.hValid := init_done && aligner.io.in.ready && hPort.toHost.hValid
    streamEnq <> datapath.io.stream
    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(base, sb, "intervaltrace_t", "intervaltrace", Seq(
        UInt32(toHostStreamIdx), UInt32(toHostCPUQueueDepth), UInt32(key.events.size),
        UInt64(IntervalTraceFormat.catalogId(key)), CStrLit(IntervalTraceFormat.manifestJson(key)),
        StdVector("std::string", key.events.map(e => CStrLit(e.name)))), hasStreams = true)
    }
  }
}
