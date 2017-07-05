// See LICENSE for license details.

package barstools.tapeout.transforms.macros

import firrtl._
import firrtl.ir._
import firrtl.PrimOps
import firrtl.Utils.{ceilLog2, BoolType}
import scala.util.parsing.json.JSON // Todo: this will be gone
import java.io.File
import scala.language.implicitConversions

trait PortPolarity
case object ActiveLow extends PortPolarity
case object ActiveHigh extends PortPolarity
case object NegativeEdge extends PortPolarity
case object PositiveEdge extends PortPolarity
object PortPolarity {
  implicit def toPortPolarity(s: Any): PortPolarity =
    (s: @unchecked) match {
      case "active low" => ActiveLow
      case "active high" => ActiveHigh
      case "negative edge" => NegativeEdge 
      case "positive edge" => PositiveEdge
    }
  implicit def toPortPolarity(s: Option[Any]): Option[PortPolarity] =
    s map toPortPolarity
}

case class MacroPort(
    clockName: String,
    clockPolarity: Option[PortPolarity],
    addressName: String,
    addressPolarity: Option[PortPolarity],
    inputName: Option[String],
    inputPolarity: Option[PortPolarity],
    outputName: Option[String],
    outputPolarity: Option[PortPolarity],
    chipEnableName: Option[String],
    chipEnablePolarity: Option[PortPolarity],
    readEnableName: Option[String],
    readEnablePolarity: Option[PortPolarity],
    writeEnableName: Option[String],
    writeEnablePolarity: Option[PortPolarity],
    maskName: Option[String],
    maskPolarity: Option[PortPolarity],
    maskGran: Option[BigInt],
    width: BigInt,
    depth: BigInt) {
  val effectiveMaskGran = maskGran.getOrElse(width)
  val AddrType = UIntType(IntWidth(ceilLog2(depth) max 1))
  val DataType = UIntType(IntWidth(width))
  val MaskType = UIntType(IntWidth(width / effectiveMaskGran))
  val tpe = BundleType(Seq(
    Field(clockName, Flip, ClockType),
    Field(addressName, Flip, AddrType)) ++
    (inputName map (Field(_, Flip, DataType))) ++
    (outputName map (Field(_, Default, DataType))) ++
    (chipEnableName map (Field(_, Flip, BoolType))) ++
    (readEnableName map (Field(_, Flip, BoolType))) ++
    (writeEnableName map (Field(_, Flip, BoolType))) ++
    (maskName map (Field(_, Flip, MaskType)))
  )
  val ports = tpe.fields map (f => Port(
     NoInfo, f.name, f.flip match { case Default => Output case Flip => Input }, f.tpe))
}

class Macro(lib: Map[String, Any]) {
  val name = lib("name").asInstanceOf[String]
  val width = BigInt(lib("width").asInstanceOf[Double].toLong)
  val depth = BigInt(lib("depth").asInstanceOf[Double].toLong)
  val ports = lib("ports").asInstanceOf[List[_]] map { x =>
    val map = x.asInstanceOf[Map[String, Any]]
    MacroPort(
      map("clock port name").asInstanceOf[String],
      map get "clock port polarity",
      map("address port name").asInstanceOf[String],
      map get "address port polarity",
      map get "input port name" map (_.asInstanceOf[String]),
      map get "input port polarity",
      map get "output port name" map (_.asInstanceOf[String]),
      map get "output port polarity",
      map get "chip enable port name" map (_.asInstanceOf[String]),
      map get "chip enable port polarity",
      map get "read enable port name" map (_.asInstanceOf[String]),
      map get "read enable port polarity",
      map get "write enable port name" map (_.asInstanceOf[String]),
      map get "write enable port polarity",
      map get "mask port name" map (_.asInstanceOf[String]),
      map get "mask port polarity",
      map get "mask granularity" map (x => BigInt(x.asInstanceOf[Double].toLong)),
      width,
      depth
    )
  }
  val writers = ports filter (p => p.inputName.isDefined && !p.outputName.isDefined)
  val readers = ports filter (p => !p.inputName.isDefined && p.outputName.isDefined)
  val readwriters = ports filter (p => p.inputName.isDefined && p.outputName.isDefined)
  val sortedPorts = writers ++ readers ++ readwriters
  val extraPorts = lib get "extra ports" match {
    case None => Nil
    case Some(p) => p.asInstanceOf[List[_]] map { x =>
      val map = x.asInstanceOf[Map[String, Any]]
      assert(map("type").asInstanceOf[String] == "constant") // TODO: release it?
      val name = map("name").asInstanceOf[String]
      val width = BigInt(map("width").asInstanceOf[Double].toLong)
      val value = BigInt(map("value").asInstanceOf[Double].toLong)
      (name -> UIntLiteral(value, IntWidth(width)))
    }
  }
  val tpe = BundleType(ports flatMap (_.tpe.fields))
  private val modPorts = (ports flatMap (_.ports)) ++
    (extraPorts map { case (name, value) => Port(NoInfo, name, Input, value.tpe) })
  val blackbox = ExtModule(NoInfo, name, modPorts, name, Nil)
  def module(body: Statement) = Module(NoInfo, name, modPorts, body)
}

object Utils {
  def readJSON(file: Option[File]): Option[Seq[Map[String, Any]]] = file match {
    case None => None
    case Some(f) => try {
      (JSON parseFull io.Source.fromFile(f).mkString) match {
        case Some(p: List[Any]) => Some(
          (p foldLeft Seq[Map[String, Any]]()){
            case (res, x: Map[_, _]) =>
              val map = x.asInstanceOf[Map[String, Any]]
              if (map("type").asInstanceOf[String] == "sram") res :+ map else res
            case (res, _) => res
          }
        )
        case _ => Some(Nil)
      }
    } catch {
      case _: Throwable => Some(Nil)
    }
  }

  def and(e1: Expression, e2: Expression) =
    DoPrim(PrimOps.And, Seq(e1, e2), Nil, e1.tpe)
  def bits(e: Expression, high: BigInt, low: BigInt): Expression =
    DoPrim(PrimOps.Bits, Seq(e), Seq(high, low), UIntType(IntWidth(high-low+1)))
  def bits(e: Expression, idx: BigInt): Expression = bits(e, idx, idx)
  def cat(es: Seq[Expression]): Expression =
    if (es.size == 1) es.head
    else DoPrim(PrimOps.Cat, Seq(es.head, cat(es.tail)), Nil, UnknownType)
  def not(e: Expression) =
    DoPrim(PrimOps.Not, Seq(e), Nil, e.tpe)

  def invert(exp: Expression, polarity: Option[PortPolarity]) =
    polarity match {
      case Some(ActiveLow) | Some(NegativeEdge) => not(exp)
      case _ => exp
    }
}
