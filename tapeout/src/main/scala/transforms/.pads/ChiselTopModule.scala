// See LICENSE for license details.

package barstools.tapeout.transforms.pads

import chisel3._
import chisel3.experimental._
import firrtl.Transform
import firrtl.annotations.Annotation

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
    override_reset: Option[Bool] = None) extends Module {

    override_clock.foreach(clock := _)
    override_reset.foreach(reset := _)

  private val mySelf = this

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
    //TODO: PORT-1.4: Remove commented code
    //    annotate(TargetModulePadAnnoC(this, modulePadAnnotation))
    annotate(new ChiselAnnotation with RunFirrtlTransform {
      override def toFirrtl: Annotation = {
        TargetModulePadAnnoF(mySelf.toNamed, modulePadAnnotation)
      }
      def transformClass: Class[_ <: Transform] = classOf[AddIOPadsTransform]
    })
  }

  private def extractElementNames(signal: Data): Seq[String] = {
    val names = signal match {
      case elt: Record =>
        elt.elements.map { case (key, value) => extractElementNames(value).map(x => key + "_" + x) }.toSeq.flatten
      case elt: Vec[_] =>
        elt.zipWithIndex.map { case (elt, i) => extractElementNames(elt).map(x => i + "_" + x) }.toSeq.flatten
      case elt: Element => Seq("")
      case elt => throw new Exception(s"Cannot extractElementNames for type ${elt.getClass}")
    }
    names.map(s => s.stripSuffix("_"))
  }

  // TODO: Replace!
  def extractElements(signal: Data): Seq[Element] = {
    signal match {
      case elt: Record =>
        elt.elements.map { case (key, value) => extractElements(value) }.toSeq.flatten
      case elt: Vec[_] =>
        elt.map { elt => extractElements(elt) }.toSeq.flatten
      case elt: Element => Seq(elt)
      case elt => throw new Exception(s"Cannot extractElements for type ${elt.getClass}")
    }
  }

  // Annotate IO with side + pad name
  def annotatePad(sig: Element, side: PadSide = defaultPadSide, name: String = ""): Unit = if (usePads) {
    val anno = IOPadAnnotation(side.serialize, name)
    annotate(new ChiselAnnotation with RunFirrtlTransform {
      override def toFirrtl: Annotation = {
        TargetIOPadAnnoF(sig.toTarget, anno)
      }
      def transformClass: Class[_ <: Transform] = classOf[AddIOPadsTransform]
    })
  }
  def annotatePad(sig: Aggregate, name: String): Unit = annotatePad(sig, side = defaultPadSide, name)
  def annotatePad(sig: Aggregate, side: PadSide): Unit = annotatePad(sig, side, name = "")
  def annotatePad(sig: Aggregate, side: PadSide, name: String): Unit =
    extractElements(sig) foreach { x => annotatePad(x, side, name) }

  // There may be cases where pads were inserted elsewhere. If that's the case, allow certain IO to
  // not have pads auto added. Note that annotatePad and noPad are mutually exclusive!
  def noPad(sig: Element): Unit = {
    if (usePads) {
      annotate(new ChiselAnnotation with RunFirrtlTransform {
        override def toFirrtl: Annotation = {
          TargetIOPadAnnoF(sig.toTarget, NoIOPadAnnotation())
        }
        def transformClass: Class[_ <: Transform] = classOf[AddIOPadsTransform]
      })
    }
  }
  def noPad(sig: Aggregate): Unit = extractElements(sig) foreach { x => noPad(x) }

  // Since this is a super class, this should be the first thing that gets run
  // (at least when the module is actually at the top -- currently no guarantees otherwise :( firrtl limitation)
  createPads()
}
