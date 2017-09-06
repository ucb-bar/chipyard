package barstools.tapeout.transforms.clkgen

import net.jcazevedo.moultingyaml._
import firrtl.annotations._
import chisel3.experimental._
import chisel3._
import firrtl._
import firrtl.transforms.DedupModules

object ClkAnnotationsYaml extends DefaultYamlProtocol {
  implicit val _clksrc = yamlFormat3(ClkSrc)
  implicit val _sink = yamlFormat1(Sink)
  implicit val _clkport = yamlFormat2(ClkPortAnnotation)
  implicit val _genclk = yamlFormat4(GeneratedClk)
  implicit val _clkmod = yamlFormat2(ClkModAnnotation)
}
case class ClkSrc(period: Double, waveform: Seq[Double] = Seq(), async: Seq[String] = Seq()) {
  def getWaveform = if (waveform == Seq.empty) Seq(0, period/2) else waveform
  // async = ids of top level clocks that are async with this clk
  // Default is 50% duty cycle, period units is default
  require(getWaveform.sorted == getWaveform, "Waveform edges must be in order")
  require(getWaveform.length == 2, "Must specify time for rising edge, then time for falling edge")
}

case class Sink(src: Option[ClkSrc] = None)

case class ClkPortAnnotation(tag: Option[Sink] = None, id: String) {
  import ClkAnnotationsYaml._
  def serialize: String = this.toYaml.prettyPrint
}

abstract class ClkModType {
  def serialize: String
}
case object ClkMux extends ClkModType {
  def serialize: String = "mux"
}
case object ClkDiv extends ClkModType {
  def serialize: String = "div"
}
case object ClkGen extends ClkModType {
  def serialize: String = "gen"
}

// Unlike typical SDC, starts at 0. 
// Otherwise, see pg. 63 of "Constraining Designs for Synthesis and Timing Analysis" 
// by S. Gangadharan
// original clk:     |-----|_____|-----|_____|
// edges:            0     1     2     3     4
// div. by 4, 50% duty cycle --> edges = 0, 2, 4
// --->              |-----------|___________|
// sources = source id's
case class GeneratedClk(
    id: String, 
    sources: Seq[String] = Seq(), 
    referenceEdges: Seq[Int] = Seq(), 
    period: Option[Double] = None) {
  require(referenceEdges.sorted == referenceEdges, "Edges must be in order for generated clk")
  if (referenceEdges.nonEmpty) require(referenceEdges.length % 2 == 1, "# of reference edges must be odd!")
}

case class ClkModAnnotation(tpe: String, generatedClks: Seq[GeneratedClk]) {

  def modType: ClkModType = HasClkAnnotation.modType(tpe)

  modType match {
    case ClkDiv => 
      generatedClks foreach { c =>
        require(c.referenceEdges.nonEmpty, "Reference edges must be defined for clk divider!")
        require(c.sources.length == 1, "Clk divider output can only have 1 source")
        require(c.period.isEmpty, "No period should be specified for clk divider output")
      }
    case ClkMux => 
      generatedClks foreach { c =>
        require(c.referenceEdges.isEmpty, "Reference edges must not be defined for clk mux!")
        require(c.period.isEmpty, "No period should be specified for clk mux output")
        require(c.sources.nonEmpty, "Clk muxes must have sources!")
      }
    case ClkGen =>
      generatedClks foreach { c =>
        require(c.referenceEdges.isEmpty, "Reference edges must not be defined for clk gen!")
        require(c.sources.isEmpty, "Clk generators shouldn't have constrained sources")
        require(c.period.nonEmpty, "Clk generator output period should be specified!")
      }
  }
  import ClkAnnotationsYaml._
  def serialize: String = this.toYaml.prettyPrint
}

abstract class FirrtlClkTransformAnnotation {
  def targetName: String
}

// Firrtl version
case class TargetClkModAnnoF(target: ModuleName, anno: ClkModAnnotation) extends FirrtlClkTransformAnnotation {
  def getAnno = Annotation(target, classOf[ClkSrcTransform], anno.serialize)
  def targetName = target.name
  def modType = anno.modType
  def generatedClks = anno.generatedClks
  def getAllClkPorts = anno.generatedClks.map(x => 
    List(List(x.id), x.sources).flatten).flatten.distinct.map(Seq(targetName, _).mkString("."))
}

// Chisel version
case class TargetClkModAnnoC(target: Module, anno: ClkModAnnotation) {
  def getAnno = ChiselAnnotation(target, classOf[ClkSrcTransform], anno.serialize)
}

// Firrtl version
case class TargetClkPortAnnoF(target: ComponentName, anno: ClkPortAnnotation) extends FirrtlClkTransformAnnotation {
  def getAnno = Annotation(target, classOf[ClkSrcTransform], anno.serialize)
  def targetName = Seq(target.module.name, target.name).mkString(".")
  def modId = Seq(target.module.name, anno.id).mkString(".")
  def sink = anno.tag
}

