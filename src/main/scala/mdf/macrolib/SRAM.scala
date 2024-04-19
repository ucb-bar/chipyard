package mdf.macrolib

import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

// SRAM macro
case class SRAMMacro(
  name:       String,
  width:      Int,
  depth:      BigInt,
  family:     String,
  ports:      Seq[MacroPort],
  vt:         String = "",
  mux:        Int = 1,
  extraPorts: Seq[MacroExtraPort] = List())
    extends Macro {
  override def toJSON(): JsObject = {
    val output = new ListBuffer[(String, JsValue)]()
    output.appendAll(
      Seq(
        "type" -> JsString("sram"),
        "name" -> Json.toJson(name),
        "width" -> Json.toJson(width),
        "depth" -> Json.toJson(depth.toString),
        "mux" -> Json.toJson(mux),
        "mask" -> Json.toJson(ports.exists(p => p.maskPort.isDefined)),
        "ports" -> JsArray(ports.map { _.toJSON })
      )
    )
    if (family != "") {
      output.appendAll(Seq("family" -> Json.toJson(family)))
    }
    if (vt != "") {
      output.appendAll(Seq("vt" -> Json.toJson(vt)))
    }
    if (extraPorts.length > 0) {
      output.appendAll(Seq("extra ports" -> JsArray(extraPorts.map { _.toJSON })))
    }

    JsObject(output)
  }

  override def typeStr = "sram"
}
object SRAMMacro {
  def parseJSON(json: Map[String, JsValue]): Option[SRAMMacro] = {
    val name: String = json.get("name") match {
      case Some(x: JsString) => x.as[String]
      case _ => return None
    }
    val width: Int = json.get("width") match {
      case Some(x: JsNumber) => x.value.intValue
      case _ => return None
    }
    val depth: BigInt = json.get("depth") match {
      case Some(x: JsString) =>
        try { BigInt(x.as[String]) }
        catch { case _: Throwable => return None }
      case _ => return None
    }
    val family: String = json.get("family") match {
      case Some(x: JsString) => x.as[String]
      case _ => "" // optional
    }
    val vt: String = json.get("vt") match {
      case Some(x: JsString) => x.as[String]
      case _ => "" // optional
    }
    val mux: Int = json.get("mux") match {
      case Some(x: JsNumber) => x.value.intValue
      case _ => 1 // default
    }
    val ports: Seq[MacroPort] = json.get("ports") match {
      case Some(x: JsArray) =>
        x.as[List[Map[String, JsValue]]].map { a =>
          val b = MacroPort.parseJSON(a, width, depth);
          if (b == None) {
            return None
          } else b.get
        }
      case _ => List()
    }
    if (ports.length == 0) {
      // Can't have portless memories.
      return None
    }
    val extraPorts: Seq[MacroExtraPort] = json.get("extra ports") match {
      case Some(x: JsArray) =>
        x.as[List[Map[String, JsValue]]].map { a =>
          val b = MacroExtraPort.parseJSON(a);
          if (b == None) {
            return None
          } else b.get
        }
      case _ => List()
    }
    Some(SRAMMacro(name, width, depth, family, ports, vt, mux, extraPorts))
  }
}

// SRAM compiler
case class SRAMGroup(
  name:       Seq[String],
  family:     String,
  vt:         Seq[String],
  mux:        Int,
  depth:      Range,
  width:      Range,
  ports:      Seq[MacroPort],
  extraPorts: Seq[MacroExtraPort] = List()) {
  def toJSON: JsObject = {
    val output = new ListBuffer[(String, JsValue)]()
    output.appendAll(
      Seq(
        "name" -> JsArray(name.map(Json.toJson(_))),
        "vt" -> JsArray(vt.map(Json.toJson(_))),
        "mux" -> Json.toJson(mux),
        "depth" -> JsArray(Seq(depth.start, depth.end, depth.step).map { x => Json.toJson(x) }),
        "width" -> JsArray(Seq(width.start, width.end, width.step).map { x => Json.toJson(x) }),
        "ports" -> JsArray(ports.map { _.toJSON })
      )
    )
    if (family != "") {
      output.appendAll(Seq("family" -> Json.toJson(family)))
    }
    if (extraPorts.length > 0) {
      output.appendAll(Seq("extra ports" -> JsArray(extraPorts.map { _.toJSON })))
    }
    JsObject(output)
  }
}
object SRAMGroup {
  def parseJSON(json: Map[String, JsValue]): Option[SRAMGroup] = {
    val family: String = json.get("family") match {
      case Some(x: JsString) => x.as[String]
      case _ => "" // optional
    }
    val name: Seq[String] = json.get("name") match {
      case Some(x: JsArray) => x.as[List[JsString]].map(_.as[String])
      case _ => return None
    }
    val vt: Seq[String] = json.get("vt") match {
      case Some(x: JsArray) => x.as[List[JsString]].map(_.as[String])
      case _ => return None
    }
    val mux: Int = json.get("mux") match {
      case Some(x: JsNumber) => x.value.intValue
      case _ => return None
    }
    val depth: Range = json.get("depth") match {
      case Some(x: JsArray) =>
        val seq = x.as[List[JsNumber]].map(_.value.intValue)
        Range.inclusive(seq(0), seq(1), seq(2))
      case _ => return None
    }
    val width: Range = json.get("width") match {
      case Some(x: JsArray) =>
        val seq = x.as[List[JsNumber]].map(_.value.intValue)
        Range.inclusive(seq(0), seq(1), seq(2))
      case _ => return None
    }
    val ports: Seq[MacroPort] = json.get("ports") match {
      case Some(x: JsArray) =>
        x.as[List[Map[String, JsValue]]].map { a =>
          {
            val b = MacroPort.parseJSON(a, None, None);
            if (b == None) {
              return None
            } else b.get
          }
        }
      case _ => List()
    }
    if (ports.length == 0) {
      // Can't have portless memories.
      return None
    }
    val extraPorts: Seq[MacroExtraPort] = json.get("extra ports") match {
      case Some(x: JsArray) =>
        x.as[List[Map[String, JsValue]]].map { a =>
          {
            val b = MacroExtraPort.parseJSON(a);
            if (b == None) {
              return None
            } else b.get
          }
        }
      case _ => List()
    }
    Some(SRAMGroup(name, family, vt, mux, depth, width, ports, extraPorts))
  }
}

