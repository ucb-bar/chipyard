// See LICENSE for license details.

package tapeout.transforms.stage

import tapeout.transforms.GenerateModelStageMain
import chisel3.stage.ChiselCli
import firrtl2.stage.{RunFirrtlTransformAnnotation}
import firrtl2.AnnotationSeq
import firrtl2.annotations.{Annotation, NoTargetAnnotation}
import firrtl2.options.{HasShellOptions, Shell, ShellOption, Stage, Unserializable}
import firrtl2.stage.FirrtlCli
import logger.Logger

sealed trait TapeoutOption extends Unserializable {
  this: Annotation =>
}

case class OutAnnoAnnotation(outAnno: String) extends NoTargetAnnotation with TapeoutOption

object OutAnnoAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "out-anno-file",
      shortOption = Some("oaf"),
      toAnnotationSeq = (s: String) => Seq(OutAnnoAnnotation(s)),
      helpText = "out-anno-file"
    )
  )
}

trait TapeoutCli {
  this: Shell =>
  parser.note("Tapeout specific options")

  Seq(
    OutAnnoAnnotation
  ).foreach(_.addOptions(parser))
}

class TapeoutStage() extends Stage {
  override val shell: Shell = new Shell(applicationName = "tapeout") with TapeoutCli with ChiselCli with FirrtlCli

  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    Logger.makeScope(annotations) {
      val stageMain = new GenerateModelStageMain(annotations)
      stageMain.executeStageMain()
    }
    annotations
  }
}
