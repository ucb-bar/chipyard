// See LICENSE for license details.

package barstools.macros

import firrtl._
import firrtl.ir._
import firrtl.PrimOps
import firrtl.Utils.{ceilLog2, BoolType}
import mdf.macrolib.{Constant, MacroPort, SRAMMacro}
import mdf.macrolib.{PolarizedPort, PortPolarity, ActiveLow, ActiveHigh, NegativeEdge, PositiveEdge, MacroExtraPort}
import java.io.File
import scala.language.implicitConversions

class FirrtlMacroPort(port: MacroPort) {
  val src = port

  val isReader = port.output.nonEmpty && port.input.isEmpty
  val isWriter = port.input.nonEmpty && port.output.isEmpty
  val isReadWriter = port.input.nonEmpty && port.output.nonEmpty

  val addrType = UIntType(IntWidth(ceilLog2(port.depth.get) max 1))
  val dataType = UIntType(IntWidth(port.width.get))
  val maskType = UIntType(IntWidth(port.width.get / port.effectiveMaskGran))

  // Bundle representing this macro port.
  val tpe = BundleType(Seq(
    Field(port.address.name, Flip, addrType)) ++
    (port.clock map (p => Field(p.name, Flip, ClockType))) ++
    (port.input map (p => Field(p.name, Flip, dataType))) ++
    (port.output map (p => Field(p.name, Default, dataType))) ++
    (port.chipEnable map (p => Field(p.name, Flip, BoolType))) ++
    (port.readEnable map (p => Field(p.name, Flip, BoolType))) ++
    (port.writeEnable map (p => Field(p.name, Flip, BoolType))) ++
    (port.maskPort map (p => Field(p.name, Flip, maskType)))
  )
  val ports = tpe.fields map (f => Port(
     NoInfo, f.name, f.flip match { case Default => Output case Flip => Input }, f.tpe))
}

// Reads an SRAMMacro and generates firrtl blackboxes.
class Macro(srcMacro: SRAMMacro) {
  val src = srcMacro

  val firrtlPorts = srcMacro.ports map { new FirrtlMacroPort(_) }

  val writers = firrtlPorts filter (p => p.isWriter)
  val readers = firrtlPorts filter (p => p.isReader)
  val readwriters = firrtlPorts filter (p => p.isReadWriter)

  val sortedPorts = writers ++ readers ++ readwriters
  val extraPorts = srcMacro.extraPorts map { p =>
    assert(p.portType == Constant) // TODO: release it?
    val name = p.name
    val width = BigInt(p.width.toLong)
    val value = BigInt(p.value.toLong)
    (name -> UIntLiteral(value, IntWidth(width)))
  }

  // Bundle representing this memory blackbox
  val tpe = BundleType(firrtlPorts flatMap (_.tpe.fields))

  private val modPorts = (firrtlPorts flatMap (_.ports)) ++
    (extraPorts map { case (name, value) => Port(NoInfo, name, Input, value.tpe) })
  val blackbox = ExtModule(NoInfo, srcMacro.name, modPorts, srcMacro.name, Nil)
  def module(body: Statement) = Module(NoInfo, srcMacro.name, modPorts, body)
}

