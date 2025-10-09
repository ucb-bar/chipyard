// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._

import org.chipsalliance.cde.config.Parameters

class TacitDUT(implicit val p: Parameters) extends Module {
  val ep = Module(new TacitBridge)
  ep.io.byte.out := ep.io.byte.out
  ep.io.reset := reset
  ep.io.clock := clock
}

class TacitModule(implicit p: Parameters) extends firesim.lib.testutils.PeekPokeHarness(() => new TacitDUT)
