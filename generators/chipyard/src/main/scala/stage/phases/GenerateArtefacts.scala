// See LICENSE

package chipyard.stage.phases

import firrtl.AnnotationSeq
import firrtl.options.{Dependency, Phase, PreservesAll, StageOptions}
import firrtl.options.Viewer.view
import chipyard.stage._
import freechips.rocketchip.util.{ElaborationArtefacts}

/** Writes [[ElaborationArtefacts]] into files */
class GenerateArtefacts extends Phase with PreservesAll[Phase] with HasChipyardStageUtils {

  override val prerequisites = Seq(Dependency[chipyard.stage.ChipyardChiselStage])

  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val targetDir = view[StageOptions](annotations).targetDir

    ElaborationArtefacts.files.foreach { case (extension, contents) =>
      writeOutputFile(targetDir, s"${view[ChipyardOptions](annotations).longName.get}.${extension}", contents ())
    }

    annotations
  }

}
