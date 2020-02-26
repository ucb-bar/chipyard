// See LICENSE for license details.

package barstools.tapeout.transforms

import chisel3.internal.InstanceId
import firrtl.PrimOps.Not
import firrtl.annotations.{Annotation, CircuitName, ModuleName, Named}
import firrtl.ir.{Input, UIntType, IntWidth, Module, Port, DefNode, NoInfo, Reference, DoPrim, Block, Circuit}
import firrtl.passes.Pass
import firrtl.{CircuitForm, CircuitState, LowForm, Transform}

object ResetInverterAnnotation {
  def apply(target: ModuleName): Annotation = Annotation(target, classOf[ResetInverterTransform], "invert")
  def unapply(a: Annotation): Option[Named] = a match {
    case Annotation(m, t, "invert") if t == classOf[ResetInverterTransform] => Some(m)
    case _ => None
  }
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

class ResetInverterTransform extends Transform {
  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  override def execute(state: CircuitState): CircuitState = {
    getMyAnnotations(state) match {
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
    chisel3.experimental.annotate(new chisel3.experimental.ChiselAnnotation{
      def toFirrtl: Annotation = ResetInverterAnnotation(module.toNamed)
    })
  }
}
