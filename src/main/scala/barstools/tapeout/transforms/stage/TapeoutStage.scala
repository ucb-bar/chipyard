// See LICENSE for license details.

package barstools.tapeout.transforms.stage

import barstools.tapeout.transforms.GenerateTopAndHarness
import chisel3.stage.ChiselCli
import firrtl.AnnotationSeq
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options.{HasShellOptions, Shell, ShellOption, Stage, Unserializable}
import firrtl.stage.FirrtlCli
import logger.Logger

sealed trait TapeoutOption extends Unserializable {
  this: Annotation =>
}

case class OutFirAnnotation(outFir: String) extends NoTargetAnnotation with TapeoutOption

object OutFirAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "out-fir-file",
      shortOption = Some("off"),
      toAnnotationSeq = (s: String) => Seq(OutFirAnnotation(s)),
      helpText = "out-fir-file"
    )
  )
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
    OutAnnoAnnotation,
    OutFirAnnotation,
  ).foreach(_.addOptions(parser))
}

class TapeoutStage extends Stage {
  override val shell: Shell = new Shell(applicationName = "tapeout") with TapeoutCli with ChiselCli with FirrtlCli

  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    Logger.makeScope(annotations) {
      val generator = new GenerateTopAndHarness(annotations)

      generator.executeTop()
    }
    annotations
  }
}

