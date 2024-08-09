//See LICENSE for license details
package firesim.bridges

import midas.widgets._

import chisel3._
import org.chipsalliance.cde.config.Parameters

class GroundTestBridge extends BlackBox
    with Bridge[HostPortIO[GroundTestBridgeTargetIO], GroundTestBridgeModule] {
  val io = IO(new GroundTestBridgeTargetIO)
  val bridgeIO = HostPort(io)
  val constructorArg = None
  generateAnnotations()
}

object GroundTestBridge {
  def apply(clock: Clock, success: Bool)(implicit p: Parameters): GroundTestBridge = {
    val bridge = Module(new GroundTestBridge)
    bridge.io.success := success
    bridge.io.clock := clock
    bridge
  }
}

class GroundTestBridgeTargetIO extends Bundle {
  val success = Input(Bool())
  val clock = Input(Clock())
}

class GroundTestBridgeModule(implicit p: Parameters)
    extends BridgeModule[HostPortIO[GroundTestBridgeTargetIO]] {
  lazy val module = new BridgeModuleImp(this) {
    val io = IO(new WidgetIO)
    val hPort = IO(HostPort(new GroundTestBridgeTargetIO))

    hPort.toHost.hReady := true.B
    hPort.fromHost.hValid := true.B

    val success = RegInit(false.B)

    when (hPort.hBits.success && !success) { success := true.B }

    genROReg(success, "success")
    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {}
  }
}
