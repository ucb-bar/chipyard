// See LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class TSIBridge(memoryRegionNameOpt: Option[String]) extends BlackBox with Bridge[HostPortIO[TSIBridgeTargetIO]] {
  val moduleName = "firechip.goldengateimplementations.TSIBridgeModule"
  val io = IO(new TSIBridgeTargetIO)
  val bridgeIO = HostPort(io)
  val constructorArg = Some(TSIBridgeParams(memoryRegionNameOpt))
  generateAnnotations()
}

object TSIBridge {
  def apply(clock: Clock, port: testchipip.tsi.TSIIO, memoryRegionNameOpt: Option[String], reset: Bool)(implicit p: Parameters): TSIBridge = {
    val ep = Module(new TSIBridge(memoryRegionNameOpt))
    require(ep.io.tsi.w == port.w)
    // TODO: Check following IOs are same size/names/etc
    ep.io.tsi <> port
    ep.io.clock := clock
    ep.io.reset := reset
    ep
  }
}
