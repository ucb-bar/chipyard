// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass
import firrtl.annotations._
import firrtl.transforms.DontTouchAnnotation

// Removes all the unused modules in a circuit by recursing through every
// instance (starting at the main module)
class RemoveUnusedModules extends Transform {
  def inputForm = HighForm
  def outputForm = HighForm

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
              case when: Conditionally =>
                someStatements(when.conseq) ++ someStatements(when.alt)
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

    state.circuit.modules.filterNot { usedModuleSet contains _.name } foreach { x => renames.record(ModuleTarget(state.circuit.main, x.name), Nil) }

    val newCircuit = Circuit(state.circuit.info, usedModuleSeq, state.circuit.main)
    state.copy(circuit = newCircuit, renames = Some(renames))
  }
}
