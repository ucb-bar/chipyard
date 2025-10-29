package org.chipsalliance.diplomacy.nodes

import chisel3.Data

import org.chipsalliance.diplomacy.ValName

/** A [[MixedNode]] that may be extended with custom behavior. */
abstract class MixedCustomNode[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data](
  inner:            InwardNodeImp[DI, UI, EI, BI],
  outer:            OutwardNodeImp[DO, UO, EO, BO]
)(
  implicit valName: ValName)
    extends MixedNode(inner, outer) {
  override def description = "custom"
  def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int)
  def mapParamsD(n:       Int, p:      Seq[DI]): Seq[DO]
  def mapParamsU(n:       Int, p:      Seq[UO]): Seq[UI]
}

/** A [[NodeImp]] that may be extended with custom behavior.
  *
  * Different from a [[MixedNode]] in that the inner and outer [[NodeImp]]s are the same.
  */
abstract class CustomNode[D, U, EO, EI, B <: Data](
  imp:              NodeImp[D, U, EO, EI, B]
)(
  implicit valName: ValName)
    extends MixedCustomNode(imp, imp)
