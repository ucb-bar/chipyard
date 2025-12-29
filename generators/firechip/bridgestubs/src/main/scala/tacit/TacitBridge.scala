// see LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

class TacitBridge() extends BlackBox 
    with Bridge[HostPortIO[TraceRawByteBridgeTargetIO]] {
  

  val moduleName = "firechip.goldengateimplementations.TacitBridgeModule"

  val io = IO(new TraceRawByteBridgeTargetIO)
  val bridgeIO = HostPort(io)

  val constructorArg = Some(TraceRawByteKey())

  generateAnnotations()
}

object TacitBridge {
  def apply(clock: Clock, byte: tacit.TraceSinkRawByteBundle, reset: Bool): TacitBridge = {
    val ep = Module(new TacitBridge)
    ep.io.byte.out <> byte.out
    ep.io.reset := reset
    ep.io.clock := clock
    ep
  }
}