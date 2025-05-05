// See LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

// This is the id of the chip we want to connect to (for args passing)
case class CTCKey(otherChipId: Int)

class CTCBridge(implicit p: Parameters) extends BlackBox
    with Bridge[HostPortIO[CTCBridgeTargetIO]] {

  val moduleName = "firechip.goldengateimplementations.CTCBridgeModule"

  val io = IO(new CTCBridgeTargetIO)

  val bridgeIO = HostPort(io)

  val constructorArg = None //Some(CTCKey(otherChipId))

  generateAnnotations()
}

object CTCBridge {
  def apply(clock: Clock, port: testchipip.ctc.CTCBridgeIO, reset: Bool)(implicit p: Parameters): CTCBridge = {
    val ep = Module(new CTCBridge)
    // ep.io.ctc_io.client_flit.in.valid <> port.client_flit.in
    // ep.io.ctc_io.client_flit.out <> port.client_flit.out
    // ep.io.ctc_io.manager_flit.in <> port.manager_flit.in
    // ep.io.ctc_io.manager_flit.out <> port.manager_flit.out
    //ep.io.ctc_io <> port
    
    port.client_flit.in.valid := ep.io.ctc_io.client_flit.in.valid
    port.client_flit.in.bits.flit := ep.io.ctc_io.client_flit.in.bits
    ep.io.ctc_io.client_flit.in.ready := port.client_flit.in.ready

    ep.io.ctc_io.client_flit.out.valid := port.client_flit.out.valid
    ep.io.ctc_io.client_flit.out.bits := port.client_flit.out.bits.flit
    port.client_flit.out.ready := ep.io.ctc_io.client_flit.out.ready

    port.manager_flit.in.valid := ep.io.ctc_io.manager_flit.in.valid
    port.manager_flit.in.bits.flit := ep.io.ctc_io.manager_flit.in.bits
    ep.io.ctc_io.manager_flit.in.ready := port.manager_flit.in.ready

    ep.io.ctc_io.manager_flit.out.valid := port.manager_flit.out.valid
    ep.io.ctc_io.manager_flit.out.bits := port.manager_flit.out.bits.flit
    port.manager_flit.out.ready := ep.io.ctc_io.manager_flit.out.ready


    ep.io.clock := clock
    ep.io.reset := reset
    ep
  }
}
