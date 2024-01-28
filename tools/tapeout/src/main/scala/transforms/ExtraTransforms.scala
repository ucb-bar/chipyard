// See LICENSE for license details.

package tapeout.transforms

import firrtl2.Mappers._
import firrtl2._
import firrtl2.annotations._
import firrtl2.ir._
import firrtl2.options._
import firrtl2.stage.Forms
import firrtl2.stage.TransformManager.TransformDependency
import firrtl2.options.{Dependency}

class ExtraLowTransforms extends Phase {//Transform with DependencyAPIMigration {
  // this PropagatePresetAnnotations is needed to run the RemoveValidIf pass (that is removed from CIRCT).
  // additionally, since that pass isn't explicitly a prereq of the LowFormEmitter it
  // needs to wrapped in this xform
  override def prerequisites: Seq[TransformDependency] = Forms.LowForm :+
    Dependency[firrtl2.transforms.PropagatePresetAnnotations]
  override def optionalPrerequisites:  Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Forms.LowEmitters
  override def invalidates(a: Transform): Boolean = false

  def execute(state: CircuitState): CircuitState = {
    state
  }
}
