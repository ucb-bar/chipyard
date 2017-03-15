package barstools.tapeout.transforms.pads

import firrtl._
import firrtl.annotations._
import firrtl.passes._
import firrtl.ir._
import barstools.tapeout.transforms._

// Main Add IO Pad transform operates on low Firrtl
class AddIOPadsTransform extends Transform with SimpleRun {

  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

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
        val passSeq = Seq(
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
        val prevAnnos = state.annotations.getOrElse(AnnotationMap(Seq.empty)).annotations
        val cs = state.copy(
          circuit = runPasses(circuitWithBBs, passSeq), 
          annotations = Some(AnnotationMap(prevAnnos ++ bbAnnotations)))
       
        // TODO: *.f file is overwritten on subsequent executions, but it doesn't seem to be used anywhere?
        (new firrtl.transforms.BlackBoxSourceHelper).execute(cs)
    }
  }
}