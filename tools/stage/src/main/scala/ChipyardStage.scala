// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import circt.stage.{ChiselStage, CIRCTTargetAnnotation, CIRCTTarget}
import firrtl.options.PhaseManager.PhaseDependency
import firrtl.options.{Shell}
import firrtl.{AnnotationSeq}
import firrtl.options.{Phase, PhaseManager, Shell, Stage, StageError, StageMain, Dependency}

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
  override val shell = new Shell("chipyard") with ChipyardCli with circt.stage.CLI
  override def run(annotations: AnnotationSeq): AnnotationSeq = {

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
      ),
      currentState = Seq(
        Dependency[firrtl.stage.phases.AddDefaults],
        Dependency[firrtl.stage.phases.Checks]
      )
    )
    val anno_seq = pm.transform(annotations)

    val run_firrtl2 = annotations.collectFirst({
      case chipyard.stage.EnableFirrtl2PassAnnotation(e) => e
    }).getOrElse("false")

    if (run_firrtl2 == "true") {
      Firrtl2Passes.run(anno_seq)
    }

    anno_seq
  }

  override final def invalidates(a: Phase): Boolean = false
}

class ExampleFirrtl2Pass extends firrtl2.Transform {
  def onStmt(s: firrtl2.ir.Statement): firrtl2.ir.Statement = {
    println(s"${s}")
    s
  }

  def onModule(m: firrtl2.ir.DefModule): firrtl2.ir.DefModule = {
    m.mapStmt(onStmt)
  }

  def printCircuit(c: firrtl2.ir.Circuit): Unit = {
    c.modules.map(onModule)
  }

  override def execute(state: firrtl2.CircuitState): firrtl2.CircuitState = {
    println("Executing firrtl2 pass")
    val c = state.circuit
    printCircuit(c)
    state
  }
}

object Firrtl2Passes {
  def run(annotations: AnnotationSeq): Unit = {
    println("Running Firrtl2 Passes")
    val circuitState: firrtl2.CircuitState = ChiselBridge.annosToState(annotations)
    val passes = Seq(
      new ExampleFirrtl2Pass
    )
    passes.foldLeft(circuitState)((c, p) => p.execute(c))

  }
}
