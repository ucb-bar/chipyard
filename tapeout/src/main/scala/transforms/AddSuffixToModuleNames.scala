// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.Mappers._


case class KeepNameAnnotation(target: ModuleTarget)
    extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget) = this.copy(n)
}

case class ModuleNameSuffixAnnotation(target: CircuitTarget, suffix: String)
    extends SingleTargetAnnotation[CircuitTarget] {
  def duplicate(n: CircuitTarget) = this.copy(target = n)
}

// This doesn't rename ExtModules under the assumption that they're some
// Verilog black box and therefore can't be renamed.  Since the point is to
// allow FIRRTL to be linked together using "cat" and ExtModules don't get
// emitted, this should be safe.
class AddSuffixToModuleNames extends Transform {
  def inputForm = LowForm
  def outputForm = LowForm

  def processAnnos(annos: AnnotationSeq): (AnnotationSeq, (String) => String) = {
    val whitelist = annos.collect({ case KeepNameAnnotation(tgt) => tgt.module }).toSet
    val newAnnos = annos.filterNot(_.isInstanceOf[ModuleNameSuffixAnnotation])
    val suffixes = annos.collect({ case ModuleNameSuffixAnnotation(_, suffix) => suffix })
    require(suffixes.length <= 1)

    val suffix = suffixes.headOption.getOrElse("")
    val renamer = { name: String => if (whitelist(name)) name else name + suffix }
    (newAnnos, renamer)
  }

  def renameInstanceModules(renamer: (String) => String)(stmt: Statement): Statement = {
    stmt match {
      case m: DefInstance => new DefInstance(m.info, m.name, renamer(m.module))
      case m: WDefInstance => new WDefInstance(m.info, m.name, renamer(m.module), m.tpe)
      case s => s map renameInstanceModules(renamer)
    }
  }

  def run(state: CircuitState, renamer: (String) => String): (Circuit, RenameMap) = {
    val myRenames = RenameMap()
    val c = state.circuit
    val modulesx = c.modules.map {
      case m if (renamer(m.name) != m.name) =>
        myRenames.record(ModuleTarget(c.main, m.name), ModuleTarget(c.main, renamer(m.name)))
        m.map(renamer).map(renameInstanceModules(renamer))
      case m => m.map(renameInstanceModules(renamer))
    }
    (Circuit(c.info, modulesx, c.main), myRenames)
  }

  def execute(state: CircuitState): CircuitState = {
    val (newAnnos, renamer) = processAnnos(state.annotations)
    val (ret, renames) = run(state, renamer)
    state.copy(circuit = ret, annotations = newAnnos, renames = Some(renames))
  }
}
