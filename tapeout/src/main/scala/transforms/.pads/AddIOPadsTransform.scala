// See LICENSE for license details.

package barstools.tapeout.transforms.pads

import firrtl._
import firrtl.annotations._
import firrtl.passes._
import firrtl.ir._
import barstools.tapeout.transforms._

import scala.collection.mutable

// Main Add IO Pad transform operates on low Firrtl
class AddIOPadsTransform extends Transform with SeqTransformBased {

  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  val transformList = new mutable.ArrayBuffer[Transform]
  def transforms: Seq[Transform] = transformList

  override def execute(state: CircuitState): CircuitState = {
    val collectedAnnos = HasPadAnnotation(getMyAnnotations(state))
    collectedAnnos match {
      // Transform not used
      case None => state
      case Some(x) =>
        val techLoc = (new TechnologyLocation).get(state)
        // Get foundry pad templates from yaml
        val foundryPads = FoundryPadsYaml.parse(techLoc)
        val portPads = AnnotatePortPads(state.circuit, x.topModName, foundryPads, x.componentAnnos,
          HasPadAnnotation.getSide(x.defaultPadSide))
        val supplyPads = AnnotateSupplyPads(foundryPads, x.supplyAnnos)
        val (circuitWithBBs, bbAnnotations) = CreatePadBBs(state.circuit, portPads, supplyPads)
        val namespace = Namespace(state.circuit)
        val padFrameName = namespace newName s"${x.topModName}_PadFrame"
        val topInternalName = namespace newName s"${x.topModName}_Internal"
        val targetDir = barstools.tapeout.transforms.GetTargetDir(state)
        PadPlacementFile.generate(techLoc, targetDir, padFrameName, portPads, supplyPads)
        transformList ++= Seq(
          Legalize,
          ResolveGenders,
          // Types really need to be known...
          InferTypes,
          new AddPadFrame(x.topModName, padFrameName, topInternalName, portPads, supplyPads),
          RemoveEmpty,
          CheckInitialization,
          InferTypes,
          Uniquify,
          ResolveKinds,
          ResolveGenders
        )
        // Expects BlackBox helper to be run after to inline pad Verilog!
        val ret = runTransforms(state)
        val currentAnnos = ret.annotations.getOrElse(AnnotationMap(Seq.empty)).annotations
        val newAnnoMap = AnnotationMap(currentAnnos ++ bbAnnotations)
        val newState = CircuitState(ret.circuit, outputForm, Some(newAnnoMap), ret.renames)

        // TODO: *.f file is overwritten on subsequent executions, but it doesn't seem to be used anywhere?
        (new firrtl.transforms.BlackBoxSourceHelper).execute(newState)
    }
  }
}