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

case class HarnessConfAnnotation(harnessConf: String) extends NoTargetAnnotation with TapeoutOption

object HarnessConfAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "harness-conf",
      shortOption = Some("thconf"),
      toAnnotationSeq = (s: String) => Seq(HarnessConfAnnotation(s)),
      helpText = "use this to set the harness conf file location"
    )
  )
}

trait TapeoutCli {
  this: Shell =>
  parser.note("Tapeout specific options")

  Seq(
    OutAnnoAnnotation,
    HarnessConfAnnotation,
  ).foreach(_.addOptions(parser))
}

class TapeoutStage(doHarness: Boolean) extends Stage {
  override val shell: Shell = new Shell(applicationName = "tapeout") with TapeoutCli with ChiselCli with FirrtlCli

  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    Logger.makeScope(annotations) {
      val generator = new GenerateTopAndHarness(annotations)

      if (doHarness) {
        generator.executeTopAndHarness()
      } else {
        generator.executeTop()
      }
    }
    annotations
  }
}

