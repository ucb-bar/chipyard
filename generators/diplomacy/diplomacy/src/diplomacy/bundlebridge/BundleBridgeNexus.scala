package org.chipsalliance.diplomacy.bundlebridge

import chisel3.{chiselTypeOf, ActualDirection, Data, Reg}
import chisel3.reflect.DataMirror
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyRawModuleImp}

class BundleBridgeNexus[T <: Data](
  inputFn:                      Seq[T] => T,
  outputFn:                     (T, Int) => Seq[T],
  default:                      Option[() => T] = None,
  inputRequiresOutput:          Boolean = false,
  override val shouldBeInlined: Boolean = true
)(
  implicit p:                   Parameters)
    extends LazyModule {
  val node = BundleBridgeNexusNode[T](default, inputRequiresOutput)

  lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
    val defaultWireOpt = default.map(_())
    val inputs: Seq[T] = node.in.map(_._1)
    inputs.foreach { i =>
      require(
        DataMirror.checkTypeEquivalence(i, inputs.head),
        s"${node.context} requires all inputs have equivalent Chisel Data types, but got\n$i\nvs\n${inputs.head}"
      )
    }
    inputs.flatMap(getElements).foreach { elt =>
      DataMirror.directionOf(elt) match {
        case ActualDirection.Output      => ()
        case ActualDirection.Unspecified => ()
        case _                           => require(false, s"${node.context} can only be used with Output-directed Bundles")
      }
    }

    val outputs: Seq[T] =
      if (node.out.size > 0) {
        val broadcast: T = if (inputs.size >= 1) inputFn(inputs) else defaultWireOpt.get
        outputFn(broadcast, node.out.size)
      } else { Nil }

    val typeName = outputs.headOption.map(_.typeName).getOrElse("NoOutput")
    override def desiredName = s"BundleBridgeNexus_$typeName"

    node.out.map(_._1).foreach { o =>
      require(
        DataMirror.checkTypeEquivalence(o, outputs.head),
        s"${node.context} requires all outputs have equivalent Chisel Data types, but got\n$o\nvs\n${outputs.head}"
      )
    }

    require(
      outputs.size == node.out.size,
      s"${node.context} outputFn must generate one output wire per edgeOut, but got ${outputs.size} vs ${node.out.size}"
    )

    node.out.zip(outputs).foreach { case ((out, _), bcast) => out := bcast }
  }
}

object BundleBridgeNexus {
  def safeRegNext[T <: Data](x: T): T = {
    val reg = Reg(chiselTypeOf(x))
    reg := x
    reg
  }

  def requireOne[T <: Data](registered: Boolean)(seq: Seq[T]): T = {
    require(seq.size == 1, "BundleBroadcast default requires one input")
    if (registered) safeRegNext(seq.head) else seq.head
  }

  def orReduction[T <: Data](registered: Boolean)(seq: Seq[T]): T = {
    val x = seq.reduce((a, b) => (a.asUInt | b.asUInt).asTypeOf(seq.head))
    if (registered) safeRegNext(x) else x
  }

  def fillN[T <: Data](registered: Boolean)(x: T, n: Int): Seq[T] = Seq.fill(n) {
    if (registered) safeRegNext(x) else x
  }

  def apply[T <: Data](
    inputFn:             Seq[T] => T = orReduction[T](false) _,
    outputFn:            (T, Int) => Seq[T] = fillN[T](false) _,
    default:             Option[() => T] = None,
    inputRequiresOutput: Boolean = false,
    shouldBeInlined:     Boolean = true
  )(
    implicit p:          Parameters
  ): BundleBridgeNexusNode[T] = {
    val nexus = LazyModule(new BundleBridgeNexus[T](inputFn, outputFn, default, inputRequiresOutput, shouldBeInlined))
    nexus.node
  }
}
