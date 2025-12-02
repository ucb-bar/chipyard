// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

// copied from testchipip.serdes.SerialWidthAggregator
class SerialWidthAggregator(narrowW: Int, wideW: Int) extends Module {
  require(wideW > narrowW)
  require(wideW % narrowW == 0)
  val io = IO(new Bundle {
    val narrow = Flipped(Decoupled(UInt(narrowW.W)))
    val wide   = Decoupled(UInt(wideW.W))
  })

  val beats = wideW / narrowW

  val narrow_beats = RegInit(0.U(log2Ceil(beats).W))
  val narrow_last_beat = narrow_beats === (beats-1).U
  val narrow_data = Reg(Vec(beats-1, UInt(narrowW.W)))

  io.narrow.ready := Mux(narrow_last_beat, io.wide.ready, true.B)
  when (io.narrow.fire) {
    narrow_beats := Mux(narrow_last_beat, 0.U, narrow_beats + 1.U)
    when (!narrow_last_beat) { narrow_data(narrow_beats) := io.narrow.bits }
  }
  io.wide.valid := narrow_last_beat && io.narrow.valid
  io.wide.bits := Cat(io.narrow.bits, narrow_data.asUInt)
}

class TacitBridgeModule(key: TraceRawByteKey)(implicit p: Parameters) 
  extends BridgeModule[HostPortIO[TraceRawByteBridgeTargetIO]]()(p) 
    with StreamToHostCPU {

  val toHostCPUQueueDepth = 6144
  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO())

    val hPort = IO(HostPort(new TraceRawByteBridgeTargetIO))

    val aggregator = Module(new SerialWidthAggregator(8, BridgeStreamConstants.streamWidthBits))
    val txfifo = Module(new Queue(UInt(BridgeStreamConstants.streamWidthBits.W), 3072))

    val target = hPort.hBits.byte

    val fire = hPort.toHost.hValid &&
               hPort.fromHost.hReady &&
               txfifo.io.enq.ready

    val targetReset = fire & hPort.hBits.reset
    txfifo.reset := reset.asBool || targetReset

    hPort.toHost.hReady := fire
    hPort.fromHost.hValid := fire

    aggregator.io.narrow.bits := target.out.bits
    aggregator.io.narrow.valid := target.out.valid && fire
    target.out.ready := aggregator.io.narrow.ready && fire

    txfifo.io.enq <> aggregator.io.wide
    streamEnq <> txfifo.io.deq

    genCRFile()
    
    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(
          base, 
          sb, 
          "tacit_t", 
          "tacit", Seq(
            UInt32(toHostStreamIdx),
            UInt32(toHostCPUQueueDepth),
          ),
          hasStreams = true)
    }
  }
}