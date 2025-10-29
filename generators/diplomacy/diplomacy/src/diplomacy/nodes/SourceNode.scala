package org.chipsalliance.diplomacy.nodes

import chisel3.{Data, Flipped, IO}

import org.chipsalliance.diplomacy.ValName

/** A node which represents a node in the graph which only has outward edges and no inward edges.
  *
  * A [[SourceNode]] cannot appear left of a `:=`, `:*=`, `:=*, or `:*=*` There are no Mixed [[SourceNode]]s, There are
  * no "Mixed" [[SourceNode]]s because each one only has an outward side.
  */
class SourceNode[D, U, EO, EI, B <: Data](
  imp:              NodeImp[D, U, EO, EI, B]
)(po:               Seq[D]
)(
  implicit valName: ValName)
    extends MixedNode(imp, imp) {

  override def description = "source"
  protected[diplomacy] def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = {
    def resolveStarInfo: String = s"""$context
                                     |$bindingInfo
                                     |number of known := bindings to inward nodes: $iKnown
                                     |number of known := bindings to outward nodes: $oKnown
                                     |number of binding queries from inward nodes: $iStars
                                     |number of binding queries from outward nodes: $oStars
                                     |${po.size} outward parameters: [${po.map(_.toString).mkString(",")}]
                                     |""".stripMargin
    require(
      oStars <= 1,
      s"""Diplomacy has detected a problem with your graph:
         |The following node appears right of a :=* $oStars times; at most once is allowed.
         |$resolveStarInfo
         |""".stripMargin
    )
    require(
      iStars == 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node cannot appear left of a :*=
         |$resolveStarInfo
         |""".stripMargin
    )
    require(
      iKnown == 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node cannot appear left of a :=
         |$resolveStarInfo
         |""".stripMargin
    )
    if (oStars == 0) require(
      po.size == oKnown,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has $oKnown outward bindings connected to it, but ${po.size} sources were specified to the node constructor.
         |Either the number of outward := bindings should be exactly equal to the number of sources, or connect this node on the right-hand side of a :=*
         |$resolveStarInfo
         |""".stripMargin
    )
    else require(
      po.size >= oKnown,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has $oKnown outward bindings connected to it, but ${po.size} sources were specified to the node constructor.
         |To resolve :=*, size of outward parameters can not be less than bindings.
         |$resolveStarInfo
         |""".stripMargin
    )
    (0, po.size - oKnown)
  }
  protected[diplomacy] def mapParamsD(n: Int, p: Seq[D]): Seq[D] = po
  protected[diplomacy] def mapParamsU(n: Int, p: Seq[U]): Seq[U] = Seq()

  def makeIOs(
  )(
    implicit valName: ValName
  ): HeterogeneousBag[B] = {
    val bundles = this.out.map(_._1)
    val ios     = IO(Flipped(new HeterogeneousBag(bundles)))
    ios.suggestName(valName.value)
    bundles.zip(ios).foreach { case (bundle, io) => bundle <> io }
    ios
  }
}
