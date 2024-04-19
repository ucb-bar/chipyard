// See LICENSE for license details.

package barstools.tapeout.transforms.retime

import chisel3.experimental.RunFirrtlTransform
import firrtl.annotations._
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency
import firrtl.{CircuitState, DependencyAPIMigration, Transform}

case class RetimeAnnotation(target: Named) extends SingleTargetAnnotation[Named] {
  override def duplicate(n: Named): Annotation = RetimeAnnotation(n)
}

class RetimeTransform extends Transform with DependencyAPIMigration {

  override def prerequisites:          Seq[TransformDependency] = Forms.LowForm
  override def optionalPrerequisites:  Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Forms.LowEmitters
  override def invalidates(a: Transform): Boolean = false

  override def execute(state: CircuitState): CircuitState = {
    state.annotations.filter(_.isInstanceOf[RetimeAnnotation]) match {
      case Nil => state
      case seq =>
        seq.foreach {
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

  def retime[T <: chisel3.Module](module: T): Unit = {
    chisel3.experimental.annotate(new chisel3.experimental.ChiselAnnotation with RunFirrtlTransform {
      def transformClass: Class[_ <: Transform] = classOf[RetimeTransform]
      def toFirrtl:       Annotation = RetimeAnnotation(module.toNamed)
    })
  }
}
