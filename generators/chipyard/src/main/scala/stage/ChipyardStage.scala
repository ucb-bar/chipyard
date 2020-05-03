// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import chisel3.stage.{ChiselCli, ChiselStage}
import firrtl.options.PhaseManager.PhaseDependency
import firrtl.options.{Phase, PreservesAll, Shell}
import firrtl.stage.FirrtlCli
import freechips.rocketchip.stage.RocketChipCli
import freechips.rocketchip.system.RocketChipStage

import firrtl.options.{Phase, PhaseManager, PreservesAll, Shell, Stage, StageError, StageMain}
import firrtl.options.phases.DeletedWrapper

class ChipyardStage extends ChiselStage with PreservesAll[Phase] {
  override val shell = new Shell("chipyard") with ChipyardCli with RocketChipCli with ChiselCli with FirrtlCli
  override val targets: Seq[PhaseDependency] = Seq(
    classOf[freechips.rocketchip.stage.phases.Checks],
    classOf[freechips.rocketchip.stage.phases.TransformAnnotations],
    classOf[freechips.rocketchip.stage.phases.PreElaboration],
    classOf[chisel3.stage.phases.Checks],
    classOf[chisel3.stage.phases.Elaborate],
    classOf[freechips.rocketchip.stage.phases.GenerateROMs],
    classOf[chisel3.stage.phases.AddImplicitOutputFile],
    classOf[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
    classOf[chisel3.stage.phases.MaybeAspectPhase],
    classOf[chisel3.stage.phases.Emitter],
    classOf[chisel3.stage.phases.Convert],
    classOf[freechips.rocketchip.stage.phases.GenerateFirrtlAnnos],
    classOf[freechips.rocketchip.stage.phases.AddDefaultTests],
    classOf[chipyard.stage.phases.AddDefaultTests],
    classOf[chipyard.stage.phases.GenerateTestSuiteMakefrags],
    classOf[freechips.rocketchip.stage.phases.GenerateArtefacts],
  )
}
