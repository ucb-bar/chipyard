// See LICENSE for license details.

package barstools.macros

import firrtl._
import firrtl.ir._
import firrtl.PrimOps
import firrtl.Utils.{ceilLog2, BoolType}
import mdf.macrolib.{Constant, MacroPort, SRAMMacro}
import mdf.macrolib.{PolarizedPort, PortPolarity, ActiveLow, ActiveHigh, NegativeEdge, PositiveEdge}
import java.io.File
import scala.language.implicitConversions

class FirrtlMacroPort(port: MacroPort) {
  val src = port

  val isReader = port.output.nonEmpty && port.input.isEmpty
  val isWriter = port.input.nonEmpty && port.output.isEmpty
  val isReadWriter = port.input.nonEmpty && port.output.nonEmpty

  val addrType = UIntType(IntWidth(ceilLog2(port.depth) max 1))
  val dataType = UIntType(IntWidth(port.width))
  val maskType = UIntType(IntWidth(port.width / port.effectiveMaskGran))

  // Bundle representing this macro port.
  val tpe = BundleType(Seq(
    Field(port.clock.name, Flip, ClockType),
    Field(port.address.name, Flip, addrType)) ++
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
