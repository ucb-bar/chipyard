// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import circt.stage.{ChiselStage, CIRCTTargetAnnotation, CIRCTTarget}
import firrtl.options.{Shell}
import firrtl.options.Viewer.view
import firrtl.{AnnotationSeq}
import firrtl.options.{Phase, PhaseManager, Shell, Dependency}

final class ChipyardChiselStage extends ChiselStage {
  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    val pm = new PhaseManager(
      targets = Seq(
        Dependency[chisel3.stage.phases.Checks],
        Dependency[chisel3.stage.phases.AddImplicitOutputFile],
        Dependency[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
        Dependency[chisel3.stage.phases.MaybeAspectPhase],
        Dependency[chisel3.stage.phases.AddSerializationAnnotations],
        Dependency[chisel3.stage.phases.Convert],
        Dependency[chisel3.stage.phases.AddDedupGroupAnnotations],
        Dependency[chisel3.stage.phases.MaybeInjectingPhase],
        Dependency[circt.stage.phases.AddImplicitOutputFile],
        Dependency[circt.stage.phases.Checks],
        Dependency[circt.stage.phases.CIRCT]
      ),
      currentState = Seq(
        Dependency[firrtl.stage.phases.AddDefaults],
        Dependency[firrtl.stage.phases.Checks]
      )
    )
    pm.transform(annotations :+ CIRCTTargetAnnotation(CIRCTTarget.CHIRRTL))
  }
}

class ChipyardStage extends ChiselStage {
  override val shell = new Shell("chipyard") with ChipyardCli with circt.stage.CLI {
    // These are added by firrtl.options.Shell (which we must extend because we are a Stage)
    override protected def includeLoggerOptions = false
  }
  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    val enableSFCFIRRTLEmissionPasses = if (view[ChipyardOptions](annotations).enableSFCFIRRTLEmission) {
      Seq(Dependency[chipyard.stage.phases.LegacyFirrtl2Emission])
    } else {
      Seq.empty
    }
    val pm = new PhaseManager(
      targets = Seq(
        Dependency[chipyard.stage.phases.Checks],
        Dependency[chipyard.stage.phases.TransformAnnotations],
        Dependency[chipyard.stage.phases.PreElaboration],
        Dependency[ChipyardChiselStage],
        Dependency[chipyard.stage.phases.GenerateFirrtlAnnos],
        Dependency[chipyard.stage.phases.AddDefaultTests],
        Dependency[chipyard.stage.phases.GenerateTestSuiteMakefrags],
        Dependency[chipyard.stage.phases.GenerateArtefacts],
      ) ++ enableSFCFIRRTLEmissionPasses,
      currentState = Seq(
        Dependency[firrtl.stage.phases.AddDefaults],
        Dependency[firrtl.stage.phases.Checks]
      )
    )
    pm.transform(annotations)
  }
  override final def invalidates(a: Phase): Boolean = false
}
