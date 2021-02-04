// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations.NoTargetAnnotation
import firrtl.ir._
import firrtl.options.Dependency
import firrtl.passes.memlib.ReplSeqMem
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency

case class LinkExtModulesAnnotation(mustLink: Seq[ExtModule]) extends NoTargetAnnotation

class AvoidExtModuleCollisions extends Transform with DependencyAPIMigration {

  override def prerequisites:         Seq[TransformDependency] = Forms.HighForm
  override def optionalPrerequisites: Seq[TransformDependency] = Seq(Dependency[RemoveUnusedModules])
  override def optionalPrerequisiteOf: Seq[TransformDependency] = {
    Forms.HighEmitters :+ Dependency[ReplSeqMem]
  }
  override def invalidates(a: Transform): Boolean = false

  def execute(state: CircuitState): CircuitState = {
    val mustLink = state.annotations.flatMap {
      case LinkExtModulesAnnotation(mustLink) => mustLink
      case _                                  => Nil
    }
    val newAnnos = state.annotations.filterNot(_.isInstanceOf[LinkExtModulesAnnotation])
    state.copy(circuit = state.circuit.copy(modules = state.circuit.modules ++ mustLink), annotations = newAnnos)
  }
}
