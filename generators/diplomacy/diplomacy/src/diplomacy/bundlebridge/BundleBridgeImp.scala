package org.chipsalliance.diplomacy.bundlebridge

import chisel3._
import chisel3.experimental.SourceInfo
import chisel3.reflect.DataMirror
import chisel3.reflect.DataMirror.internal.chiselTypeClone
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.nodes.{RenderedEdge, SimpleNodeImp}

class BundleBridgeImp[T <: Data]()
    extends SimpleNodeImp[BundleBridgeParams[T], BundleBridgeParams[T], BundleBridgeEdgeParams[T], T] {
  def edge(pd: BundleBridgeParams[T], pu: BundleBridgeParams[T], p: Parameters, sourceInfo: SourceInfo) =
    BundleBridgeEdgeParams(pd, pu)
  def bundle(e: BundleBridgeEdgeParams[T]): T = {
    val sourceOpt = e.source.genOpt.map(_())
    val sinkOpt   = e.sink.genOpt.map(_())
    (sourceOpt, sinkOpt) match {
      case (None, None)       => throw new Exception("BundleBridge needs source or sink to provide bundle generator function")
      case (Some(a), None)    => chiselTypeClone(a)
      case (None, Some(b))    => chiselTypeClone(b)
      case (Some(a), Some(b)) => {
        require(
          DataMirror.checkTypeEquivalence(a, b),
          s"BundleBridge requires doubly-specified source and sink generators to have equivalent Chisel Data types, but got \n$a\n vs\n$b"
        )
        chiselTypeClone(a)
      }
    }
  }
  def render(e: BundleBridgeEdgeParams[T]) = RenderedEdge(colour = "#cccc00" /* yellow */ )
}
