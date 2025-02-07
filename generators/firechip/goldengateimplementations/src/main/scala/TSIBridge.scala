// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class TSIBridgeModule(tsiBridgeParams: TSIBridgeParams)(implicit p: Parameters)
    extends BridgeModule[HostPortIO[TSIBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO)
    val hPort = IO(HostPort(new TSIBridgeTargetIO))

    val inBuf  = Module(new Queue(UInt(TSI.WIDTH.W), 16))
    val outBuf = Module(new Queue(UInt(TSI.WIDTH.W), 16))
    val tokensToEnqueue = RegInit(0.U(32.W))

    val target = hPort.hBits.tsi
    val tFire = hPort.toHost.hValid && hPort.fromHost.hReady && tokensToEnqueue =/= 0.U
    val targetReset = tFire & hPort.hBits.reset
    inBuf.reset  := reset.asBool || targetReset
    outBuf.reset := reset.asBool || targetReset

    hPort.toHost.hReady := tFire
    hPort.fromHost.hValid := tFire

    target.in <> inBuf.io.deq
    inBuf.io.deq.ready := target.in.ready && tFire

    outBuf.io.enq <> target.out
    outBuf.io.enq.valid := target.out.valid && tFire

    genWOReg(inBuf.io.enq.bits, "in_bits")
    Pulsify(genWORegInit(inBuf.io.enq.valid, "in_valid", false.B), pulseLength = 1)
    genROReg(inBuf.io.enq.ready, "in_ready")
    genROReg(outBuf.io.deq.bits, "out_bits")
    genROReg(outBuf.io.deq.valid, "out_valid")
    Pulsify(genWORegInit(outBuf.io.deq.ready, "out_ready", false.B), pulseLength = 1)

    val stepSize = Wire(UInt(32.W))
    val start = Wire(Bool())
    when (start) {
      tokensToEnqueue := stepSize
    }.elsewhen (tFire) {
      tokensToEnqueue := tokensToEnqueue - 1.U
    }

    genWOReg(stepSize, "step_size")
    genROReg(tokensToEnqueue === 0.U, "done")
    Pulsify(genWORegInit(start, "start", false.B), pulseLength = 1)

    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      val memoryRegionNameOpt = tsiBridgeParams.memoryRegionNameOpt
      val offsetConst = memoryRegionNameOpt.map(memoryRegions(_)).getOrElse(BigInt(0))

      genConstructor(
          base,
          sb,
          "tsibridge_t",
          "tsibridge",
          Seq(
              CppBoolean(tsiBridgeParams.memoryRegionNameOpt.isDefined),
              UInt64(offsetConst)
          ),
          hasLoadMem = true
      )
    }
  }
}
