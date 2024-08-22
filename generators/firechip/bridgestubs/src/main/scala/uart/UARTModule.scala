// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._

import org.chipsalliance.cde.config.Parameters

class UARTDUT(implicit val p: Parameters) extends Module {
  val ep = Module(new UARTBridge(initBaudRate=115200, freqMHz=100))
  ep.io.reset    := reset
  ep.io.clock    := clock
  ep.io.uart.txd := ep.io.uart.rxd
}

class UARTModule(implicit p: Parameters) extends firesim.lib.testutils.PeekPokeHarness(() => new UARTDUT)
