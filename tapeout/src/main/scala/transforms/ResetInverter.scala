// See LICENSE for license details.

package barstools.tapeout.transforms

import chisel3.experimental.RunFirrtlTransform
import firrtl.PrimOps.Not
import firrtl.annotations.{Annotation, CircuitName, ModuleName, SingleTargetAnnotation}
import firrtl.ir._
import firrtl.passes.Pass
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency
import firrtl.{CircuitState, DependencyAPIMigration, Transform}

case class ResetInverterAnnotation(target: ModuleName) extends SingleTargetAnnotation[ModuleName] {
  override def duplicate(n: ModuleName): Annotation = ResetInverterAnnotation(n)
}

object ResetN extends Pass {
  private val Bool = UIntType(IntWidth(1))
  // Only works on Modules with a Bool port named reset
  def invertReset(mod: Module): Module = {
    // Check that it actually has reset
    require(mod.ports.exists(p => p.name == "reset" && p.tpe == Bool),
      "Can only invert reset on a module with reset!")
    // Rename "reset" to "reset_n"
    val portsx = mod.ports map {
      case Port(info, "reset", Input, Bool) => Port(info, "reset_n", Input, Bool)
      case other => other
    }
    val newReset = DefNode(NoInfo, "reset", DoPrim(Not, Seq(Reference("reset_n", Bool)), Seq.empty, Bool))
    val bodyx = Block(Seq(newReset, mod.body))
    mod.copy(ports = portsx, body = bodyx)
  }

  def run(c: Circuit): Circuit = {
    c.copy(modules = c.modules map {
      case mod: Module if mod.name == c.main => invertReset(mod)
      case other => other
    })
  }
}

class ResetInverterTransform extends Transform with DependencyAPIMigration {

  override def prerequisites: Seq[TransformDependency] = Forms.LowForm
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Forms.LowEmitters

  override def execute(state: CircuitState): CircuitState = {
    state.annotations.filter(_.isInstanceOf[ResetInverterAnnotation]) match {
      case Nil => state
      case Seq(ResetInverterAnnotation(ModuleName(state.circuit.main, CircuitName(_)))) =>
        state.copy(circuit = ResetN.run(state.circuit))
      case annotations =>
        throw new Exception(s"There should be only one InvertReset annotation: got ${annotations.mkString(" -- ")}")
    }
  }
}

trait ResetInverter {
  self: chisel3.Module =>
  def invert[T <: chisel3.internal.LegacyModule](module: T): Unit = {
    chisel3.experimental.annotate(new chisel3.experimental.ChiselAnnotation with RunFirrtlTransform {
      def transformClass: Class[_ <: Transform] = classOf[ResetInverterTransform]
      def toFirrtl: Annotation = ResetInverterAnnotation(module.toNamed)
    })
  }
}
