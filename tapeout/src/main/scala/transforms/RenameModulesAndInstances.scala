// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass

// This doesn't rename ExtModules under the assumption that they're some
// Verilog black box and therefore can't be renamed.  Since the point is to
// allow FIRRTL to be linked together using "cat" and ExtModules don't get
// emitted, this should be safe.
class RenameModulesAndInstancesPass(rename: (String) => String) extends Pass {
  def name = "Rename Modules and Instances"

  def renameInstances(body: Statement): Statement = {
    body match {
      case m: DefInstance => new DefInstance(m.info, m.name, rename(m.module))
      case m: WDefInstance => new WDefInstance(m.info, m.name, rename(m.module), m.tpe)
      case b: Block => new Block( b.stmts map { s => renameInstances(s) } )
      case s: Statement => s
    }
  }

  def run(c: Circuit): Circuit = {
    val modulesx = c.modules.map {
      case m: ExtModule => m
      case m: Module => new Module(m.info, rename(m.name), m.ports, renameInstances(m.body))
    }
    Circuit(c.info, modulesx, c.main)
  }
}

class RenameModulesAndInstances(rename: (String) => String) extends Transform with PassBased {
  def inputForm = LowForm
  def outputForm = LowForm
  def passSeq = Seq(new RenameModulesAndInstancesPass(rename))

  def execute(state: CircuitState): CircuitState = {
    state.copy(circuit = runPasses(state.circuit))
  }
}
