package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations._
import firrtl.passes._
import firrtl.ir._

object WriteConfig {
  def apply(dir: String, file: String, contents: String): Unit = {
    val writer = new java.io.PrintWriter(new java.io.File(s"$dir/$file"))
    writer write contents
    writer.close()
  }
}

object GetTargetDir {
  def apply(state: CircuitState): String = {
    val annos = state.annotations.getOrElse(AnnotationMap(Seq.empty)).annotations
    val destDir = annos.map {
      case Annotation(f, t, s) if t == classOf[transforms.BlackBoxSourceHelper] =>
        transforms.BlackBoxSource.parse(s) match {
          case Some(transforms.BlackBoxTargetDir(dest)) => Some(dest)
          case _ => None
        }
      case _ => None
    }.flatten
    val loc = {
      if (destDir.isEmpty) "."
      else destDir.head
    }
    val targetDir = new java.io.File(loc)
    if(!targetDir.exists()) FileUtils.makeDirectory(targetDir.getAbsolutePath)
    loc
  }
}

// Fake transform just to track Technology information directory
object TechnologyLocation {
  def apply(dir: String): Annotation = {
    Annotation(CircuitName("All"), classOf[TechnologyLocation], dir)
  }
}
class TechnologyLocation extends Transform {
  def inputForm: CircuitForm = LowForm
  def outputForm: CircuitForm = LowForm
  def execute(state: CircuitState) = throw new Exception("Technology Location transform execution doesn't work!")
  def get(state: CircuitState): String = {
    val annos = state.annotations.getOrElse(AnnotationMap(Seq.empty)).annotations
    val dir = annos.map {
      case Annotation(f, t, s) if t == classOf[TechnologyLocation] => Some(s)
      case _ => None
    }.flatten
    dir.length match {
      case 0 => ""
      case 1 => 
        val targetDir = new java.io.File(dir.head)
        if(!targetDir.exists()) throw new Exception("Technology yaml directory doesn't exist!")
        dir.head
      case _ => throw new Exception("Only 1 tech directory annotation allowed!")
    }
  }
}



