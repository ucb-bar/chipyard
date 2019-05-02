// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations._
import firrtl.ir._
import firrtl.passes.Pass

// This doesn't rename ExtModules under the assumption that they're some
// Verilog black box and therefore can't be renamed.  Since the point is to
// allow FIRRTL to be linked together using "cat" and ExtModules don't get
// emitted, this should be safe.
class RenameModulesAndInstances(rename: (String) => String) extends Transform {
  def inputForm = LowForm
  def outputForm = LowForm

  def renameInstances(body: Statement): Statement = {
    body match {
      case m: DefInstance => new DefInstance(m.info, m.name, rename(m.module))
      case m: WDefInstance => new WDefInstance(m.info, m.name, rename(m.module), m.tpe)
      case b: Block => new Block( b.stmts map { s => renameInstances(s) } )
      case s: Statement => s
    }
  }

  def run(state: CircuitState): (Circuit, RenameMap) = {
    val myRenames = RenameMap()
    val c = state.circuit
    val modulesx = c.modules.map {
      case m: ExtModule =>
        myRenames.record(ModuleTarget(c.main, m.name), ModuleTarget(c.main, rename(m.name)))
        m.copy(name = rename(m.name))
      case m: Module =>
        myRenames.record(ModuleTarget(c.main, m.name), ModuleTarget(c.main, rename(m.name)))
        new Module(m.info, rename(m.name), m.ports, renameInstances(m.body))
    }
    (Circuit(c.info, modulesx, c.main), myRenames)
  }

  def execute(state: CircuitState): CircuitState = {
    val (ret, renames) = run(state)
    state.copy(circuit = ret, renames = Some(renames))
  }
}
