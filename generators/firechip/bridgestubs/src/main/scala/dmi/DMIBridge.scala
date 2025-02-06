// See LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.debug.{ClockedDMIIO}

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class DMIBridge(memoryRegionNameOpt: Option[String], addrBits: Int)
    extends BlackBox
    with Bridge[HostPortIO[DMIBridgeTargetIO]] {
  val moduleName = "firechip.goldengateimplementations.DMIBridgeModule"
  val io             = IO(new DMIBridgeTargetIO(addrBits))
  val bridgeIO = HostPort(io)
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
    // TODO: Check following IOs are same size/names/etc
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