// Chisel version
case class TargetClkPortAnnoC(target: Element, anno: ClkPortAnnotation) {
  def getAnno = ChiselAnnotation(target, classOf[ClkSrcTransform], anno.serialize)
}

object HasClkAnnotation {

  import ClkAnnotationsYaml._

  def modType(tpe: String): ClkModType = tpe match {
    case s: String if s == ClkMux.serialize => ClkMux
    case s: String if s == ClkDiv.serialize => ClkDiv
    case s: String if s == ClkGen.serialize => ClkGen
    case _ => throw new Exception("Clock module annotaiton type invalid")
  }

  def unapply(a: Annotation): Option[FirrtlClkTransformAnnotation] = a match {
    case Annotation(f, t, s) if t == classOf[ClkSrcTransform] => f match {
      case m: ModuleName => 
        Some(TargetClkModAnnoF(m, s.parseYaml.convertTo[ClkModAnnotation]))
      case c: ComponentName =>
        Some(TargetClkPortAnnoF(c, s.parseYaml.convertTo[ClkPortAnnotation]))
      case _ => throw new Exception("Clk source annotation only valid on module or component!")    
    }
    case _ => None
  }

  def apply(annos: Seq[Annotation]): Option[(Seq[TargetClkModAnnoF],Seq[TargetClkPortAnnoF])] = {
    // Get all clk-related annotations
    val clkAnnos = annos.map(x => unapply(x)).flatten 
    val targets = clkAnnos.map(x => x.targetName)
    require(targets.distinct.length == targets.length, "Only 1 clk related annotation is allowed per component/module")
    if (clkAnnos.length == 0) None
    else {
      val componentAnnos = clkAnnos.filter { 
        case TargetClkPortAnnoF(ComponentName(_, ModuleName(_, _)), _) => true
        case _ => false
      }.map(x => x.asInstanceOf[TargetClkPortAnnoF])
      val associatedMods = componentAnnos.map(x => x.target.module.name)
      val moduleAnnos = clkAnnos.filter { 
        case TargetClkModAnnoF(ModuleName(m, _), _) => 
          require(associatedMods contains m, "Clk modules should always have clk port annotations!")
          true 
        case _ => false
      }.map(x => x.asInstanceOf[TargetClkModAnnoF])
      Some((moduleAnnos, componentAnnos))
    }
  }

}

// Applies to both black box + normal module
trait IsClkModule {

  self: chisel3.Module =>

  private def doNotDedup(module: Module): Unit = {
    annotate(ChiselAnnotation(module, classOf[DedupModules], "nodedup!"))
  }
  doNotDedup(this)

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

  def getIOName(signal: Element): String = {
    val possibleNames = extractElements(io).zip(extractElementNames(io)).map { 
      case (sig, name) if sig == signal => Some(name)
      case _ => None
    }.flatten
    if (possibleNames.length == 1) possibleNames.head
    else throw new Exception("You can only get the name of an io port!")
  }

  def annotateDerivedClks(tpe: ClkModType, generatedClks: Seq[GeneratedClk]): Unit = 
    annotateDerivedClks(ClkModAnnotation(tpe.serialize, generatedClks))
  def annotateDerivedClks(anno: ClkModAnnotation): Unit = annotateDerivedClks(this, anno)
  def annotateDerivedClks(m: Module, anno: ClkModAnnotation): Unit = 
    annotate(TargetClkModAnnoC(m, anno).getAnno)

  def annotateClkPort(p: Element): Unit = annotateClkPort(p, None, "")
  def annotateClkPort(p: Element, sink: Sink): Unit = annotateClkPort(p, Some(sink), "")
  def annotateClkPort(p: Element, id: String): Unit = annotateClkPort(p, None, id)
  def annotateClkPort(p: Element, sink: Sink, id: String): Unit = annotateClkPort(p, Some(sink), id)
  def annotateClkPort(p: Element, sink: Option[Sink], id: String): Unit = {
    // If no id is specified, it'll try to figure out a name, assuming p is an io port
    val newId = id match {
      case "" => 
        getIOName(p)
      case _ => id
    }
    annotateClkPort(p, ClkPortAnnotation(sink, newId))
  }

  def annotateClkPort(p: Element, anno: ClkPortAnnotation): Unit = {
    p.dir match {
      case chisel3.core.Direction.Input => 
        require(anno.tag.nonEmpty, "Module inputs must be clk sinks")
        require(anno.tag.get.src.isEmpty, 
          "Clock module (not top) input clks should not have clk period, etc. specified")
      case chisel3.core.Direction.Output =>
        require(anno.tag.isEmpty, "Module outputs must not be clk sinks (they're sources!)")
      case _ =>
        throw new Exception("Clk port direction must be specified!")
    }
    p match {
      case _: chisel3.core.Clock =>
      case _ => throw new Exception("Clock port must be of type Clock")
    }
    annotate(TargetClkPortAnnoC(p, anno).getAnno)
  }
}