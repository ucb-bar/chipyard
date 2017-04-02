// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.passes.Pass

class EnumerateModulesPass(enumerate: (Module) => Unit) extends Pass {

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

class EnumerateModules(enumerate: (Module) => Unit) extends Transform with SeqTransformBased {
  def inputForm = LowForm
  def outputForm = LowForm
  def transforms = Seq(new EnumerateModulesPass(enumerate))

  def execute(state: CircuitState): CircuitState = {
    val ret = runTransforms(state)
    CircuitState(ret.circuit, outputForm, ret.annotations, ret.renames)
  }
}
