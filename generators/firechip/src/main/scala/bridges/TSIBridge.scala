// See LICENSE for license details
package firesim.bridges

import midas.widgets._

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters

import testchipip.tsi.{TSIIO, TSI}

/**
  * Class which parameterizes the TSIBridge
  *
  * memoryRegionNameOpt, if unset, indicates that firesim-fesvr should not attempt to write a payload into DRAM through the loadmem unit.
  * This is suitable for target designs which do not use the FASED DRAM model.
  * If a FASEDBridge for the backing AXI4 memory is present, then memoryRegionNameOpt should be set to the same memory region name which is passed
  * to the FASEDBridge. This enables fast payload loading in firesim-fesvr through the loadmem unit.
  */
case class TSIBridgeParams(memoryRegionNameOpt: Option[String])

class TSIBridge(memoryRegionNameOpt: Option[String]) extends BlackBox with Bridge[HostPortIO[TSIBridgeTargetIO], TSIBridgeModule] {
  val io = IO(new TSIBridgeTargetIO)
  val bridgeIO = HostPort(io)
  val constructorArg = Some(TSIBridgeParams(memoryRegionNameOpt))
  generateAnnotations()
}

object TSIBridge {
  def apply(clock: Clock, port: TSIIO, memoryRegionNameOpt: Option[String], reset: Bool)(implicit p: Parameters): TSIBridge = {
    val ep = Module(new TSIBridge(memoryRegionNameOpt))
    ep.io.tsi <> port
    ep.io.clock := clock
    ep.io.reset := reset
    ep
  }
}

class TSIBridgeTargetIO extends Bundle {
  val tsi = Flipped(new TSIIO)
  val reset = Input(Bool())
  val clock = Input(Clock())
}

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
