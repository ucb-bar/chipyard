package mdf.macrolib

import play.api.libs.json._
import scala.language.implicitConversions

// Filler and metal filler
abstract class FillerMacroBase(name: String, vt: String) extends Macro {
  override def toString(): String = {
    s"${this.getClass.getSimpleName}(name=${name}, vt=${vt})"
  }

  override def toJSON(): JsObject = {
    JsObject(
      Seq(
        "type" -> JsString(typeStr),
        "name" -> Json.toJson(name),
        "vt" -> Json.toJson(vt)
      )
    )
  }
}
object FillerMacroBase {
  def parseJSON(json: Map[String, JsValue]): Option[FillerMacroBase] = {
    val typee: String = json.get("type") match {
      case Some(x: JsString) =>
        x.value match {
          case "" => return None
          case x  => x
        }
      case _ => return None
    }
    val name: String = json.get("name") match {
      case Some(x: JsString) =>
        x.value match {
          case "" => return None
          case x  => x
        }
      case _ => return None
    }
    val vt: String = json.get("vt") match {
      case Some(x: JsString) =>
        x.value match {
          case "" => return None
          case x  => x
        }
      case _ => return None
    }
    typee match {
      case "metal filler cell" => Some(MetalFillerMacro(name, vt))
      case "filler cell"       => Some(FillerMacro(name, vt))
      case _                   => None
    }
  }
}

case class FillerMacro(name: String, vt: String) extends FillerMacroBase(name, vt) {
  override def typeStr = "filler cell"
}
case class MetalFillerMacro(name: String, vt: String) extends FillerMacroBase(name, vt) {
  override def typeStr = "metal filler cell"
}
