// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations.CircuitName
import firrtl.ir._
import firrtl.passes.Pass

// Converts some modules to external modules, based on a given function.  If
// that function returns "true" then the module is converted into an ExtModule,
// otherwise it's left alone.
class ConvertToExtModPass(classify: (Module) => Boolean) extends Pass {
  def run(c: Circuit): Circuit = {
    val modulesx = c.modules.map {
      case m: ExtModule => m
      case m: Module =>
        if (classify(m)) {
          new ExtModule(m.info, m.name, m.ports, m.name, Seq.empty)
        } else {
          m
        }
    }
    Circuit(c.info, modulesx, c.main)
  }
}
class ConvertToExtMod(classify: (Module) => Boolean) extends Transform with SeqTransformBased {
  def inputForm = HighForm
  def outputForm = HighForm
  def transforms = Seq(new ConvertToExtModPass(classify))

  def execute(state: CircuitState): CircuitState = {
    val ret = runTransforms(state)
    CircuitState(ret.circuit, outputForm, ret.annotations, ret.renames)
  }
}
