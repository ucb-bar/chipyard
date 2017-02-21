// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass

class EnumerateModulesPass(enumerate: (Module) => Unit) extends Pass {
  def name = "Enumurate Modules"

  def run(c: Circuit): Circuit = {
    val modulesx = c.modules.map {
      case m: ExtModule => m
      case m: Module => {
        enumerate(m)
        m
      }
    }
    Circuit(c.info, modulesx, c.main)
  }
}

class EnumerateModules(enumerate: (Module) => Unit) extends Transform with PassBased {
  def inputForm = LowForm
  def outputForm = LowForm
  def passSeq = Seq(new EnumerateModulesPass(enumerate))

  def execute(state: CircuitState): CircuitState = {
    CircuitState(runPasses(state.circuit), state.form)
  }
}
