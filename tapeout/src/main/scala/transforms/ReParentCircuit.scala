// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass
import firrtl.annotations._

class ReParentCircuit(newTopName: String) extends Transform {
  def inputForm = HighForm
  def outputForm = HighForm

  def run(c: Circuit, newTopName: String): (Circuit, RenameMap) = {
    val myRenames = RenameMap()
    myRenames.record(CircuitTarget(c.main), CircuitTarget(newTopName))
    (Circuit(c.info, c.modules, newTopName),  myRenames)
  }

  def execute(state: CircuitState): CircuitState = {
    val (ret, renames) = run(state.circuit, newTopName)
    state.copy(circuit = ret, renames = Some(renames))
  }
}
