// See LICENSE for license details.

package barstools.tapeout.transforms.clkgen

import firrtl._
import firrtl.passes._

import scala.collection.mutable

class ClkSrcTransform extends Transform with SeqTransformBased {

  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  val transformList = new mutable.ArrayBuffer[Transform]
  def transforms = transformList

  override def execute(state: CircuitState): CircuitState = {
    val collectedAnnos = HasClkAnnotation(getMyAnnotations(state))
    collectedAnnos match {
      // Transform not used
      case None => CircuitState(state.circuit, LowForm)
      case Some((clkModAnnos, clkPortAnnos)) =>
        val targetDir = barstools.tapeout.transforms.GetTargetDir(state)
      
        transformList ++= Seq(
          InferTypes,
          new CreateClkConstraints(clkModAnnos, clkPortAnnos, targetDir)
        )
        val ret = runTransforms(state)
        CircuitState(ret.circuit, outputForm, ret.annotations, ret.renames)
    }
  }
}
