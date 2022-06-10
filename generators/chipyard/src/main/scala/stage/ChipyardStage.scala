// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import chisel3.stage.{ChiselCli, ChiselStage}
import firrtl.options.PhaseManager.PhaseDependency
import firrtl.options.{Phase, PreservesAll, Shell}
import firrtl.stage.FirrtlCli
import freechips.rocketchip.stage.RocketChipCli
import freechips.rocketchip.system.RocketChipStage

import firrtl.options.{Phase, PhaseManager, PreservesAll, Shell, Stage, StageError, StageMain, Dependency}
import firrtl.options.phases.DeletedWrapper

class ChipyardStage extends ChiselStage {
  override val shell = new Shell("chipyard") with ChipyardCli with RocketChipCli with ChiselCli with FirrtlCli
  override val targets: Seq[PhaseDependency] = Seq(
    Dependency[freechips.rocketchip.stage.phases.Checks],
    Dependency[freechips.rocketchip.stage.phases.TransformAnnotations],
    Dependency[freechips.rocketchip.stage.phases.PreElaboration],
    // Note: Dependency[RocketChiselStage] is not listed here because it is
    // package private, however it is named as a prereq for the passes below.
    Dependency[freechips.rocketchip.stage.phases.GenerateFirrtlAnnos],
    Dependency[freechips.rocketchip.stage.phases.AddDefaultTests],
    Dependency[chipyard.stage.phases.AddDefaultTests],
    Dependency[chipyard.stage.phases.GenerateTestSuiteMakefrags],
    Dependency[freechips.rocketchip.stage.phases.GenerateArtefacts],
  )
  override final def invalidates(a: Phase): Boolean = false
}
