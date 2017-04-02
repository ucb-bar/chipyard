// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass

// Removes all the unused modules in a circuit by recursing through every
// instance (starting at the main module)
class RemoveUnusedModulesPass extends Pass {

  def run(c: Circuit): Circuit = {
    val modulesByName = c.modules.map{
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
    val usedModuleSet = getUsedModules(modulesByName(c.main))

    val usedModuleSeq = c.modules.filter { usedModuleSet contains _.name }

    Circuit(c.info, usedModuleSeq, c.main)
  }
}

class RemoveUnusedModules extends Transform with SeqTransformBased {
  def inputForm = MidForm
  def outputForm = MidForm
  def transforms = Seq(new RemoveUnusedModulesPass)

  def execute(state: CircuitState): CircuitState = {
    val ret = runTransforms(state)
    CircuitState(ret.circuit, outputForm, ret.annotations, ret.renames)
  }
}