object Utils {
  def filterForSRAM(s: Option[Seq[mdf.macrolib.Macro]]): Option[Seq[mdf.macrolib.SRAMMacro]] = {
    s match {
      case Some(l:Seq[mdf.macrolib.Macro]) => Some(l filter { _.isInstanceOf[mdf.macrolib.SRAMMacro] } map { m => m.asInstanceOf[mdf.macrolib.SRAMMacro] })
      case _ => None
    }
  }
  // This utility reads a conf in and returns MDF like mdf.macrolib.Utils.readMDFFromPath
  def readConfFromPath(path: Option[String]): Option[Seq[mdf.macrolib.Macro]] = {
    path.map((p) => Utils.readConfFromString(scala.io.Source.fromFile(p).mkString))
  }
  def readConfFromString(str: String): Seq[mdf.macrolib.Macro] = {
    MemConf.fromString(str).map { m:MemConf =>
      SRAMMacro(m.name, m.width, m.depth, Utils.portSpecToFamily(m.ports), Utils.portSpecToMacroPort(m.width, m.depth, m.maskGranularity, m.ports), Seq.empty[MacroExtraPort])
    }
  }
  def portSpecToFamily(ports: Seq[MemPort]): String = {
    val numR = ports.count(_ match { case ReadPort => true; case _ => false})
    val numW = ports.count(_ match { case WritePort|MaskedWritePort => true; case _ => false})
    val numRW = ports.count(_ match { case ReadWritePort|MaskedReadWritePort => true; case _ => false})
    val numRStr = if(numR > 0) s"${numR}r" else ""
    val numWStr = if(numW > 0) s"${numW}w" else ""
    val numRWStr = if(numRW > 0) s"${numRW}rw" else ""
    return numRStr + numWStr + numRWStr
  }
  // This translates between two represenations of ports
  def portSpecToMacroPort(width: Int, depth: Int, maskGran: Option[Int], ports: Seq[MemPort]): Seq[MacroPort] = {
    var numR = 0
    var numW = 0
    var numRW = 0
    ports.map { _ match {
      case ReadPort => {
        val portName = s"R${numR}"
        numR += 1
        MacroPort(
          width=Some(width), depth=Some(depth),
          address=PolarizedPort(s"${portName}_addr", ActiveHigh),
          clock=Some(PolarizedPort(s"${portName}_clk", PositiveEdge)),
          chipEnable=Some(PolarizedPort(s"${portName}_en", ActiveHigh)),
          output=Some(PolarizedPort(s"${portName}_data", ActiveHigh))
        ) }
      case WritePort => {
        val portName = s"W${numW}"
        numW += 1
        MacroPort(
          width=Some(width), depth=Some(depth),
          address=PolarizedPort(s"${portName}_addr", ActiveHigh),
          clock=Some(PolarizedPort(s"${portName}_clk", PositiveEdge)),
          writeEnable=Some(PolarizedPort(s"${portName}_en", ActiveHigh)),
          input=Some(PolarizedPort(s"${portName}_data", ActiveHigh))
        ) }
      case MaskedWritePort => {
        val portName = s"W${numW}"
        numW += 1
        MacroPort(
          width=Some(width), depth=Some(depth),
          address=PolarizedPort(s"${portName}_addr", ActiveHigh),
          clock=Some(PolarizedPort(s"${portName}_clk", PositiveEdge)),
          writeEnable=Some(PolarizedPort(s"${portName}_en", ActiveHigh)),
          maskPort=Some(PolarizedPort(s"${portName}_mask", ActiveHigh)),
          maskGran=maskGran,
          input=Some(PolarizedPort(s"${portName}_data", ActiveHigh))
        ) }
      case ReadWritePort => {
        val portName = s"RW${numRW}"
        numRW += 1
        MacroPort(
          width=Some(width), depth=Some(depth),
          address=PolarizedPort(s"${portName}_addr", ActiveHigh),
          clock=Some(PolarizedPort(s"${portName}_clk", PositiveEdge)),
          chipEnable=Some(PolarizedPort(s"${portName}_en", ActiveHigh)),
          writeEnable=Some(PolarizedPort(s"${portName}_wmode", ActiveHigh)),
          input=Some(PolarizedPort(s"${portName}_wdata", ActiveHigh)),
          output=Some(PolarizedPort(s"${portName}_rdata", ActiveHigh))
        ) }
      case MaskedReadWritePort => {
        val portName = s"RW${numRW}"
        numRW += 1
        MacroPort(
          width=Some(width), depth=Some(depth),
          address=PolarizedPort(s"${portName}_addr", ActiveHigh),
          clock=Some(PolarizedPort(s"${portName}_clk", PositiveEdge)),
          chipEnable=Some(PolarizedPort(s"${portName}_en", ActiveHigh)),
          writeEnable=Some(PolarizedPort(s"${portName}_wmode", ActiveHigh)),
          maskPort=Some(PolarizedPort(s"${portName}_wmask", ActiveHigh)),
          maskGran=maskGran,
          input=Some(PolarizedPort(s"${portName}_wdata", ActiveHigh)),
          output=Some(PolarizedPort(s"${portName}_rdata", ActiveHigh))
        ) }
    }}
  }
  def findSRAMCompiler(s: Option[Seq[mdf.macrolib.Macro]]): Option[mdf.macrolib.SRAMCompiler] = {
    s match {
      case Some(l:Seq[mdf.macrolib.Macro]) =>
        l collectFirst {
          case x: mdf.macrolib.SRAMCompiler => x
        }
      case _ => None
    }
  }
  def buildSRAMMacros(s: mdf.macrolib.SRAMCompiler): Seq[mdf.macrolib.SRAMMacro] = {
    for (g <- s.groups; d <- g.depth; w <- g.width; vt <- g.vt)
      yield mdf.macrolib.SRAMMacro(makeName(g, d, w, vt), w, d, g.family, g.ports.map(_.copy(width=Some(w), depth=Some(d))), g.extraPorts)
  }
  def buildSRAMMacro(g: mdf.macrolib.SRAMGroup, d: Int, w: Int, vt: String): mdf.macrolib.SRAMMacro = {
    return mdf.macrolib.SRAMMacro(makeName(g, d, w, vt), w, d, g.family, g.ports.map(_.copy(width=Some(w), depth=Some(d))), g.extraPorts)
  }
  def makeName(g: mdf.macrolib.SRAMGroup, depth: Int, width: Int, vt: String): String = {
    g.name.foldLeft(""){ (builder, next) =>
      next match {
        case "depth"|"DEPTH" => builder + depth
        case "width"|"WIDTH" => builder + width
        case "vt" => builder + vt.toLowerCase
        case "VT" => builder + vt.toUpperCase
        case "family" => builder + g.family.toLowerCase
        case "FAMILY" => builder + g.family.toUpperCase
        case "mux"|"MUX" => builder + g.mux
        case other => builder + other
      }
    }
  }

  def and(e1: Expression, e2: Expression) =
    DoPrim(PrimOps.And, Seq(e1, e2), Nil, e1.tpe)
  def or(e1: Expression, e2: Expression) =
    DoPrim(PrimOps.Or, Seq(e1, e2), Nil, e1.tpe)
  def bits(e: Expression, high: BigInt, low: BigInt): Expression =
    DoPrim(PrimOps.Bits, Seq(e), Seq(high, low), UIntType(IntWidth(high-low+1)))
  def bits(e: Expression, idx: BigInt): Expression = bits(e, idx, idx)
  def cat(es: Seq[Expression]): Expression =
    if (es.size == 1) es.head
    else DoPrim(PrimOps.Cat, Seq(es.head, cat(es.tail)), Nil, UnknownType)
  def not(e: Expression) =
    DoPrim(PrimOps.Not, Seq(e), Nil, e.tpe)

  // Convert a port to a FIRRTL expression, handling polarity along the way.
  def portToExpression(pp: PolarizedPort): Expression =
    portToExpression(WRef(pp.name), Some(pp.polarity))

  def portToExpression(exp: Expression, polarity: Option[PortPolarity]): Expression =
    polarity match {
      case Some(ActiveLow) | Some(NegativeEdge) => not(exp)
      case _ => exp
    }

  // Check if a number is a power of two
  def isPowerOfTwo(x: Int): Boolean = (x & (x - 1)) == 0
}
