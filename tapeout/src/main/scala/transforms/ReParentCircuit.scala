// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass
import firrtl.annotations._

case class ReParentCircuitAnnotation(target: ModuleTarget)
    extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget) = this.copy(n)
}

class ReParentCircuit extends Transform {
  def inputForm = HighForm
  def outputForm = HighForm

  def execute(state: CircuitState): CircuitState = {
    val c = state.circuit
    val newTopName = state.annotations.collectFirst {
      case ReParentCircuitAnnotation(tgt) => tgt.module
    }
    val newCircuit = c.copy(main = newTopName.getOrElse(c.main))
    val mainRename = newTopName.map { s =>
      val rmap = RenameMap()
      rmap.record(CircuitTarget(c.main), CircuitTarget(s))
      rmap
    }
    state.copy(circuit = newCircuit, renames = mainRename)
  }
}
