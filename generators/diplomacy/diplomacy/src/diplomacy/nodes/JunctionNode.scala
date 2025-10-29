package org.chipsalliance.diplomacy.nodes

import chisel3.Data

import org.chipsalliance.diplomacy.ValName

/** A JunctionNode creates multiple parallel arbiters.
  *
  * @example
  *   {{{
  *   val jbar = LazyModule(new JBar)
  *   slave1.node := jbar.node
  *   slave2.node := jbar.node
  *   extras.node :=* jbar.node
  *   jbar.node :*= masters1.node
  *   jbar.node :*= masters2.node
  *   }}}
  *
  * In the above example, only the first two connections have their multiplicity specified. All the other connections
  * include a '*' on the JBar's side, so the JBar decides the multiplicity. Thus, in this example, we get 2x crossbars
  * with 2 masters like this: {slave1, extras.1} <= jbar.1 <= {masters1.1, masters2.1} {slave2, extras.2} <= jbar.2 <=
  * {masters1.2, masters2,2}
  *
  * @example
  *   {{{
  *   val jbar = LazyModule(new JBar)
  *   jbar.node :=* masters.node
  *   slaves1.node :=* jbar.node
  *   slaves2.node :=* jbar.node
  *   }}}
  *   In the above example, the first connection takes multiplicity (*) from the right (masters). Supposing masters.node
  *   had 3 edges, this would result in these three arbiters: {slaves1.1, slaves2.1} <= jbar.1 <= { masters.1 }
  *   {slaves1.2, slaves2.2} <= jbar.2 <= { masters.2 } {slaves1.3, slaves2.3} <= jbar.3 <= { masters.3 }
  */
class MixedJunctionNode[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data](
  inner:            InwardNodeImp[DI, UI, EI, BI],
  outer:            OutwardNodeImp[DO, UO, EO, BO]
)(dFn:              Seq[DI] => Seq[DO],
  uFn:              Seq[UO] => Seq[UI]
)(
  implicit valName: ValName)
    extends MixedNode(inner, outer) {
  protected[diplomacy] var multiplicity = 0

  def uRatio: Int = iPorts.size / multiplicity
  def dRatio: Int = oPorts.size / multiplicity

  override def description = "junction"
  protected[diplomacy] def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = {
    require(
      iKnown == 0 || oKnown == 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node appears left of a :=* or a := and right of a :*= or :=. Only one side may drive multiplicity.
         |$context
         |$bindingInfo
         |""".stripMargin
    )
    multiplicity = iKnown.max(oKnown)
    (multiplicity, multiplicity)
  }
  protected[diplomacy] def mapParamsD(n: Int, p: Seq[DI]):                                  Seq[DO]    =
    p.grouped(multiplicity).toList.transpose.map(dFn).transpose.flatten
  protected[diplomacy] def mapParamsU(n: Int, p: Seq[UO]):                                  Seq[UI]    =
    p.grouped(multiplicity).toList.transpose.map(uFn).transpose.flatten

  def inoutGrouped: Seq[(Seq[(BI, EI)], Seq[(BO, EO)])] = {
    val iGroups = in.grouped(multiplicity).toList.transpose
    val oGroups = out.grouped(multiplicity).toList.transpose
    iGroups.zip(oGroups)
  }
}

/** A node type which has a fixed ratio between the number of input edges and output edges.
  *
  * The [[NodeImp]] on either side is the same.
  *
  * One example usage would be for putting down a series of 2:1 arbiters.
  *
  * Suppose you had N banks of L2 and wanted to connect those to two different driver crossbars. In that case you can do
  * this:
  * {{{
  *   l2banks.node :*= jbar.node
  *   jbar.node :*= xbar1.node
  *   jbar.node :*= xbar2.node
  * }}}
  * If the L2 has 4 banks, now there are 4 egress ports on both xbar1 and xbar2 and they are arbitrated by the jbar.
  */
class JunctionNode[D, U, EO, EI, B <: Data](
  imp:              NodeImp[D, U, EO, EI, B]
)(dFn:              Seq[D] => Seq[D],
  uFn:              Seq[U] => Seq[U]
)(
  implicit valName: ValName)
    extends MixedJunctionNode[D, U, EI, B, D, U, EO, B](imp, imp)(dFn, uFn)
