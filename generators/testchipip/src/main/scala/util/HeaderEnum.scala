package testchipip.util

import chisel3._
import chisel3.util.log2Up
import scala.collection.mutable.{HashMap, ListBuffer}

class HeaderEnum(val prefix: String) {
  val h = new HashMap[String,Int]
  def makeHeader(): String = {
    h.toSeq.sortBy(_._2).map { case (n,i) => s"#define ${prefix.toUpperCase}_${n.toUpperCase} $i\n" }.mkString
  }
  def apply(s: String): UInt = h(s).U(log2Up(h.size).W)
}

object HeaderEnum {
  val contents = new ListBuffer[String]

  def apply(prefix: String, names: String*): HeaderEnum = {
    val e = new HeaderEnum(prefix)
    names.zipWithIndex.foreach { case (n,i) => e.h.put(n,i) }
    val header = e.makeHeader()
    if(!HeaderEnum.contents.contains(header)) HeaderEnum.contents += header
    e
  }
}
