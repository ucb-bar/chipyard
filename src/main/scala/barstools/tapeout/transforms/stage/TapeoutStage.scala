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

case class HarnessOutputAnnotation(harnessOutput: String) extends NoTargetAnnotation with TapeoutOption

object HarnessOutputAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "harness-o",
      shortOption = Some("tho"),
      toAnnotationSeq = (s: String) => Seq(HarnessOutputAnnotation(s)),
      helpText = "use this to generate a harness at <harness-output>"
    )
  )
}

case class SynTopAnnotation(synTop: String) extends NoTargetAnnotation with TapeoutOption

object SynTopAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "syn-top",
      shortOption = Some("tst"),
      toAnnotationSeq = (s: String) => Seq(SynTopAnnotation(s)),
      helpText = "use this to set synTop"
    )
  )
}

case class TopFirAnnotation(topFir: String) extends NoTargetAnnotation with TapeoutOption

object TopFirAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "top-fir",
      shortOption = Some("tsf"),
      toAnnotationSeq = (s: String) => Seq(TopFirAnnotation(s)),
      helpText = "use this to set topFir"
    )
  )
}

case class TopAnnoOutAnnotation(topAnnoOut: String) extends NoTargetAnnotation with TapeoutOption

object TopAnnoOutAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "top-anno-out",
      shortOption = Some("tsaof"),
      toAnnotationSeq = (s: String) => Seq(TopAnnoOutAnnotation(s)),
      helpText = "use this to set topAnnoOut"
    )
  )
}

case class TopDotfOutAnnotation(topDotfOut: String) extends NoTargetAnnotation with TapeoutOption

object TopDotfOutAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "top-dotf-out",
      shortOption = Some("tdf"),
      toAnnotationSeq = (s: String) => Seq(TopDotfOutAnnotation(s)),
      helpText = "use this to set the filename for the top resource .f file"
    )
  )
}

case class HarnessTopAnnotation(harnessTop: String) extends NoTargetAnnotation with TapeoutOption

object HarnessTopAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "harness-top",
      shortOption = Some("tht"),
      toAnnotationSeq = (s: String) => Seq(HarnessTopAnnotation(s)),
      helpText = "use this to set harnessTop"
    )
  )
}

case class HarnessFirAnnotation(harnessFir: String) extends NoTargetAnnotation with TapeoutOption

object HarnessFirAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "harness-fir",
      shortOption = Some("thf"),
      toAnnotationSeq = (s: String) => Seq(HarnessFirAnnotation(s)),
      helpText = "use this to set harnessFir"
    )
  )
}

case class HarnessAnnoOutAnnotation(harnessAnnoOut: String) extends NoTargetAnnotation with TapeoutOption

object HarnessAnnoOutAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "harness-anno-out",
      shortOption = Some("thaof"),
      toAnnotationSeq = (s: String) => Seq(HarnessAnnoOutAnnotation(s)),
      helpText = "use this to set harnessAnnoOut"
    )
  )
}

case class HarnessDotfOutAnnotation(harnessDotfOut: String) extends NoTargetAnnotation with TapeoutOption

object HarnessDotfOutAnnotation extends HasShellOptions {
  val options: Seq[ShellOption[_]] = Seq(
    new ShellOption[String](
      longOption = "harness-dotf-out",
      shortOption = Some("hdf"),
      toAnnotationSeq = (s: String) => Seq(HarnessDotfOutAnnotation(s)),
      helpText = "use this to set the filename for the harness resource .f file"
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
    HarnessOutputAnnotation,
    SynTopAnnotation,
    TopFirAnnotation,
    TopAnnoOutAnnotation,
    TopDotfOutAnnotation,
    HarnessTopAnnotation,
    HarnessFirAnnotation,
    HarnessAnnoOutAnnotation,
    HarnessDotfOutAnnotation,
    HarnessConfAnnotation
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

