package barstools.tapeout.transforms.clkgen

import firrtl._
import firrtl.annotations._
import firrtl.passes._
import firrtl.ir._

class ClkSrcTransform extends Transform with SimpleRun {

  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  override def execute(state: CircuitState): CircuitState = {
    val collectedAnnos = HasClkAnnotation(getMyAnnotations(state))
    collectedAnnos match {
      // Transform not used
      case None => CircuitState(state.circuit, LowForm)
      case Some((clkModAnnos, clkPortAnnos)) => 
        val targetDir = barstools.tapeout.transforms.GetTargetDir(state)
        val passSeq = Seq(
          // TODO: Enable when it's legal?
          // InferTypes,
          new CreateClkConstraints(clkModAnnos, clkPortAnnos, targetDir)
        )
        state.copy(circuit = runPasses(state.circuit, passSeq))
    }
  }
}
