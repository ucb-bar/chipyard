// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations.{ModuleTarget, ReferenceTarget, SingleTargetAnnotation}
import firrtl.ir._
import firrtl.options.Dependency
import firrtl.passes.memlib.ReplSeqMem
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency

case class ConvertToExtModAnnotation(target: ModuleTarget)
    extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget) = this.copy(n)
}

// Converts some modules to external modules, based on a given function.  If
// that function returns "true" then the module is converted into an ExtModule,
// otherwise it's left alone.
class ConvertToExtMod extends Transform with DependencyAPIMigration {

  override def prerequisites: Seq[TransformDependency] = Forms.HighForm
  override def optionalPrerequisites: Seq[TransformDependency] = Seq.empty
  override def optionalPrerequisiteOf: Seq[TransformDependency] = {
    Forms.HighEmitters ++ Seq(Dependency[RemoveUnusedModules], Dependency[ReplSeqMem])
  }
  override def invalidates(a: Transform): Boolean = false

  def run(state: CircuitState, makeExt: Set[String]): (Circuit, RenameMap) = {
    val renames = RenameMap()
    val c = state.circuit
    renames.setCircuit(c.main)
    val modulesx = c.modules.map {
      case m: ExtModule => m
      case m: Module =>
        val removing = collection.mutable.HashSet[String]()
        def findDeadNames(statement: Statement): Unit = {
          statement match {
            case hn: IsDeclaration => removing += hn.name
            case x => x.foreachStmt(findDeadNames)
          }
        }
        if (makeExt(m.name)) {
          m.foreachStmt(findDeadNames)
          removing.foreach { name =>
            renames.record(ReferenceTarget(c.main, m.name, Nil, name, Nil), Nil)
          }
          new ExtModule(m.info, m.name, m.ports, m.name, Seq.empty)
        } else {
          m
        }
    }
    (Circuit(c.info, modulesx, c.main), renames)
  }

  def execute(state: CircuitState): CircuitState = {
    val makeExt = state.annotations.collect({ case ConvertToExtModAnnotation(tgt) => tgt.module }).toSet
    val newAnnos = state.annotations.filterNot(_.isInstanceOf[ConvertToExtModAnnotation])
    val (ret, renames) = run(state, makeExt)
    state.copy(circuit = ret, annotations = newAnnos, renames = Some(renames))
  }
}