case class SRAMCompiler(
  name:   String,
  groups: Seq[SRAMGroup])
    extends Macro {
  override def toJSON(): JsObject = {
    val output = new ListBuffer[(String, JsValue)]()
    output.appendAll(
      Seq(
        "type" -> Json.toJson("sramcompiler"),
        "name" -> Json.toJson(name),
        "groups" -> JsArray(groups.map { _.toJSON })
      )
    )

    JsObject(output)
  }

  override def typeStr = "sramcompiler"
}
object SRAMCompiler {
  def parseJSON(json: Map[String, JsValue]): Option[SRAMCompiler] = {
    val name: String = json.get("name") match {
      case Some(x: JsString) => x.as[String]
      case _ => return None
    }
    val groups: Seq[SRAMGroup] = json.get("groups") match {
      case Some(x: JsArray) =>
        x.as[List[Map[String, JsValue]]].map { a =>
          {
            val b = SRAMGroup.parseJSON(a);
            if (b == None) { return None }
            else b.get
          }
        }
      case _ => List()
    }
    if (groups.length == 0) {
      // Can't have portless memories.
      return None
    }
    Some(SRAMCompiler(name, groups))
  }
}

// Type of extra port
sealed abstract class MacroExtraPortType
case object Constant extends MacroExtraPortType
object MacroExtraPortType {
  implicit def toMacroExtraPortType(s: Any): Option[MacroExtraPortType] = {
    s match {
      case "constant" => Some(Constant)
      case _          => None
    }
  }

  implicit def toString(t: MacroExtraPortType): String = {
    t match {
      case Constant => "constant"
      case _        => ""
    }
  }
}

// Extra port in SRAM
case class MacroExtraPort(
  name:     String,
  width:    Int,
  portType: MacroExtraPortType,
  value:    BigInt) {
  def toJSON(): JsObject = {
    JsObject(
      Seq(
        "name" -> Json.toJson(name),
        "width" -> Json.toJson(width),
        "type" -> JsString(MacroExtraPortType.toString(portType)),
        "value" -> JsNumber(BigDecimal(value))
      )
    )
  }
}
object MacroExtraPort {
  def parseJSON(json: Map[String, JsValue]): Option[MacroExtraPort] = {
    val name = json.get("name") match {
      case Some(x: JsString) => x.value
      case _ => return None
    }
    val width = json.get("width") match {
      case Some(x: JsNumber) => x.value.intValue
      case _ => return None
    }
    val portType: MacroExtraPortType = json.get("type") match {
      case Some(x: JsString) =>
        MacroExtraPortType.toMacroExtraPortType(x.value) match {
          case Some(t: MacroExtraPortType) => t
          case _ => return None
        }
      case _ => return None
    }
    val value = json.get("value") match {
      case Some(x: JsNumber) => x.value.toBigInt
      case _ => return None
    }
    Some(MacroExtraPort(name, width, portType, value))
  }
}

