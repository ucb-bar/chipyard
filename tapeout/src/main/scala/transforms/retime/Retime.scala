// See LICENSE for license details.

package barstools.tapeout.transforms.retime

import chisel3.internal.InstanceId
import firrtl.PrimOps.Not
import firrtl.annotations.{Annotation, CircuitName, ModuleName, Named, ComponentName}
import firrtl.ir.{Input, UIntType, IntWidth, Module, Port, DefNode, NoInfo, Reference, DoPrim, Block, Circuit}
import firrtl.passes.Pass
import firrtl.{CircuitForm, CircuitState, LowForm, Transform}

object RetimeAnnotation {
  def apply(target: ModuleName): Annotation = Annotation(target, classOf[RetimeTransform], "retime")
  def unapply(a: Annotation): Option[Named] = a match {
    case Annotation(m, t, "retime") if t == classOf[RetimeTransform] => Some(m)
    case _ => None
  }
}

class RetimeTransform extends Transform {
  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  override def execute(state: CircuitState): CircuitState = {
    getMyAnnotations(state) match {
      case Nil => state
      case seq => seq.foreach {
        case RetimeAnnotation(ModuleName(module, CircuitName(_))) =>
          logger.info(s"Retiming module $module")
        case RetimeAnnotation(ComponentName(name, ModuleName(module, CircuitName(_)))) =>
          logger.info(s"Retiming instance $module.$name")
        case _ =>
          throw new Exception(s"There should be RetimeAnnotations, got ${seq.mkString(" -- ")}")
      }
      state
    }
  }
}

trait RetimeLib {
  self: chisel3.Module =>
  def retime[T <: chisel3.experimental.LegacyModule](module: T): Unit = {
    chisel3.experimental.annotate(new chisel3.experimental.ChiselAnnotation{
      def toFirrtl: Annotation = RetimeAnnotation(module.toNamed)
    })
  }
}
