// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.Mappers._
import firrtl.annotations.{ModuleTarget, SingleTargetAnnotation, CircuitTarget}
import firrtl.stage.TransformManager.{TransformDependency}
import firrtl.stage.{Forms}

case class KeepNameAnnotation(target: ModuleTarget)
    extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget) = this.copy(n)
}

case class ModuleNameSuffixAnnotation(target: CircuitTarget, suffix: String)
    extends SingleTargetAnnotation[CircuitTarget] {
  def duplicate(n: CircuitTarget) = this.copy(target = n)
}

class AddSuffixToModuleNames extends Transform with DependencyAPIMigration {

  override def prerequisites: Seq[TransformDependency] = Forms.LowForm
  override def optionalPrerequisites: Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Forms.LowEmitters
  override def invalidates(a: Transform): Boolean = false

  def determineRenamerandAnnos(state: CircuitState): (AnnotationSeq, (String) => String) = {
    // remove determine suffix annotation
    val newAnnos = state.annotations.filterNot(_.isInstanceOf[ModuleNameSuffixAnnotation])
    val suffixes = state.annotations.collect({ case ModuleNameSuffixAnnotation(_, suffix) => suffix })
    require(suffixes.length <= 1)
    val suffix = suffixes.headOption.getOrElse("")

    // skip renaming ExtModules and top-level module
    val excludeSet = state.circuit.modules.flatMap {
      case e: ExtModule => Some(e.name)
      case m if (m.name == state.circuit.main) => Some(m.name)
      case _ => None
    }.toSet

    val renamer = { (name: String) => if (excludeSet(name)) name else name + suffix }

    (newAnnos, renamer)
  }

  def renameInstanceModules(renamer: (String) => String)(stmt: Statement): Statement = {
    stmt match {
      case m: DefInstance => new DefInstance(m.info, m.name, renamer(m.module))
      case s => s.map(renameInstanceModules(renamer)) // if is statement, recurse
    }
  }

  def run(state: CircuitState, renamer: (String) => String): (Circuit, RenameMap) = {
    val myRenames = RenameMap()
    val c = state.circuit
    val modulesx = c.modules.map {
      case m if (renamer(m.name) != m.name) =>
        myRenames.record(ModuleTarget(c.main, m.name), ModuleTarget(c.main, renamer(m.name)))
        m.map(renamer).map(renameInstanceModules(renamer))
      case m => m.map(renameInstanceModules(renamer))
    }
    (Circuit(c.info, modulesx, c.main), myRenames)
  }

  def execute(state: CircuitState): CircuitState = {
    val (newAnnos, renamer) = determineRenamerandAnnos(state)
    val (ret, renames) = run(state, renamer)
    state.copy(circuit = ret, annotations = newAnnos, renames = Some(renames))
  }
}
