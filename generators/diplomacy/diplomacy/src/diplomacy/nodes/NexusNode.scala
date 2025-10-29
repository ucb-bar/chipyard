package org.chipsalliance.diplomacy.nodes

import chisel3.Data

import org.chipsalliance.diplomacy.ValName

/** [[MixedNexusNode]] is used when the number of nodes connecting from either side is unknown (e.g. a Crossbar which
  * also is a protocol adapter).
  *
  * The [[NodeImp]] is different between [[inner]] and [[outer]],
  *
  * @param dFn
  *   Function for mapping the parameters flowing downward into new outward flowing down parameters.
  * @param uFn
  *   Function for mapping the parameters flowing upward into new inward flowing up parameters.
  * @param inputRequiresOutput
  *   True if it is required that if there are input connections, there are output connections (this node can't just be
  *   a sink).
  * @param outputRequiresInput
  *   True if it is required that if there are output connections, there are input connections (this node can't just be
  *   a source).
  */
class MixedNexusNode[DI, UI, EI, BI <: Data, DO, UO, EO, BO <: Data](
  inner:               InwardNodeImp[DI, UI, EI, BI],
  outer:               OutwardNodeImp[DO, UO, EO, BO]
)(dFn:                 Seq[DI] => DO,
  uFn:                 Seq[UO] => UI,
  // no inputs and no outputs is always allowed
  inputRequiresOutput: Boolean = true,
  outputRequiresInput: Boolean = true
)(
  implicit valName:    ValName)
    extends MixedNode(inner, outer) {
  override def description = "nexus"
  protected[diplomacy] def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = {
    // a nexus treats :=* as a weak pointer
    def resolveStarInfo: String = s"""$context
                                     |$bindingInfo
                                     |number of known := bindings to inward nodes: $iKnown
                                     |number of known := bindings to outward nodes: $oKnown
                                     |number of binding queries from inward nodes: $iStars
                                     |number of binding queries from outward nodes: $oStars
                                     |""".stripMargin
    require(
      !outputRequiresInput || oKnown == 0 || iStars + iKnown != 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has $oKnown outward connections and no inward connections. At least one inward connection was required.
         |$resolveStarInfo
         |""".stripMargin
    )
    require(
      !inputRequiresOutput || iKnown == 0 || oStars + oKnown != 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node node has $iKnown inward connections and no outward connections. At least one outward connection was required.
         |$resolveStarInfo
         |""".stripMargin
    )
    if (iKnown == 0 && oKnown == 0) (0, 0)
    else (1, 1)
  }
  protected[diplomacy] def mapParamsD(n: Int, p: Seq[DI]):                                  Seq[DO]    = {
    if (n > 0) { val a = dFn(p); Seq.fill(n)(a) }
    else Nil
  }
  protected[diplomacy] def mapParamsU(n: Int, p: Seq[UO]):                                  Seq[UI]    = {
    if (n > 0) { val a = uFn(p); Seq.fill(n)(a) }
    else Nil
  }
}

/** [[NexusNode]] is a [[MixedNexusNode]], in which the inward and outward side of the node have the same [[NodeImp]]
  * implementation.
  */
class NexusNode[D, U, EO, EI, B <: Data](
  imp:                 NodeImp[D, U, EO, EI, B]
)(dFn:                 Seq[D] => D,
  uFn:                 Seq[U] => U,
  inputRequiresOutput: Boolean = true,
  outputRequiresInput: Boolean = true
)(
  implicit valName:    ValName)
    extends MixedNexusNode[D, U, EI, B, D, U, EO, B](imp, imp)(dFn, uFn, inputRequiresOutput, outputRequiresInput)
