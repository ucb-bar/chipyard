// See LICENSE for license details.

package barstools.tapeout.transforms.stage

import barstools.tapeout.transforms.GenerateModelStageMain
import chisel3.stage.ChiselCli
import firrtl.stage.{RunFirrtlTransformAnnotation}
import firrtl.AnnotationSeq
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options.{HasShellOptions, Shell, ShellOption, Stage, Unserializable}
import firrtl.stage.FirrtlCli
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

case class CompilerNameAnnotation(name: String) extends NoTargetAnnotation with TapeoutOption

// duplicate of firrtl.stage.CompilerAnnotation but needed so that you can have a
// CompilerAnnotation to match on when adding new transforms
object DuplicateCompilerAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "duplicate-compiler",
      shortOption = Some("DX"),
      toAnnotationSeq = (s: String) => {
        Seq(
          CompilerNameAnnotation(s))
      },
      helpText = "duplicate-compiler",
      helpValueName = Some("same as --compiler FIRRTL flag")
    )
  )
}

trait TapeoutCli {
  this: Shell =>
  parser.note("Tapeout specific options")

  Seq(
    OutAnnoAnnotation,
    DuplicateCompilerAnnotation
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
