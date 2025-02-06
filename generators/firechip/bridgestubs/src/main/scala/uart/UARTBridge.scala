// See LICENSE for license details

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

// Note: This file is heavily commented as it serves as a bridge walkthrough
// example in the FireSim docs

// DOC include start: UART Bridge Target-Side Module
class UARTBridge(initBaudRate: BigInt, freqMHz: Int)(implicit p: Parameters) extends BlackBox
    with Bridge[HostPortIO[UARTBridgeTargetIO]] {
  // Module portion corresponding to this bridge
  val moduleName = "firechip.goldengateimplementations.UARTBridgeModule"
  // Since we're extending BlackBox this is the port will connect to in our target's RTL
  val io = IO(new UARTBridgeTargetIO)
  // Implement the bridgeIO member of Bridge using HostPort. This indicates that
  // we want to divide io, into a bidirectional token stream with the input
  // token corresponding to all of the inputs of this BlackBox, and the output token consisting of
  // all of the outputs from the BlackBox
  val bridgeIO = HostPort(io)

  // Do some intermediate work to compute our host-side BridgeModule's constructor argument
  val div = (BigInt(freqMHz) * 1000000 / initBaudRate).toInt

  // And then implement the constructorArg member
  val constructorArg = Some(UARTKey(div))

  // Finally, and this is critical, emit the Bridge Annotations -- without
  // this, this BlackBox would appear like any other BlackBox to Golden Gate
  generateAnnotations()
}
// DOC include end: UART Bridge Target-Side Module

// DOC include start: UART Bridge Companion Object
object UARTBridge {
  def apply(clock: Clock, uart: sifive.blocks.devices.uart.UARTPortIO, reset: Bool, freqMHz: Int)(implicit p: Parameters): UARTBridge = {
    val ep = Module(new UARTBridge(uart.c.initBaudRate, freqMHz))
    ep.io.uart.txd := uart.txd
    uart.rxd := ep.io.uart.rxd
    ep.io.clock := clock
    ep.io.reset := reset
    ep
  }
}
// DOC include end: UART Bridge Companion Object
