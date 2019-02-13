// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass
import firrtl.annotations.{SingleTargetAnnotation, Annotation}
import firrtl.transforms.DontTouchAnnotation

// Removes all the unused modules in a circuit by recursing through every
// instance (starting at the main module)
class RemoveUnusedModules extends Transform {
  def inputForm = MidForm
  def outputForm = MidForm

  def execute(state: CircuitState): CircuitState = {
    val modulesByName = state.circuit.modules.map{
      case m: Module => (m.name, Some(m))
      case m: ExtModule => (m.name, None)
    }.toMap

    def getUsedModules(om: Option[Module]): Set[String] = {
      om match {
        case Some(m) => {
          def someStatements(statement: Statement): Seq[Statement] =
            statement match {
              case b: Block => 
                b.stmts.map{ someStatements(_) }
                  .foldLeft(Seq[Statement]())(_ ++ _)
              case i: DefInstance => Seq(i)
              case w: WDefInstance => Seq(w)
              case _ => Seq()
            }

            someStatements(m.body).map{
              case s: DefInstance => Set(s.module) | getUsedModules(modulesByName(s.module))
              case s: WDefInstance => Set(s.module) | getUsedModules(modulesByName(s.module))
              case _ => Set[String]()
            }.foldLeft(Set(m.name))(_ | _)
          }

        case None => Set.empty[String]
      }
    }
    val usedModuleSet = getUsedModules(modulesByName(state.circuit.main))

    val usedModuleSeq = state.circuit.modules.filter { usedModuleSet contains _.name }
    val usedModuleNames = usedModuleSeq.map(_.name)

    val renames = state.renames.getOrElse(RenameMap())

    // This is what the annotation filter should look like, but for some reason it doesn't work.
    //state.circuit.modules.filterNot { usedModuleSet contains _.name } foreach { x => renames.record(ModuleTarget(state.circuit.main, x.name), Seq()) }

    val newCircuit = Circuit(state.circuit.info, usedModuleSeq, state.circuit.main)
    val newAnnos = AnnotationSeq(state.annotations.toSeq.filter { _ match {
      // XXX This is wrong, but it works for now
      // Tracked by https://github.com/ucb-bar/barstools/issues/36
      case x: DontTouchAnnotation => false
      //case x: DontTouchAnnotation => usedModuleNames contains x.target.module
      case _ => true
    }})

    CircuitState(newCircuit, outputForm, newAnnos, Some(renames))
  }
}
