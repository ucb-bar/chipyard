// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import chisel3.stage.{ChiselCli, ChiselStage}
import firrtl.options.PhaseManager.PhaseDependency
import firrtl.options.{Phase, PreservesAll, Shell}
import firrtl.stage.FirrtlCli

import firrtl.options.{Phase, PhaseManager, PreservesAll, Shell, Stage, StageError, StageMain, Dependency}
import firrtl.options.phases.DeletedWrapper

final class ChipyardChiselStage extends ChiselStage {

  override val targets = Seq(
    Dependency[chisel3.stage.phases.Checks],
    Dependency[chisel3.stage.phases.Elaborate],
    Dependency[chisel3.stage.phases.AddImplicitOutputFile],
    Dependency[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
    Dependency[chisel3.stage.phases.MaybeAspectPhase],
    Dependency[chisel3.stage.phases.Emitter],
    Dependency[chisel3.stage.phases.Convert]
  )

}

class ChipyardStage extends ChiselStage {
  override val shell = new Shell("chipyard") with ChipyardCli with ChiselCli with FirrtlCli
  override val targets: Seq[PhaseDependency] = Seq(
    Dependency[chipyard.stage.phases.Checks],
    Dependency[chipyard.stage.phases.TransformAnnotations],
    Dependency[chipyard.stage.phases.PreElaboration],
    Dependency[ChipyardChiselStage],
    Dependency[chipyard.stage.phases.GenerateFirrtlAnnos],
    Dependency[chipyard.stage.phases.AddDefaultTests],
    Dependency[chipyard.stage.phases.GenerateTestSuiteMakefrags],
    Dependency[chipyard.stage.phases.GenerateArtefacts],
  )
  override final def invalidates(a: Phase): Boolean = false
}
