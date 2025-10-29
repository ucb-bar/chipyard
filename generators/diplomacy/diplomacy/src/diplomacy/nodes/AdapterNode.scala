package org.chipsalliance.diplomacy.nodes

import chisel3.Data

import org.chipsalliance.diplomacy.ValName

/** [[MixedAdapterNode]] is used to transform between different diplomacy protocols ([[NodeImp]]), without changing the
  * number of edges passing through it.
  *
  * For example, a [[MixedAdapterNode]] is needed for a TL to AXI bridge (interface).
  * {{{
  *   case class TLToAXI4Node(stripBits: Int = 0)(implicit valName: ValName) extends MixedAdapterNode(TLImp, AXI4Imp)
  * }}}
  *
  * @param dFn
  *   convert downward parameter from input to output.
  * @param uFn
  *   convert upward parameter from output to input.
  */
class MixedAdapterNode[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data](
  inner:            InwardNodeImp[DI, UI, EI, BI],
  outer:            OutwardNodeImp[DO, UO, EO, BO]
)(dFn:              DI => DO,
  uFn:              UO => UI
)(
  implicit valName: ValName)
    extends MixedNode(inner, outer) {
  override def description                                 = "adapter"
  protected[diplomacy] override def flexibleArityDirection = true
  protected[diplomacy] def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = {
    require(
      oStars + iStars <= 1,
      s"""Diplomacy has detected a problem with your graph:
         |The following node appears left of a :*= $iStars times and right of a :=* $oStars times, at most once is allowed.
         |$context
         |$bindingInfo
         |""".stripMargin
    )
    if (oStars > 0) {
      require(
        iKnown >= oKnown,
        s"""Diplomacy has detected a problem with your graph:
           |After being connected right of :=*, the following node appears left of a := $iKnown times and right of a := $oKnown times.
           |${iKnown - oKnown} additional right of := bindings are required to resolve :=* successfully.
           |$context
           |$bindingInfo
           |""".stripMargin
      )
      (0, iKnown - oKnown)
    } else if (iStars > 0) {
      require(
        oKnown >= iKnown,
        s"""Diplomacy has detected a problem with your graph:
           |After being connected left of :*=, the following node appears left of a := $iKnown times and right of a := $oKnown times.
           |${oKnown - iKnown} additional left := bindings are required to resolve :*= successfully.
           |$context
           |$bindingInfo
           |""".stripMargin
      )
      (oKnown - iKnown, 0)
    } else {
      require(
        oKnown == iKnown,
        s"""Diplomacy has detected a problem with your graph:
           |The following node appears left of a := $iKnown times and right of a := $oKnown times.
           |Either the number of bindings on both sides of the node match, or connect this node by left-hand side of :*= or right-hand side of :=*
           |$context
           |$bindingInfo
           |""".stripMargin
      )
      (0, 0)
    }
  }
  protected[diplomacy] def mapParamsD(n: Int, p: Seq[DI]):                                  Seq[DO]    = {
    require(
      n == p.size,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has ${p.size} inputs and $n outputs, they must match.
         |$context
         |$bindingInfo
         |""".stripMargin
    )
    p.map(dFn)
  }
  protected[diplomacy] def mapParamsU(n: Int, p: Seq[UO]):                                  Seq[UI]    = {
    require(
      n == p.size,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has $n inputs and ${p.size} outputs, they must match
         |$context
         |$bindingInfo
         |""".stripMargin
    )
    p.map(uFn)
  }
}

/** A node which modifies the parameters flowing through it, but without changing the number of edges or the diplomatic
  * protocol implementation.
  */
class AdapterNode[D, U, EO, EI, B <: Data](
  imp:              NodeImp[D, U, EO, EI, B]
)(dFn:              D => D,
  uFn:              U => U
)(
  implicit valName: ValName)
    extends MixedAdapterNode[D, U, EI, B, D, U, EO, B](imp, imp)(dFn, uFn)
