// See LICENSE for license details.

package barstools.tapeout.transforms.pads

import firrtl.annotations._
import net.jcazevedo.moultingyaml._

object PadAnnotationsYaml extends DefaultYamlProtocol {
  implicit val _iopad = yamlFormat2(IOPadAnnotation)
  implicit val _noiopad = yamlFormat1(NoIOPadAnnotation)
  implicit val _supplyanno = yamlFormat5(SupplyAnnotation)
  implicit val _modulepadanno = yamlFormat4(ModulePadAnnotation)

  // Putting these serialize methods here seems to fix warnings about missing implicits for the toYaml
  def serialize(noIOPad: NoIOPadAnnotation): String = {
    noIOPad.toYaml.prettyPrint
  }
  def serialize(ioPadAnnotation: IOPadAnnotation): String = {
    ioPadAnnotation.toYaml.prettyPrint
  }
  def serialize(modulePadAnnotation: ModulePadAnnotation): String = {
    modulePadAnnotation.toYaml.prettyPrint
  }
}

abstract class FirrtlPadTransformAnnotation {
  def targetName: String
}

// IO Port can either be annotated with padName + padSide OR noPad (mutually exclusive)
abstract class IOAnnotation {
  def serialize: String
}

case class IOPadAnnotation(padSide: String, padName: String) extends IOAnnotation {
  def serialize: String = PadAnnotationsYaml.serialize(this)
  def getPadSide: PadSide = HasPadAnnotation.getSide(padSide)
}

case class NoIOPadAnnotation(noPad: String = "") extends IOAnnotation {
  def serialize: String = PadAnnotationsYaml.serialize(this)
  def field: String = "noPad:"
}

// Firrtl version
case class TargetIOPadAnnoF(target: ComponentName, anno: IOAnnotation)
  extends FirrtlPadTransformAnnotation with SingleTargetAnnotation[ComponentName] {

  def duplicate(n: ComponentName): TargetIOPadAnnoF = this.copy(target = n)
  def targetName: String = target.name
}

// A bunch of supply pads (designated by name, # on each chip side) can be associated with the top module
case class SupplyAnnotation(
    padName: String,
    leftSide: Int = 0,
    rightSide: Int = 0,
    topSide: Int = 0,
    bottomSide: Int = 0)

// The chip top should have a default pad side, a pad template file, and supply annotations
case class ModulePadAnnotation(
    defaultPadSide: String = Top.serialize,
    coreWidth: Int = 0,
    coreHeight: Int = 0,
    supplyAnnos: Seq[SupplyAnnotation] = Seq.empty) {

  def serialize: String = PadAnnotationsYaml.serialize(this)
  def supplyPadNames: Seq[String] = supplyAnnos.map(_.padName)
  require(supplyPadNames.distinct.length == supplyPadNames.length, "Supply pads should only be specified once!")
  def getDefaultPadSide: PadSide = HasPadAnnotation.getSide(defaultPadSide)
}

// Firrtl version
case class TargetModulePadAnnoF(target: ModuleName, anno: ModulePadAnnotation)
  extends FirrtlPadTransformAnnotation with SingleTargetAnnotation[ModuleName] {

  def duplicate(n: ModuleName): TargetModulePadAnnoF = this.copy(target = n)
  def targetName: String = target.name
}


case class CollectedAnnos(
    componentAnnos: Seq[TargetIOPadAnnoF],
    moduleAnnos: TargetModulePadAnnoF) {
  def supplyAnnos = moduleAnnos.anno.supplyAnnos
  def defaultPadSide = moduleAnnos.anno.defaultPadSide
  def topModName = moduleAnnos.targetName
  def coreWidth = moduleAnnos.anno.coreWidth
  def coreHeight = moduleAnnos.anno.coreHeight
}

object HasPadAnnotation {

  def getSide(a: String): PadSide = a match {
    case i if i == Left.serialize => Left
    case i if i == Right.serialize => Right
    case i if i == Top.serialize => Top
    case i if i == Bottom.serialize => Bottom
    case _ => throw new Exception(s" $a not a valid pad side annotation!")
  }

  //TODO: PORT-1.4: Remove commented code
//  def unapply(a: Annotation): Option[FirrtlPadTransformAnnotation] = a match {
//    case Annotation(f, t, s) if t == classOf[AddIOPadsTransform] => f match {
//      case m: ModuleName =>
//        Some(TargetModulePadAnnoF(m, s.parseYaml.convertTo[ModulePadAnnotation]))
//      case c: ComponentName if s.contains(NoIOPadAnnotation().field) =>
//        Some(TargetIOPadAnnoF(c, s.parseYaml.convertTo[NoIOPadAnnotation]))
//      case c: ComponentName =>
//        Some(TargetIOPadAnnoF(c, s.parseYaml.convertTo[IOPadAnnotation]))
//      case _ => throw new Exception("Annotation only valid on module or component")
//    }
//    case _ => None
//  }

  def apply(annos: Seq[Annotation]): Option[CollectedAnnos] = {
    // Get all pad-related annotations (config files, pad sides, pad names, etc.)
    val padAnnos = annos.flatMap {
      case a: TargetModulePadAnnoF => Some(a)
      case a: TargetIOPadAnnoF => Some(a)
      case _ => None
    }
    val targets = padAnnos.map(x => x.targetName)
    require(targets.distinct.length == targets.length, "Only 1 pad related annotation is allowed per component/module")
    if (padAnnos.length == 0) {
      None
    } else {
      val moduleAnnosTemp = padAnnos.filter {
        case TargetModulePadAnnoF(_, _) => true
        case _ => false
      }
      require(moduleAnnosTemp.length == 1, "Only 1 module may be designated 'Top'")
      val moduleAnnos = moduleAnnosTemp.head
      val topModName = moduleAnnos.targetName
      val componentAnnos = padAnnos.filter {
        case TargetIOPadAnnoF(ComponentName(_, ModuleName(n, _)), _) if n == topModName =>
          true
        case TargetIOPadAnnoF(ComponentName(_, ModuleName(n, _)), _) if n != topModName =>
          throw new Exception("Pad related component annotations must all be in the same top module")
        case _ => false
      }.map(x => x.asInstanceOf[TargetIOPadAnnoF])
      Some(CollectedAnnos(componentAnnos, moduleAnnos.asInstanceOf[TargetModulePadAnnoF]))
    }
  }
}
