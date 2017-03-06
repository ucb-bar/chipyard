package barstools.tapeout.transforms

import chisel3._
import scala.collection.immutable.ListMap

final class CustomBundle(elts: (String, Data)*) extends Record {
  val elements = ListMap(elts map { case (field, elt) => field -> elt.chiselCloneType }: _*)
  def apply(elt: String): Data = elements(elt)
  override def cloneType = (new CustomBundle(elements.toList: _*)).asInstanceOf[this.type]
}

final class CustomIndexedBundle(elts: (Int, Data)*) extends Record {
  // Must be String, Data
  val elements = ListMap(elts map { case (field, elt) => field.toString -> elt.chiselCloneType }: _*)
  def indexedElements = ListMap(elts map { case (field, elt) => field -> elt.chiselCloneType }: _*)
  def apply(elt: Int): Data = elements(elt.toString)
  override def cloneType = (new CustomIndexedBundle(indexedElements.toList: _*)).asInstanceOf[this.type]
}

object CustomIndexedBundle {
  def apply(gen: Data, idxs: Seq[Int]) = new CustomIndexedBundle(idxs.map(_ -> gen): _*)
  // Allows Vecs of elements of different types/widths
  def apply(gen: Seq[Data]) = new CustomIndexedBundle(gen.zipWithIndex.map{ case (elt, field) => field -> elt }: _*)
}