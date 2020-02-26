package barstools.tapeout.transforms.pads

import chisel3._
import barstools.tapeout.transforms.clkgen._
import chisel3.experimental._
import firrtl.transforms.DedupModules

// TODO: Move out of pads

// NOTE: You can't really annotate outside of the module itself UNLESS you break up the compile step in 2 i.e.
// annotate post-Chisel but pre-Firrtl (unfortunate non-generator friendly downside).
// It's recommended to have a Tapeout specific TopModule wrapper.
// LIMITATION: All signals of a bus must be on the same chip side

// Chisel-y annotations
abstract class TopModule(
    supplyAnnos: Seq[SupplyAnnotation] = Seq.empty,
    defaultPadSide: PadSide = Top,
    coreWidth: Int = 0,
    coreHeight: Int = 0,
    usePads: Boolean = true,
    override_clock: Option[Clock] = None,
    override_reset: Option[Bool] = None) extends Module with IsClkModule {

    override_clock.foreach(clock := _)
    override_reset.foreach(reset := _)

  override def annotateClkPort(p: Element, anno: ClkPortAnnotation): Unit = {
    DataMirror.directionOf(p) match {
      case chisel3.core.ActualDirection.Input =>
        require(anno.tag.nonEmpty, "Top Module input clks must be clk sinks")
        require(anno.tag.get.src.nonEmpty,
          "Top module input clks must have clk period, etc. specified")
      case _ =>
        throw new Exception("Clk port direction must be specified!")
    }
    p match {
      case _: chisel3.core.Clock =>
      case _ => throw new Exception("Clock port must be of type Clock")
    }
    annotate(TargetClkPortAnnoC(p, anno))
  }

  override def annotateDerivedClks(m: Module, anno: ClkModAnnotation): Unit =
    throw new Exception("Top module cannot be pure clock module!")

  // Annotate module as top module (that requires pad transform)
  // Specify the yaml file that indicates how pads are templated,
  // the default chip side that pads should be placed (if nothing is specified per IO),
  // and supply annotations: supply pad name, location, and #
  def createPads(): Unit = if (usePads) {
    val modulePadAnnotation = ModulePadAnnotation(
      defaultPadSide = defaultPadSide.serialize,
      coreWidth = coreWidth,
      coreHeight = coreHeight,
      supplyAnnos = supplyAnnos
    )
    annotate(TargetModulePadAnnoC(this, modulePadAnnotation))
  }

  // Annotate IO with side + pad name
  def annotatePad(sig: Element, side: PadSide = defaultPadSide, name: String = ""): Unit = if (usePads) {
    val anno = IOPadAnnotation(side.serialize, name)
    annotate(TargetIOPadAnnoC(sig, anno))
  }
  def annotatePad(sig: Aggregate, name: String): Unit = annotatePad(sig, side = defaultPadSide, name)
  def annotatePad(sig: Aggregate, side: PadSide): Unit = annotatePad(sig, side, name = "")
  def annotatePad(sig: Aggregate, side: PadSide, name: String): Unit =
    extractElements(sig) foreach { x => annotatePad(x, side, name) }

  // There may be cases where pads were inserted elsewhere. If that's the case, allow certain IO to
  // not have pads auto added. Note that annotatePad and noPad are mutually exclusive!
  def noPad(sig: Element): Unit = if (usePads) annotate(TargetIOPadAnnoC(sig, NoIOPadAnnotation()))
  def noPad(sig: Aggregate): Unit = extractElements(sig) foreach { x => noPad(x) }

  // Since this is a super class, this should be the first thing that gets run
  // (at least when the module is actually at the top -- currently no guarantees otherwise :( firrtl limitation)
  createPads()
}
