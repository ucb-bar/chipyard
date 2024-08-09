// See LICENSE for license details
package firesim.bridges

import midas.widgets._

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.debug.{ClockedDMIIO, DMIReq, DMIResp}

/** Class which parameterizes the DMIBridge
  *
  * memoryRegionNameOpt, if unset, indicates that firesim-fesvr should not attempt to write a payload into DRAM through
  * the loadmem unit. This is suitable for target designs which do not use the FASED DRAM model. If a FASEDBridge for
  * the backing AXI4 memory is present, then memoryRegionNameOpt should be set to the same memory region name which is
  * passed to the FASEDBridge. This enables fast payload loading in firesim-fesvr through the loadmem unit.
  */
case class DMIBridgeParams(memoryRegionNameOpt: Option[String], addrBits: Int)

class DMIBridge(memoryRegionNameOpt: Option[String], addrBits: Int)
    extends BlackBox
    with Bridge[HostPortIO[DMIBridgeTargetIO], DMIBridgeModule] {
  val io             = IO(new DMIBridgeTargetIO(addrBits))
  val bridgeIO       = HostPort(io)
  val constructorArg = Some(DMIBridgeParams(memoryRegionNameOpt, addrBits: Int))
  generateAnnotations()
}

object DMIBridge {
  def apply(
    clock:               Clock,
    port:                ClockedDMIIO,
    memoryRegionNameOpt: Option[String],
    reset:               Bool,
    addrBits:            Int,
  )(implicit p:          Parameters
  ): DMIBridge = {
    val ep = Module(new DMIBridge(memoryRegionNameOpt, addrBits))
    // req into target, resp out of target
    port.dmi.req     <> ep.io.debug.req
    ep.io.debug.resp <> port.dmi.resp
    port.dmiClock    := clock
    port.dmiReset    := reset
    ep.io.clock      := clock
    ep.io.reset      := reset
    ep
  }
}

// copy with no Parameters
class BasicDMIIO(addrBits: Int) extends Bundle {
  val req  = new DecoupledIO(new DMIReq(addrBits))
  val resp = Flipped(new DecoupledIO(new DMIResp))
}

class DMIBridgeTargetIO(addrBits: Int) extends Bundle {
  val debug = new BasicDMIIO(addrBits)
  val reset = Input(Bool())
  val clock = Input(Clock())
}

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
