package barstools.tapeout.transforms.utils

import chisel3._

import scala.collection.immutable.ListMap

class CustomBundle[T <: Data](elts: (String, T)*) extends Record {
  val elements = ListMap(elts.map { case (field, elt) => field -> chiselTypeOf(elt) }: _*)
  def apply(elt: String): T = elements(elt)
  def apply(elt: Int):    T = elements(elt.toString)
  override def cloneType = (new CustomBundle(elements.toList: _*)).asInstanceOf[this.type]
}

class CustomIndexedBundle[T <: Data](elts: (Int, T)*) extends Record {
  // Must be String, Data
  val elements = ListMap(elts.map { case (field, elt) => field.toString -> chiselTypeOf(elt) }: _*)
  // TODO: Make an equivalent to the below work publicly (or only on subclasses?)
  def indexedElements = ListMap(elts.map { case (field, elt) => field -> chiselTypeOf(elt) }: _*)
  def apply(elt: Int): T = elements(elt.toString)
  override def cloneType = (new CustomIndexedBundle(indexedElements.toList: _*)).asInstanceOf[this.type]
}

object CustomIndexedBundle {
  def apply[T <: Data](gen: T, idxs: Seq[Int]) = new CustomIndexedBundle(idxs.map(_ -> gen): _*)
  // Allows Vecs of elements of different types/widths
  def apply[T <: Data](gen: Seq[T]) = new CustomIndexedBundle(gen.zipWithIndex.map { case (elt, field) =>
    field -> elt
  }: _*)
}
