// See LICENSE for license details

package firechip.bridgestubs

import chisel3._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._
import chipyard.iobinders.JTAGChipIO

class JTAGBridge(implicit p: Parameters)
    extends BlackBox
    with Bridge[HostPortIO[JTAGBridgeTargetIO]] {
  val moduleName = "firechip.goldengateimplementations.JTAGBridgeModule"
  val io = IO(new JTAGBridgeTargetIO)
  val bridgeIO = HostPort(io)
  val constructorArg = Some(JTAGBridgeParams())
  generateAnnotations()
}

object JTAGBridge {
  def apply(clock: Clock, port: JTAGChipIO, reset: Bool)(implicit p: Parameters): JTAGBridge = {
    val ep = Module(new JTAGBridge)
    ep.io.clock := clock
    ep.io.reset := reset
    // Bool -> Clock cast: the target treats this as the JTAG clock domain
    port.TCK := ep.io.jtag.TCK.asClock
    port.TMS := ep.io.jtag.TMS
    port.TDI := ep.io.jtag.TDI
    ep.io.jtag.TDO := port.TDO
    // If the JTAGChipIO has a reset, drive it from the bridge reset
    port.reset.foreach(_ := reset)
    ep
  }
}
