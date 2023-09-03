// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import chisel3.experimental.BaseModule
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options.{HasShellOptions, ShellOption, Unserializable}

trait ChipyardOption extends Unserializable { this: Annotation => }

/** This hijacks the existing ConfigAnnotation to accept the legacy _-delimited format  */
private[stage] object UnderscoreDelimitedConfigsAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "legacy-configs",
      toAnnotationSeq = a => {
        val split = a.split(':')
        assert(split.length == 2, s"'${a}' split by ':' doesn't yield two things")
        val packageName = split.head
        val configs     = split.last.split("_")
        Seq(new ConfigsAnnotation(configs map { config => if (config contains ".") s"${config}" else s"${packageName}.${config}" } ))
      },
      helpText = "A string of underscore-delimited configs (configs have decreasing precendence from left to right).",
      shortOption = Some("LC")
    )
  )
}

/** Paths to config classes */
case class ConfigsAnnotation(configNames: Seq[String]) extends NoTargetAnnotation with ChipyardOption
private[stage] object ConfigsAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[Seq[String]](
      longOption = "configs",
      toAnnotationSeq = a => Seq(ConfigsAnnotation(a)),
      helpText = "<comma-delimited configs>",
      shortOption = Some("C")
    )
  )
}

case class TopModuleAnnotation(clazz: Class[_ <: Any]) extends NoTargetAnnotation with ChipyardOption
private[stage] object TopModuleAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "top-module",
      toAnnotationSeq = a => Seq(TopModuleAnnotation(Class.forName(a).asInstanceOf[Class[_ <: BaseModule]])),
      helpText = "<top module>",
      shortOption = Some("T")
    )
  )
}

/** Optional base name for generated files' filenames */
case class OutputBaseNameAnnotation(outputBaseName: String) extends NoTargetAnnotation with ChipyardOption
private[stage] object OutputBaseNameAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "name",
      toAnnotationSeq = a => Seq(OutputBaseNameAnnotation(a)),
      helpText = "<base name of output files>",
      shortOption = Some("n")
    )
  )
}
