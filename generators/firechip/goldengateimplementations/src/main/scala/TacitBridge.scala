// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class TacitBridgeModule(key: TraceRawByteKey)(implicit p: Parameters) extends BridgeModule[HostPortIO[TraceRawByteBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO())

    val hPort = IO(HostPort(new TraceRawByteBridgeTargetIO))

    val txfifo = Module(new Queue(UInt(8.W), 128))

    val target = hPort.hBits.byte

    val fire = hPort.toHost.hValid &&
               hPort.fromHost.hReady &&
               txfifo.io.enq.ready

    val targetReset = fire & hPort.hBits.reset
    txfifo.reset := reset.asBool || targetReset

    hPort.toHost.hReady := fire
    hPort.fromHost.hValid := fire

    txfifo.io.enq.bits := target.out.bits
    txfifo.io.enq.valid := target.out.valid && fire
    target.out.ready := txfifo.io.enq.ready && fire

    genROReg(txfifo.io.deq.bits, "out_bits")
    genROReg(txfifo.io.deq.valid, "out_valid")

    Pulsify(genWORegInit(txfifo.io.deq.ready, "out_ready", false.B), pulseLength = 1)

    genCRFile()
    
    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(base, sb, "tacit_t", "tacit")
    }
  }
}