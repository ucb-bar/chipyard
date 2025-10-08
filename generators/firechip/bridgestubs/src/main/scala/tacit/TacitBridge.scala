// see LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class TacitBridge(implicit p: Parameters) extends BlackBox 
    with Bridge[HostPortIO[ByteBridgeTargetIO]] {
  

  val moduleName = "firechip.goldengateimplementations.TacitBridgeModule"

  val io = IO(new ByteBridgeTargetIO)
  val bridgeIO = HostPort(io)

  val constructorArg = Some(ByteKey())

  generateAnnotations()
}

object TacitBridge {
  def apply(clock: Clock, byte: UInt, reset: Bool)(implicit p: Parameters): TacitBridge = {
    val ep = Module(new TacitBridge)
    ep.io.byte := byte
    ep.io.reset := reset
    ep.io.clock := clock
    ep
  }
}