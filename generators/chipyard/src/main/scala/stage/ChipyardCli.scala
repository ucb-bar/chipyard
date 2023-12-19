// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import firrtl.options.Shell

trait ChipyardCli { this: Shell =>

  parser.note("Chipyard Generator Options")
  Seq(
    TopModuleAnnotation,
    ConfigsAnnotation,
    OutputBaseNameAnnotation,
    UnderscoreDelimitedConfigsAnnotation
  ).foreach(_.addOptions(parser))
}
