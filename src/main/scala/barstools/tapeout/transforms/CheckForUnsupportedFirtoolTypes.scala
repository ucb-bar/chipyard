// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations.{ModuleTarget, ReferenceTarget, SingleTargetAnnotation}
import firrtl.ir._
import firrtl.options.Dependency
import firrtl.passes.memlib.ReplSeqMem
import firrtl.stage.Forms
import firrtl.stage.{RunFirrtlTransformAnnotation}
import firrtl.stage.TransformManager.TransformDependency

class CheckForUnsupportedFirtoolTypes extends Transform with DependencyAPIMigration {
  override def prerequisites:         Seq[TransformDependency] = Forms.ChirrtlForm
  override def optionalPrerequisites: Seq[TransformDependency] = Seq.empty
  override def optionalPrerequisiteOf: Seq[TransformDependency] = Seq.empty
  override def invalidates(a: Transform): Boolean = false

  def run(state: CircuitState): Boolean = {
    val c = state.circuit

		//def checkFixed(t: Type): Unit = {
		//	println(s"checkFixed -> $t")
		//	t match {
		//		case FixedType(_, _) => {
		//			runLowering = true
		//			println(s"runLowering is $runLowering")
		//		}
		//		case _ => Unit
		//	}
		//}

		def onStmtType(s: Statement): Boolean = {
			var runLowering = false
			println(s"Entering onStmtType")

      def recursive(s: Statement): Unit = {
        s match {
          case x: DefRegister => x.foreachType(_ => println(s"It works!"))
          case x: DefWire => x.foreachType(_ => println(s"1 It works!"))
          case x: DefNode => x.foreachType(_ => println(s"2 It works!"))
          case x: DefMemory => x.foreachType(_ => println(s"3 It works!"))
          case x: WDefInstance => x.foreachType(_ => println(s"4 It works!"))
          case x: Connect => x.foreachType(_ => println(s"5 It works!"))
          case x: PartialConnect => x.foreachType(_ => println(s"6 It works!"))
          case x: Block => x.foreachStmt(recursive)
          case x => x.foreachType(_ => println(s"Uh oh"))
        }
      }

			//s.foreachType(checkFixed)
			s.foreachType(_ => println("Reached"))

			runLowering
		}

    val runLoweringOverall = c.modules.map {
      case m: ExtModule => false
      case m: Module => onStmtType(m.body)
    }

		runLoweringOverall.reduce(_ || _)
  }

  def execute(state: CircuitState): CircuitState = {
    val runLoweringAnnos = Seq(RunFirrtlTransformAnnotation(new MiddleFirrtlEmitter))
    val doLowering = run(state)
		println(s"DEBUG: Final doLowering -> $doLowering")
    state.copy(annotations = state.annotations)
  }
}
