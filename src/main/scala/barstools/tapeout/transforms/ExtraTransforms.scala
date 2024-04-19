// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl.Mappers._
import firrtl._
import firrtl.annotations.{CircuitTarget, ModuleTarget, SingleTargetAnnotation}
import firrtl.ir._
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency
import firrtl.options.{Dependency}

class ExtraLowTransforms extends Transform with DependencyAPIMigration {
  // this PropagatePresetAnnotations is needed to run the RemoveValidIf pass (that is removed from CIRCT).
  // additionally, since that pass isn't explicitly a prereq of the LowFormEmitter it
  // needs to wrapped in this xform
  override def prerequisites: Seq[TransformDependency] = Forms.LowForm :+
    Dependency[firrtl.transforms.PropagatePresetAnnotations]
  override def optionalPrerequisites:  Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Forms.LowEmitters
  override def invalidates(a: Transform): Boolean = false

  def execute(state: CircuitState): CircuitState = {
    state
  }
}
