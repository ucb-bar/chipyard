// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage.phases

import scala.util.Try
import scala.collection.mutable

import chipsalliance.rocketchip.config.Parameters
import chisel3.stage.phases.Elaborate
import firrtl.AnnotationSeq
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options.{Phase, PreservesAll, Dependency}
import firrtl.options.Viewer.view
import freechips.rocketchip.stage.RocketChipOptions
import freechips.rocketchip.stage.phases.{RocketTestSuiteAnnotation}
import freechips.rocketchip.system.{RocketTestSuite, TestGeneration}
import freechips.rocketchip.util.HasRocketChipStageUtils
import freechips.rocketchip.tile.XLen

import chipyard.{TestSuiteHelper, CoreManager}

class AddDefaultTests extends Phase with PreservesAll[Phase] with HasRocketChipStageUtils {
  // Make sure we run both after RocketChip's version of this phase, and Rocket Chip's annotation emission phase
  // because the RocketTestSuiteAnnotation is not serializable (but is not marked as such). 
  override val prerequisites = Seq(
    Dependency[freechips.rocketchip.stage.phases.GenerateFirrtlAnnos],
    Dependency[freechips.rocketchip.stage.phases.AddDefaultTests])
  override val dependents = Seq(Dependency[freechips.rocketchip.stage.phases.GenerateTestSuiteMakefrags])

  private def addTestSuiteAnnotations(implicit p: Parameters): Seq[Annotation] = {
    val annotations = mutable.ArrayBuffer[Annotation]()
    val suiteHelper = new TestSuiteHelper
    // Use Xlen as a proxy for detecting if we are a processor-like target
    // The underlying test suites expect this field to be defined
    if (p.lift(XLen).nonEmpty) {
      suiteHelper.addRocketTestSuites
      suiteHelper.addBoomTestSuites
      CoreManager.cores map (core => suiteHelper.addThirdPartyTestSuites(core.tileParamsLookup))
    }

    // if hwacha parameter exists then generate its tests
    // TODO: find a more elegant way to do this. either through
    // trying to disambiguate BuildRoCC, having a AccelParamsKey,
    // or having the Accelerator/Tile add its own tests
    import hwacha.HwachaTestSuites._
    if (Try(p(hwacha.HwachaNLanes)).getOrElse(0) > 0) {
      suiteHelper.addSuites(rv64uv.map(_("p")))
      suiteHelper.addSuites(rv64uv.map(_("vp")))
      suiteHelper.addSuite(rv64sv("p"))
      suiteHelper.addSuite(hwachaBmarks)
      annotations += CustomMakefragSnippet(
        "SRC_EXTENSION = $(base_dir)/hwacha/$(src_path)/*.scala" + "\nDISASM_EXTENSION = --extension=hwacha")
    }
    RocketTestSuiteAnnotation(suiteHelper.suites.values.toSeq) +: annotations
  }


  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val (testSuiteAnnos, oAnnos) = annotations.partition {
      case RocketTestSuiteAnnotation(_)  => true
      case o => false
    }
    implicit val p = getConfig(view[RocketChipOptions](annotations).configNames.get).toInstance
    addTestSuiteAnnotations ++ oAnnos
  }
}
