package org.chipsalliance.diplomacy.bundlebridge

import chisel3.Data

case class BundleBridgeParams[T <: Data](genOpt: Option[() => T])

case object BundleBridgeParams {
  def apply[T <: Data](gen: () => T): BundleBridgeParams[T] = BundleBridgeParams(Some(gen))
}

case class BundleBridgeEdgeParams[T <: Data](source: BundleBridgeParams[T], sink: BundleBridgeParams[T])
