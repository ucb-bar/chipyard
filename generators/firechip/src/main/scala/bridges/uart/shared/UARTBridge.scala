//See LICENSE for license details
package firesim.bridges

import midas.widgets._

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import sifive.blocks.devices.uart.{UARTPortIO, UARTParams}

//Note: This file is heavily commented as it serves as a bridge walkthrough
//example in the FireSim docs


// DOC include start: UART Bridge Target-Side Interface
class UARTBridgeTargetIO(val uParams: UARTParams) extends Bundle {
  val clock = Input(Clock())
  val uart = Flipped(new UARTPortIO(uParams))
  // Note this reset is optional and used only to reset target-state modelled
  // in the bridge This reset just like any other Bool included in your target
  // interface, simply appears as another Bool in the input token.
  val reset = Input(Bool())
}
// DOC include end: UART Bridge Target-Side Interface

// DOC include start: UART Bridge Constructor Arg
// Out bridge module constructor argument. This captures all of the extra
// metadata we'd like to pass to the host-side BridgeModule. Note, we need to
// use a single case class to do so, even if it is simply to wrap a primitive
// type, as is the case for UART (int)
case class UARTKey(uParams: UARTParams, div: Int)
// DOC include end: UART Bridge Constructor Arg

// DOC include start: UART Bridge Target-Side Module
class UARTBridge(uParams: UARTParams, freqMHz: Int)(implicit p: Parameters) extends BlackBox
    with Bridge {
  // Module portion corresponding to this bridge
  val moduleName = "UARTBridgeModule"
  // Since we're extending BlackBox this is the port will connect to in our target's RTL
  val io = IO(new UARTBridgeTargetIO(uParams))
  // Implement the bridgeIO member of Bridge using HostPort. This indicates that
  // we want to divide io, into a bidirectional token stream with the input
  // token corresponding to all of the inputs of this BlackBox, and the output token consisting of
  // all of the outputs from the BlackBox
  val bridgeIO = HostPort(io)

  // Do some intermediate work to compute our host-side BridgeModule's constructor argument
  val baudrate = uParams.initBaudRate
  val div = (BigInt(freqMHz) * 1000000 / baudrate).toInt

  // And then implement the constructorArg member
  val constructorArg = Some(UARTKey(uParams, div))

  // Finally, and this is critical, emit the Bridge Annotations -- without
  // this, this BlackBox would appear like any other BlackBox to Golden Gate
  generateAnnotations()
}
// DOC include end: UART Bridge Target-Side Module

// DOC include start: UART Bridge Companion Object
object UARTBridge {
  def apply(clock: Clock, uart: UARTPortIO, reset: Bool, freqMHz: Int)(implicit p: Parameters): UARTBridge = {
    val ep = Module(new UARTBridge(uart.c, freqMHz))
    ep.io.uart <> uart
    ep.io.clock := clock
    ep.io.reset := reset
    ep
  }
}
// DOC include end: UART Bridge Companion Object
