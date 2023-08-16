// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage.phases

import scala.collection.mutable

import firrtl.AnnotationSeq
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options.{Phase, PreservesAll, StageOptions, Unserializable, Dependency}
import firrtl.options.Viewer.view
import chipyard.stage._
import freechips.rocketchip.system.TestGeneration

trait MakefragSnippet { self: Annotation =>
  def toMakefrag: String
}

case class CustomMakefragSnippet(val toMakefrag: String) extends NoTargetAnnotation with MakefragSnippet with Unserializable

/** Generates a make script to run tests in [[RocketTestSuiteAnnotation]]. */
class GenerateTestSuiteMakefrags extends Phase with HasChipyardStageUtils {

  // Our annotations tend not to be serializable, but are not marked as such.
  override val prerequisites = Seq(Dependency[chipyard.stage.phases.GenerateFirrtlAnnos],
                                   Dependency[chipyard.stage.phases.AddDefaultTests])

  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val targetDir = view[StageOptions](annotations).targetDir
    val fileName = s"${view[ChipyardOptions](annotations).longName.get}.d"

    val makefragBuilder = new mutable.StringBuilder()
    val outputAnnotations = annotations.flatMap {
      case ChipyardTestSuiteAnnotation(tests) =>
        // Unfortunately the gen method of TestGeneration is rocketchip package
        // private, so we either have to copy code in or use the stateful form
        TestGeneration.addSuites(tests)
        None
      case a: MakefragSnippet =>
        makefragBuilder :+ ("\n" + a.toMakefrag)
        None
      case a => Some(a)
    }
    writeOutputFile(targetDir, fileName, TestGeneration.generateMakeFrag ++ makefragBuilder.toString)
    outputAnnotations
  }

  override final def invalidates(a: Phase): Boolean = false
}
