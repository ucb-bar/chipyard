// See LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class NICBridge(implicit p: Parameters) extends BlackBox with Bridge[HostPortIO[NICBridgeTargetIO]] {
  val moduleName = "firechip.goldengateimplementations.SimpleNICBridgeModule"
  val io = IO(new NICBridgeTargetIO)
  val bridgeIO = HostPort(io)
  val constructorArg = None
  generateAnnotations()
}


object NICBridge {
  def apply(clock: Clock, nicIO: icenet.NICIOvonly)(implicit p: Parameters): NICBridge = {
    val ep = Module(new NICBridge)
    // TODO: Check following IOs are same size/names/etc
    ep.io.nic <> nicIO
    ep.io.clock := clock
    ep
  }
}
