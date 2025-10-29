package org.chipsalliance.diplomacy.bundlebridge

import chisel3.{chiselTypeOf, ActualDirection, Data, Flipped, IO, Input}
import chisel3.reflect.DataMirror
import chisel3.reflect.DataMirror.internal.chiselTypeClone
import org.chipsalliance.cde.config.Parameters

import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.nodes.SourceNode

case class BundleBridgeSource[T <: Data](
  genOpt:           Option[() => T] = None
)(
  implicit valName: ValName)
    extends SourceNode(new BundleBridgeImp[T])(Seq(BundleBridgeParams(genOpt))) {
  def bundle: T = out(0)._1

  private def inferInput = getElements(bundle).forall { elt =>
    DataMirror.directionOf(elt) == ActualDirection.Unspecified
  }

  def makeIO(
  )(
    implicit valName: ValName
  ): T = {
    val io: T = IO(
      if (inferInput) Input(chiselTypeOf(bundle))
      else Flipped(chiselTypeClone(bundle))
    )
    io.suggestName(valName.value)
    bundle <> io
    io
  }
  def makeIO(name: String): T = makeIO()(ValName(name))

  private var doneSink = false
  def makeSink(
  )(
    implicit p: Parameters
  ) = {
    require(!doneSink, "Can only call makeSink() once")
    doneSink = true
    val sink = BundleBridgeSink[T]()
    sink := this
    sink
  }
}

object BundleBridgeSource {
  def apply[T <: Data](
  )(
    implicit valName: ValName
  ): BundleBridgeSource[T] = {
    BundleBridgeSource(None)
  }
  def apply[T <: Data](
    gen:              () => T
  )(
    implicit valName: ValName
  ): BundleBridgeSource[T] = {
    BundleBridgeSource(Some(gen))
  }
}
