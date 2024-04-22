// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage.phases

import scala.util.Try
import scala.collection.mutable

import org.chipsalliance.cde.config.Parameters
import chisel3.stage.phases.Elaborate
import firrtl.AnnotationSeq
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options._
import firrtl.options.Viewer._
import freechips.rocketchip.system.{RocketTestSuite, TestGeneration}
import freechips.rocketchip.subsystem.{TilesLocated, InSubsystem}
import freechips.rocketchip.tile.XLen

import chipyard.TestSuiteHelper
import chipyard.TestSuitesKey
import chipyard.stage._

/** Annotation that contains a list of [[RocketTestSuite]]s to run */
case class ChipyardTestSuiteAnnotation(tests: Seq[RocketTestSuite]) extends NoTargetAnnotation with Unserializable


class AddDefaultTests extends Phase with PreservesAll[Phase] with HasChipyardStageUtils {
  override val prerequisites = Seq(Dependency[ChipyardChiselStage])
  override val dependents = Seq(Dependency[GenerateTestSuiteMakefrags])

  private def addTestSuiteAnnotations(implicit p: Parameters): Seq[Annotation] = {
    val annotations = mutable.ArrayBuffer[Annotation]()
    val suiteHelper = new TestSuiteHelper
    // Use Xlen as a proxy for detecting if we are a processor-like target
    // The underlying test suites expect this field to be defined
    val tileParams = p(TilesLocated(InSubsystem)) map (tp => tp.tileParams)
    if (p.lift(XLen).nonEmpty)
      // If a custom test suite is set up, use the custom test suite
      annotations += CustomMakefragSnippet(p(TestSuitesKey).apply(tileParams, suiteHelper, p))

    ChipyardTestSuiteAnnotation(suiteHelper.suites.values.toSeq) +: annotations.toSeq
  }


  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val (testSuiteAnnos, oAnnos) = annotations.partition {
      case ChipyardTestSuiteAnnotation(_)  => true
      case o => false
    }
    implicit val p = getConfig(view[ChipyardOptions](annotations).configNames.get).toInstance
    addTestSuiteAnnotations(p) ++ oAnnos
  }
}
