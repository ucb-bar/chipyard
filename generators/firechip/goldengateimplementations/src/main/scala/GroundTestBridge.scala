// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class GroundTestBridgeModule()(implicit p: Parameters) extends BridgeModule[HostPortIO[GroundTestBridgeTargetIO]]()(p) {
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
