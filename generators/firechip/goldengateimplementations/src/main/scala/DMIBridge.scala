// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class DMIBridgeModule(dmiBridgeParams: DMIBridgeParams)(implicit p: Parameters)
    extends BridgeModule[HostPortIO[DMIBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val io    = IO(new WidgetIO)
    val hPort = IO(HostPort(new DMIBridgeTargetIO(dmiBridgeParams.addrBits)))

    // in(to) target go req's, out of target go resp's
    val inBuf           = Module(new Queue(new DMIReq(dmiBridgeParams.addrBits), 16))
    val outBuf          = Module(new Queue(new DMIResp(), 16))
    val tokensToEnqueue = RegInit(0.U(32.W))

    val target      = hPort.hBits.debug
    val tFire       = hPort.toHost.hValid && hPort.fromHost.hReady && tokensToEnqueue =/= 0.U
    val targetReset = tFire & hPort.hBits.reset
    inBuf.reset  := reset.asBool || targetReset
    outBuf.reset := reset.asBool || targetReset

    hPort.toHost.hReady   := tFire
    hPort.fromHost.hValid := tFire

    target.req         <> inBuf.io.deq
    inBuf.io.deq.ready := target.req.ready && tFire

    outBuf.io.enq       <> target.resp
    outBuf.io.enq.valid := target.resp.valid && tFire

    genWOReg(inBuf.io.enq.bits.addr, "in_bits_addr")
    genWOReg(inBuf.io.enq.bits.data, "in_bits_data")
    genWOReg(inBuf.io.enq.bits.op, "in_bits_op")
    Pulsify(genWORegInit(inBuf.io.enq.valid, "in_valid", false.B), pulseLength = 1)
    genROReg(inBuf.io.enq.ready, "in_ready")

    genROReg(outBuf.io.deq.bits.data, "out_bits_data")
    genROReg(outBuf.io.deq.bits.resp, "out_bits_resp")
    genROReg(outBuf.io.deq.valid, "out_valid")
    Pulsify(genWORegInit(outBuf.io.deq.ready, "out_ready", false.B), pulseLength = 1)

    val stepSize = Wire(UInt(32.W))
    val start    = Wire(Bool())
    when(start) {
      tokensToEnqueue := stepSize
    }.elsewhen(tFire) {
      tokensToEnqueue := tokensToEnqueue - 1.U
    }

    genWOReg(stepSize, "step_size")
    genROReg(tokensToEnqueue === 0.U, "done")
    Pulsify(genWORegInit(start, "start", false.B), pulseLength = 1)

    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      val memoryRegionNameOpt = dmiBridgeParams.memoryRegionNameOpt
      val offsetConst         = memoryRegionNameOpt.map(memoryRegions(_)).getOrElse(BigInt(0))

      genConstructor(
        base,
        sb,
        "dmibridge_t",
        "dmibridge",
        Seq(
          CppBoolean(dmiBridgeParams.memoryRegionNameOpt.isDefined),
          UInt64(offsetConst),
        ),
        hasLoadMem = true,
      )
    }
  }
}
