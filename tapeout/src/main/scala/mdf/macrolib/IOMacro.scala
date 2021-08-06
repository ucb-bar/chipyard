package mdf.macrolib

import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

sealed abstract class PortType { def toJSON(): JsString = JsString(toString) }
case object Digital extends PortType { override def toString: String = "digital" }
case object Analog extends PortType { override def toString: String = "analog" }
case object Power extends PortType { override def toString: String = "power" }
case object Ground extends PortType { override def toString: String = "ground" }
case object NoConnect extends PortType { override def toString: String = "NC" }

sealed abstract class Direction { def toJSON(): JsString = JsString(toString) }
case object Input extends Direction { override def toString: String = "input" }
case object Output extends Direction { override def toString: String = "output" }
case object InOut extends Direction { override def toString: String = "inout" }

sealed abstract class Termination { def toJSON(): JsValue }
case object CMOS extends Termination { override def toJSON(): JsString = JsString("CMOS") }
case class Resistive(ohms: Int) extends Termination { override def toJSON(): JsNumber = JsNumber(ohms) }

sealed abstract class TerminationType { def toJSON(): JsString }
case object Single extends TerminationType { override def toJSON(): JsString = JsString("single") }
case object Differential extends TerminationType { override def toJSON(): JsString = JsString("differential") }

// IO macro
case class IOMacro(
  name:                 String,
  tpe:                  PortType,
  direction:            Option[Direction] = None,
  termination:          Option[Termination] = None,
  terminationType:      Option[TerminationType] = None,
  terminationReference: Option[String] = None,
  matching:             Seq[String] = Seq.empty[String],
  bbname:               Option[String] = None)
    extends Macro {
  override def toJSON(): JsObject = {

    val output = new ListBuffer[(String, JsValue)]()
    output.appendAll(
      Seq(
        "name" -> Json.toJson(name),
        "type" -> tpe.toJSON()
      )
    )
    if (direction.isDefined) output.append("direction" -> direction.get.toJSON)
    if (termination.isDefined) output.append("termination" -> termination.get.toJSON)
    if (terminationType.isDefined) output.append("terminationType" -> terminationType.get.toJSON)
    if (terminationReference.isDefined) output.append("terminationReference" -> JsString(terminationReference.get))
    if (matching.nonEmpty) output.append("match" -> JsArray(matching.map(JsString)))
    if (bbname.nonEmpty) output.append("blackBox" -> JsString(bbname.get))

    JsObject(output)
  }

  override def typeStr = "iomacro"
}
object IOMacro {
  def parseJSON(json: Map[String, JsValue]): Option[IOMacro] = {
    val name: String = json.get("name") match {
      case Some(x: JsString) => x.as[String]
      case _ => return None
    }
    val tpe: PortType = json.get("type") match {
      case Some(JsString("power"))   => Power
      case Some(JsString("ground"))  => Ground
      case Some(JsString("digital")) => Digital
      case Some(JsString("analog"))  => Analog
      case Some(JsString("NC"))      => NoConnect
      case _                         => return None
    }
    val direction: Option[Direction] = json.get("direction") match {
      case Some(JsString("input"))  => Some(Input)
      case Some(JsString("output")) => Some(Output)
      case Some(JsString("inout"))  => Some(InOut)
      case _                        => None
    }
    val termination: Option[Termination] = json.get("termination") match {
      case Some(JsNumber(x))      => Some(Resistive(x.toInt))
      case Some(JsString("CMOS")) => Some(CMOS)
      case _                      => None
    }
    val terminationType: Option[TerminationType] = json.get("terminationType") match {
      case Some(JsString("differential")) => Some(Differential)
      case Some(JsString("single"))       => Some(Single)
      case _                              => None
    }
    val terminationRef: Option[String] = json.get("terminationReference") match {
      case Some(JsString(x))              => Some(x)
      case _ if terminationType.isDefined => return None
      case _                              => None
    }
    val matching: Seq[String] = json.get("match") match {
      case Some(JsArray(array)) => array.map(_.as[JsString].value).toList
      case _                    => Seq.empty[String]
    }
    val bbname: Option[String] = json.get("blackBox") match {
      case Some(JsString(module)) => Some(module)
      case Some(_)                => return None
      case _                      => None
    }
    Some(IOMacro(name, tpe, direction, termination, terminationType, terminationRef, matching, bbname))
  }
}

case class IOProperties(name: String, top: String, ios: Seq[IOMacro]) extends Macro {
  override def toJSON(): JsObject = {
    val output = new ListBuffer[(String, JsValue)]()
    output.appendAll(
      Seq(
        "name" -> Json.toJson(name),
        "top" -> Json.toJson(top),
        "type" -> Json.toJson(typeStr),
        "ios" -> JsArray(ios.map(_.toJSON))
      )
    )
    JsObject(output)
  }

  override def typeStr = "io_properties"

}

object IOProperties {
  def parseJSON(json: Map[String, JsValue]): Option[IOProperties] = {
    val name: String = json.get("name") match {
      case Some(x: JsString) => x.as[String]
      case _ => return None
    }
    val top: String = json.get("top") match {
      case Some(x: JsString) => x.as[String]
      case _ => return None
    }
    val ios: Seq[IOMacro] = json.get("ios") match {
      case Some(x: JsArray) =>
        x.as[List[Map[String, JsValue]]].map { a =>
          val b = IOMacro.parseJSON(a);
          if (b == None) {
            return None
          } else b.get
        }
      case _ => List()
    }
    Some(IOProperties(name, top, ios))
  }
}
