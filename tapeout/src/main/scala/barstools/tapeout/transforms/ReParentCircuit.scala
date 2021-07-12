// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations._
import firrtl.options.Dependency
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency
import firrtl.annotations.TargetToken.{Instance, OfModule}

case class ReParentCircuitAnnotation(target: ModuleTarget) extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget) = this.copy(n)
}

class ReParentCircuit extends Transform with DependencyAPIMigration {

  override def prerequisites:         Seq[TransformDependency] = Forms.HighForm
  override def optionalPrerequisites: Seq[TransformDependency] = Seq.empty
  override def optionalPrerequisiteOf: Seq[TransformDependency] = {
    Forms.HighEmitters :+ Dependency[RemoveUnusedModules]
  }
  override def invalidates(a: Transform): Boolean = false

  def execute(state: CircuitState): CircuitState = {
    val c = state.circuit
    val newTopName = state.annotations.collectFirst { case ReParentCircuitAnnotation(tgt) =>
      tgt.module
    }
    val newCircuit = c.copy(main = newTopName.getOrElse(c.main))
    val mainRename = newTopName.map { s =>
      val rmap = RenameMap()
      rmap.record(CircuitTarget(c.main), CircuitTarget(s))
      rmap
    }

    val newAnnotations = newTopName.map({ topName =>
      // Update InstanceTargets
      def updateInstanceTarget(t: InstanceTarget): Option[InstanceTarget] = {
        val idx = t.path.lastIndexWhere(_._2.value == topName)
        if (idx == -1) Some(t.copy(circuit=topName)) else Some(t.copy(circuit=topName, module=topName, path=t.path.drop(idx+1)))
      }

      AnnotationSeq(state.annotations.toSeq.map({
        case x: SingleTargetAnnotation[InstanceTarget] if x.target.isInstanceOf[InstanceTarget] =>
          updateInstanceTarget(x.target).map(y => x.duplicate(y))
        case x: MultiTargetAnnotation =>
          val newTargets: Seq[Seq[Option[Target]]] = x.targets.map(_.map({
            case y: InstanceTarget => updateInstanceTarget(y)
            case y => Some(y)
          }))
          if (newTargets.flatten.forall(_.isDefined)) Some(x.duplicate(newTargets.map(_.map(_.get)))) else None
        case x => Some(x)
      }).filter(_.isDefined).map(_.get))
    }).getOrElse(state.annotations)

    state.copy(circuit = newCircuit, renames = mainRename, annotations = newAnnotations)
  }
}
