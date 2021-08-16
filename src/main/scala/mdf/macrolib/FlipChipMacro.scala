package mdf.macrolib

import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

// Flip Chip Macro
case class FlipChipMacro(
  name:           String,
  bumpDimensions: (Int, Int),
  bumpLocations:  Seq[Seq[String]])
    extends Macro {
  override def toJSON(): JsObject = {

    val output = new ListBuffer[(String, JsValue)]()
    output.appendAll(
      Seq(
        "name" -> Json.toJson(name),
        "type" -> Json.toJson(typeStr),
        "bump_dimensions" -> JsArray(Seq(bumpDimensions._1, bumpDimensions._2).map { JsNumber(_) }),
        "bump_locations" -> JsArray(bumpLocations.map(l => JsArray(l.map(JsString))))
      )
    )

    JsObject(output)
  }
  val maxIONameSize = bumpLocations.foldLeft(0) { (size, row) =>
    row.foldLeft(size) { (size, str) => scala.math.max(size, str.length) }
  }
  def visualize: String = {
    val output = new StringBuffer()
    for (x <- 0 until bumpDimensions._1) {
      for (y <- 0 until bumpDimensions._2) {
        val name = bumpLocations(x)(y).drop(1).dropRight(1)
        val extra = maxIONameSize - name.length()
        val leftSpace = " " * (extra / 2)
        val rightSpace = " " * (extra / 2 + extra % 2)
        output.append(leftSpace + name + rightSpace + "|")
      }
      output.append("\n")
    }
    output.toString()
  }

  override def typeStr = "flipchip"
}

object FlipChipMacro {
  def parseJSON(json: Map[String, JsValue]): Option[FlipChipMacro] = {
    val name: String = json.get("name") match {
      case Some(x: JsString) => x.as[String]
      case _ => return None
    }

    val bumpDimensions: (Int, Int) = json.get("bump_dimensions") match {
      case Some(JsArray(x)) if x.size == 2 =>
        val z = x.map(_.as[JsNumber].value.intValue)
        (z(0), z(1))
      case None => return None
    }
    val bumpLocations: Seq[Seq[String]] = json.get("bump_locations") match {
      case Some(JsArray(array)) =>
        array.collect { case JsArray(a2) => a2.map(_.toString).toSeq }.toSeq
      case _ => return None
    }
    // Can't have dimensions and locations which don't match
    if (bumpLocations.size != bumpDimensions._1) return None
    if (bumpLocations.collect { case x if x.size != bumpDimensions._2 => x }.nonEmpty) return None

    Some(FlipChipMacro(name, bumpDimensions, bumpLocations))
  }
}
