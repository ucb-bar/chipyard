package org.chipsalliance.diplomacy

import chisel3.{Aggregate, Data, Element}
import org.chipsalliance.cde.config.Parameters

import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import org.chipsalliance.diplomacy.nodes.{InwardNodeHandle, NodeHandle, OutwardNodeHandle}

package object bundlebridge {

  def BundleBridgeNameNode[T <: Data](name: String): BundleBridgeIdentityNode[T] =
    BundleBridgeIdentityNode[T]()(ValName(name))

  def BundleBroadcast[T <: Data](
    name:                Option[String] = None,
    registered:          Boolean = false,
    default:             Option[() => T] = None,
    inputRequiresOutput: Boolean = false, // when false, connecting a source does not mandate connecting a sink
    shouldBeInlined:     Boolean = true
  )(
    implicit p:          Parameters
  ): BundleBridgeNexusNode[T] = {
    val broadcast = LazyModule(
      new BundleBridgeNexus[T](
        inputFn = BundleBridgeNexus.requireOne[T](registered),
        outputFn = BundleBridgeNexus.fillN[T](registered),
        default = default,
        inputRequiresOutput = inputRequiresOutput,
        shouldBeInlined = shouldBeInlined
      )
    )

    name.foreach(broadcast.suggestName)
    broadcast.node
  }

  private[bundlebridge] def getElements[T <: Data](x: T): Seq[Element] = x match {
    case e: Element   => Seq(e)
    case a: Aggregate => a.getElements.flatMap(getElements)
  }

  type BundleBridgeInwardNode[T <: Data] = InwardNodeHandle[BundleBridgeParams[T], BundleBridgeParams[
    T
  ], BundleBridgeEdgeParams[T], T]

  type BundleBridgeOutwardNode[T <: Data] = OutwardNodeHandle[BundleBridgeParams[T], BundleBridgeParams[
    T
  ], BundleBridgeEdgeParams[T], T]

  type BundleBridgeNode[T <: Data] = NodeHandle[BundleBridgeParams[T], BundleBridgeParams[T], BundleBridgeEdgeParams[
    T
  ], T, BundleBridgeParams[T], BundleBridgeParams[T], BundleBridgeEdgeParams[T], T]
}
