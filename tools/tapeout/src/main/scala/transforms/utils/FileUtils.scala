// See LICENSE for license details.

package barstools.tapeout.transforms.utils

import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl._
import firrtl.annotations._
import firrtl.stage.Forms
import firrtl.stage.TransformManager.TransformDependency
import firrtl.transforms.BlackBoxTargetDirAnno

object WriteConfig {
  def apply(dir: String, file: String, contents: String): Unit = {
    val writer = new java.io.PrintWriter(new java.io.File(s"$dir/$file"))
    writer.write(contents)
    writer.close()
  }
}

object GetTargetDir {
  def apply(state: CircuitState): String = {
    val annos = state.annotations
    val destDir = annos.map {
      case BlackBoxTargetDirAnno(s) => Some(s)
      case _                        => None
    }.flatten
    val loc = {
      if (destDir.isEmpty) "."
      else destDir.head
    }
    val targetDir = new java.io.File(loc)
    if (!targetDir.exists()) FileUtils.makeDirectory(targetDir.getAbsolutePath)
    loc
  }
}

trait HasSetTechnologyLocation {
  self: chisel3.Module =>

  def setTechnologyLocation(dir: String) {
    annotate(new ChiselAnnotation {
      override def toFirrtl: Annotation = {
        TechnologyLocationAnnotation(dir)
      }
    })
  }
}

case class TechnologyLocationAnnotation(dir: String) extends SingleTargetAnnotation[CircuitName] {
  val target: CircuitName = CircuitName("All")
  override def duplicate(n: CircuitName): Annotation = TechnologyLocationAnnotation(dir)
}

class TechnologyLocation extends Transform with DependencyAPIMigration {

  override def prerequisites:          Seq[TransformDependency] = Forms.LowForm
  override def optionalPrerequisites:  Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Forms.LowEmitters

  def execute(state: CircuitState): CircuitState = {
    throw new Exception("Technology Location transform execution doesn't work!")
  }

  def get(state: CircuitState): String = {
    val annos = state.annotations
    val dir = annos.flatMap {
      case TechnologyLocationAnnotation(dir) => Some(dir)
      case _                                 => None
    }
    dir.length match {
      case 0 => ""
      case 1 =>
        val targetDir = new java.io.File(dir.head)
        if (!targetDir.exists()) throw new Exception(s"Technology yaml directory $targetDir doesn't exist!")
        dir.head
      case _ => throw new Exception("Only 1 tech directory annotation allowed!")
    }
  }
}
