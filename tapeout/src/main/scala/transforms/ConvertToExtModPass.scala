// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations._
import firrtl.ir._
import firrtl.passes.Pass

// Converts some modules to external modules, based on a given function.  If
// that function returns "true" then the module is converted into an ExtModule,
// otherwise it's left alone.
class ConvertToExtMod(classify: (Module) => Boolean) extends Transform {
  def inputForm = HighForm
  def outputForm = HighForm


  def run(state: CircuitState): (Circuit, RenameMap) = {

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
        if (classify(m)) {
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
    val (ret, renames) = run(state)
    state.copy(circuit = ret, renames = Some(renames))
  }
}
