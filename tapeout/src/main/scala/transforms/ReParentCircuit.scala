// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass

// "Re-Parents" a circuit, which changes the top module to something else.
class ReParentCircuitPass(newTopName: String) extends Pass {
  def name = "Re-Parent Circuit"

  def run(c: Circuit): Circuit = {
    Circuit(c.info, c.modules, newTopName)
  }
}

class ReParentCircuit(newTopName: String) extends Transform with PassBased {
  def inputForm = HighForm
  def outputForm = HighForm
  def passSeq = Seq(new ReParentCircuitPass(newTopName))

  def execute(state: CircuitState): CircuitState = {
    state.copy(circuit = runPasses(state.circuit))
  }
}