// A named port that also has polarity.
case class PolarizedPort(name: String, polarity: PortPolarity) {
  def toSeqMap(prefix: String): Seq[Tuple2[String, JsValue]] = {
    Seq(
      prefix + " port name" -> Json.toJson(name),
      prefix + " port polarity" -> JsString(polarity)
    )
  }
}
object PolarizedPort {
  // Parse a pair of "<prefix> port name" and "<prefix> port polarity" keys into a
  // polarized port definition.
  def parseJSON(json: Map[String, JsValue], prefix: String): Option[PolarizedPort] = {
    val name = json.get(prefix + " port name") match {
      case Some(x: JsString) => Some(x.value)
      case _ => None
    }
    val polarity: Option[PortPolarity] = json.get(prefix + " port polarity") match {
      case Some(x: JsString) => Some(x.value)
      case _ => None
    }

    (name, polarity) match {
      case (Some(n: String), Some(p: PortPolarity)) => Some(PolarizedPort(n, p))
      case _ => None
    }
  }
}

// A SRAM memory port
case class MacroPort(
  address:     PolarizedPort,
  clock:       Option[PolarizedPort] = None,
  writeEnable: Option[PolarizedPort] = None,
  readEnable:  Option[PolarizedPort] = None,
  chipEnable:  Option[PolarizedPort] = None,
  output:      Option[PolarizedPort] = None,
  input:       Option[PolarizedPort] = None,
  maskPort:    Option[PolarizedPort] = None,
  maskGran:    Option[Int] = None,
  // For internal use only; these aren't port-specific.
  width: Option[Int],
  depth: Option[BigInt]) {
  def effectiveMaskGran = maskGran.getOrElse(width.get)

  def toJSON(): JsObject = {
    val keys: Seq[Tuple2[String, Option[Any]]] = Seq(
      "address" -> Some(address),
      "clock" -> clock,
      "write enable" -> writeEnable,
      "read enable" -> readEnable,
      "chip enable" -> chipEnable,
      "output" -> output,
      "input" -> input,
      "mask" -> maskPort,
      "mask granularity" -> maskGran
    )
    JsObject(keys.flatMap(k => {
      val (key, value) = k
      value match {
        case Some(x: Int) => Seq(key -> JsNumber(x))
        case Some(x: PolarizedPort) => x.toSeqMap(key)
        case _ => List()
      }
    }))
  }

  // Check that all port names are unique.
  private val polarizedPorts =
    List(Some(address), clock, writeEnable, readEnable, chipEnable, output, input, maskPort).flatten
  assert(polarizedPorts.distinct.size == polarizedPorts.size, "All port names must be unique")
}
object MacroPort {
  def parseJSON(json: Map[String, JsValue]): Option[MacroPort] = parseJSON(json, None, None)
  def parseJSON(json: Map[String, JsValue], width: Int, depth: BigInt): Option[MacroPort] =
    parseJSON(json, Some(width), Some(depth))
  def parseJSON(json: Map[String, JsValue], width: Option[Int], depth: Option[BigInt]): Option[MacroPort] = {
    val address = PolarizedPort.parseJSON(json, "address")
    if (address == None) {
      return None
    }

    val clock = PolarizedPort.parseJSON(json, "clock")
    // TODO: validate based on family (e.g. 1rw must have a write enable, etc)
    val writeEnable = PolarizedPort.parseJSON(json, "write enable")
    val readEnable = PolarizedPort.parseJSON(json, "read enable")
    val chipEnable = PolarizedPort.parseJSON(json, "chip enable")

    val output = PolarizedPort.parseJSON(json, "output")
    val input = PolarizedPort.parseJSON(json, "input")

    val maskPort = PolarizedPort.parseJSON(json, "mask")
    val maskGran: Option[Int] = json.get("mask granularity") match {
      case Some(x: JsNumber) => Some(x.value.intValue)
      case _ => None
    }

    if (maskPort.isDefined != maskGran.isDefined) {
      return None
    }

    Some(
      MacroPort(
        width = width,
        depth = depth,
        address = address.get,
        clock = clock,
        writeEnable = writeEnable,
        readEnable = readEnable,
        chipEnable = chipEnable,
        output = output,
        input = input,
        maskPort = maskPort,
        maskGran = maskGran
      )
    )
  }
}

// Port polarity
trait PortPolarity
case object ActiveLow extends PortPolarity
case object ActiveHigh extends PortPolarity
case object NegativeEdge extends PortPolarity
case object PositiveEdge extends PortPolarity
object PortPolarity {
  implicit def toPortPolarity(s: String): PortPolarity = (s: @unchecked) match {
    case "active low"    => ActiveLow
    case "active high"   => ActiveHigh
    case "negative edge" => NegativeEdge
    case "positive edge" => PositiveEdge
  }
  implicit def toPortPolarity(s: Option[String]): Option[PortPolarity] =
    s.map(toPortPolarity)

  implicit def toString(p: PortPolarity): String = {
    p match {
      case ActiveLow    => "active low"
      case ActiveHigh   => "active high"
      case NegativeEdge => "negative edge"
      case PositiveEdge => "positive edge"
    }
  }
}
