package org.chipsalliance.diplomacy.lazymodule

import chisel3.{Data, Flipped, Record}

import scala.collection.immutable.ListMap
import scala.util.matching._

/** [[AutoBundle]] will construct the [[Bundle]]s for a [[LazyModule]] in [[LazyModuleImpLike.instantiate]],
  *
  * @param elts
  *   is a sequence of data containing for each IO port a tuple of (name, data, flipped), where name: IO name data:
  *   actual data for connection. flipped: flip or not in [[makeElements]]
  */
private[diplomacy] final class AutoBundle(elts: (String, Data, Boolean)*) extends Record {
  // We need to preserve the order of elts, despite grouping by name to disambiguate things.
  val elements: ListMap[String, Data] = ListMap() ++ elts.zipWithIndex
    .map(makeElements)
    .groupBy(_._1)
    .values
    .flatMap {
      // If name is unique, it will return a Seq[index -> (name -> data)].
      case Seq((key, element, i)) => Seq(i -> (key -> element))
      // If name is not unique, name will append with j, and return `Seq[index -> (s"${name}_${j}" -> data)]`.
      case seq                    => seq.zipWithIndex.map { case ((key, element, i), j) => i -> (key + "_" + j -> element) }
    }
    .toList
    .sortBy(_._1)
    .map(_._2)
  require(elements.size == elts.size)

  // Trim final "(_[0-9]+)*$" in the name, flip data with flipped.
  private def makeElements(tuple: ((String, Data, Boolean), Int)) = {
    val ((key, data, flip), i) = tuple
    // Trim trailing _0_1_2 stuff so that when we append _# we don't create collisions.
    val regex                  = new Regex("(_[0-9]+)*$")
    val element                =
      if (flip) Flipped(data.cloneType)
      else data.cloneType
    (regex.replaceAllIn(key, ""), element, i)
  }
}
