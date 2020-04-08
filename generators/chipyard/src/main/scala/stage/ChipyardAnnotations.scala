// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import freechips.rocketchip.stage.ConfigsAnnotation
import firrtl.options.{HasShellOptions, ShellOption}

/** This hijacks the existing ConfigAnnotation to accept the legacy _-delimited format  */
private[stage] object UnderscoreDelimitedConfigsAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "legacy-configs",
      toAnnotationSeq = a => Seq(new ConfigsAnnotation(a.split("_"))),
      helpText = "A string of underscore-delimited configs (configs have decreasing precendence from left to right).",
      shortOption = Some("LC")
    )
  )
}

