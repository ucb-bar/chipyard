package org.chipsalliance.diplomacy.nodes

import chisel3.{Data, IO}

import org.chipsalliance.diplomacy.ValName

/** A node which represents a node in the graph which has only inward edges, no outward edges.
  *
  * A [[SinkNode]] cannot appear cannot appear right of a `:=`, `:*=`, `:=*`, or `:*=*`
  *
  * There are no "Mixed" [[SinkNode]]s because each one only has an inward side.
  */
class SinkNode[D, U, EO, EI, B <: Data](
  imp:              NodeImp[D, U, EO, EI, B]
)(pi:               Seq[U]
)(
  implicit valName: ValName)
    extends MixedNode(imp, imp) {
  override def description = "sink"
  protected[diplomacy] def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = {
    def resolveStarInfo: String = s"""$context
                                     |$bindingInfo
                                     |number of known := bindings to inward nodes: $iKnown
                                     |number of known := bindings to outward nodes: $oKnown
                                     |number of binding queries from inward nodes: $iStars
                                     |number of binding queries from outward nodes: $oStars
                                     |${pi.size} inward parameters: [${pi.map(_.toString).mkString(",")}]
                                     |""".stripMargin
    require(
      iStars <= 1,
      s"""Diplomacy has detected a problem with your graph:
         |The following node appears left of a :*= $iStars times; at most once is allowed.
         |$resolveStarInfo
         |""".stripMargin
    )
    require(
      oStars == 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node cannot appear right of a :=*
         |$resolveStarInfo
         |""".stripMargin
    )
    require(
      oKnown == 0,
      s"""Diplomacy has detected a problem with your graph:
         |The following node cannot appear right of a :=
         |$resolveStarInfo
         |""".stripMargin
    )
    if (iStars == 0) require(
      pi.size == iKnown,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has $iKnown inward bindings connected to it, but ${pi.size} sinks were specified to the node constructor.
         |Either the number of inward := bindings should be exactly equal to the number of sink, or connect this node on the left-hand side of a :*=
         |$resolveStarInfo
         |""".stripMargin
    )
    else require(
      pi.size >= iKnown,
      s"""Diplomacy has detected a problem with your graph:
         |The following node has $iKnown inward bindings connected to it, but ${pi.size} sinks were specified to the node constructor.
         |To resolve :*=, size of inward parameters can not be less than bindings.
         |$resolveStarInfo
         |""".stripMargin
    )
    (pi.size - iKnown, 0)
  }
  protected[diplomacy] def mapParamsD(n: Int, p: Seq[D]): Seq[D] = Seq()
  protected[diplomacy] def mapParamsU(n: Int, p: Seq[U]): Seq[U] = pi

  def makeIOs(
  )(
    implicit valName: ValName
  ): HeterogeneousBag[B] = {
    val bundles = this.in.map(_._1)
    val ios     = IO(new HeterogeneousBag(bundles))
    ios.suggestName(valName.value)
    bundles.zip(ios).foreach { case (bundle, io) => io <> bundle }
    ios
  }
}
