// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class CTCBridgeModule(implicit p: Parameters) extends BridgeModule[HostPortIO[CTCBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    // Copied from TSIBridge thank youuuu tsi bridge
    val io = IO(new WidgetIO)
    val hPort = IO(HostPort(new CTCBridgeTargetIO))

    val clientInBuf  = Module(new Queue(UInt(CTC.WIDTH.W), 16))
    val clientOutBuf = Module(new Queue(UInt(CTC.WIDTH.W), 16))
    val managerInBuf  = Module(new Queue(UInt(CTC.WIDTH.W), 16))
    val managerOutBuf = Module(new Queue(UInt(CTC.WIDTH.W), 16))

    val target = hPort.hBits.ctc_io
    val tFire = hPort.toHost.hValid && hPort.fromHost.hReady
    val targetReset = tFire & hPort.hBits.reset
    clientInBuf.reset  := reset.asBool || targetReset
    clientOutBuf.reset := reset.asBool || targetReset
    managerInBuf.reset  := reset.asBool || targetReset
    managerOutBuf.reset := reset.asBool || targetReset

    hPort.toHost.hReady := tFire
    hPort.fromHost.hValid := tFire

    // Connect client flit to buffer
    target.client_flit.in <> clientInBuf.io.deq
    clientInBuf.io.deq.ready := target.client_flit.in.ready && tFire

    clientOutBuf.io.enq <> target.client_flit.out
    clientOutBuf.io.enq.valid := target.client_flit.out.valid && tFire

    // Connect manager flit to buffer
    target.manager_flit.in <> managerInBuf.io.deq
    managerInBuf.io.deq.ready := target.manager_flit.in.ready && tFire

    managerOutBuf.io.enq <> target.manager_flit.out
    managerOutBuf.io.enq.valid := target.manager_flit.out.valid && tFire

    // target.client_flit.in.valid := clientInBuf.io.deq.valid
    // target.client_flit.in.bits.flit := clientInBuf.io.deq.bits
    // clientInBuf.io.deq.ready := target.client_flit.in.ready && tFire

    // clientOutBuf.io.enq.bits := target.client_flit.out.bits.flit
    // target.client_flit.out.ready := clientOutBuf.io.enq.ready
    // clientOutBuf.io.enq.valid := target.client_flit.out.valid && tFire

    // // Connect manager flit to buffer
    // target.manager_flit.in.valid := managerInBuf.io.deq.valid
    // target.manager_flit.in.bits.flit := managerInBuf.io.deq.bits
    // managerInBuf.io.deq.ready := target.manager_flit.in.ready && tFire

    // managerOutBuf.io.enq.bits := target.manager_flit.out.bits.flit
    // target.manager_flit.out.ready := managerOutBuf.io.enq.ready
    // managerOutBuf.io.enq.valid := target.manager_flit.out.valid && tFire

    // CLIENT MMIO
    genWOReg(clientInBuf.io.enq.bits, "client_in_bits")
    Pulsify(genWORegInit(clientInBuf.io.enq.valid, "client_in_valid", false.B), pulseLength = 1)
    genROReg(clientInBuf.io.enq.ready, "client_in_ready")
    genROReg(clientOutBuf.io.deq.bits, "client_out_bits")
    genROReg(clientOutBuf.io.deq.valid, "client_out_valid")
    Pulsify(genWORegInit(clientOutBuf.io.deq.ready, "client_out_ready", false.B), pulseLength = 1)

    // MANAGER MMIO
    genWOReg(managerInBuf.io.enq.bits, "manager_in_bits")
    Pulsify(genWORegInit(managerInBuf.io.enq.valid, "manager_in_valid", false.B), pulseLength = 1)
    genROReg(managerInBuf.io.enq.ready, "manager_in_ready")
    genROReg(managerOutBuf.io.deq.bits, "manager_out_bits")
    genROReg(managerOutBuf.io.deq.valid, "manager_out_valid")
    Pulsify(genWORegInit(managerOutBuf.io.deq.ready, "manager_out_ready", false.B), pulseLength = 1)

    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(base, sb, "ctc_t", "ctc")
    }
  }
}