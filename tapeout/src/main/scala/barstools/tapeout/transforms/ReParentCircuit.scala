// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations._
import firrtl.options.Dependency
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency

case class ReParentCircuitAnnotation(target: ModuleTarget)
    extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget) = this.copy(n)
}

class ReParentCircuit extends Transform with DependencyAPIMigration {

  override def prerequisites: Seq[TransformDependency] = Forms.HighForm
  override def optionalPrerequisites: Seq[TransformDependency] = Seq.empty
  override def optionalPrerequisiteOf: Seq[TransformDependency] = {
    Forms.HighEmitters :+ Dependency[RemoveUnusedModules]
  }
  override def invalidates(a: Transform): Boolean = false

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
