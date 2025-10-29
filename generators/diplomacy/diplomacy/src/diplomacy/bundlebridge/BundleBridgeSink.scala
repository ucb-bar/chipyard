package org.chipsalliance.diplomacy.bundlebridge

import chisel3.{chiselTypeOf, ActualDirection, Data, IO, Output}
import chisel3.reflect.DataMirror
import chisel3.reflect.DataMirror.internal.chiselTypeClone

import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.nodes.SinkNode

case class BundleBridgeSink[T <: Data](
  genOpt:           Option[() => T] = None
)(
  implicit valName: ValName)
    extends SinkNode(new BundleBridgeImp[T])(Seq(BundleBridgeParams(genOpt))) {
  def bundle: T = in(0)._1

  private def inferOutput = getElements(bundle).forall { elt =>
    DataMirror.directionOf(elt) == ActualDirection.Unspecified
  }

  def makeIO(
  )(
    implicit valName: ValName
  ): T = {
    val io: T = IO(
      if (inferOutput) Output(chiselTypeOf(bundle))
      else chiselTypeClone(bundle)
    )
    io.suggestName(valName.value)
    io <> bundle
    io
  }
  def makeIO(name: String): T = makeIO()(ValName(name))
}

object BundleBridgeSink {
  def apply[T <: Data](
  )(
    implicit valName: ValName
  ): BundleBridgeSink[T] = {
    BundleBridgeSink(None)
  }
}
