// See LICENSE for license details.

package barstools.macros

import scala.util.matching._

sealed abstract class MemPort(val name: String) { override def toString = name }

case object ReadPort extends MemPort("read")
case object WritePort extends MemPort("write")
case object MaskedWritePort extends MemPort("mwrite")
case object ReadWritePort extends MemPort("rw")
case object MaskedReadWritePort extends MemPort("mrw")

object MemPort {

  val all = Set(ReadPort, WritePort, MaskedWritePort, ReadWritePort, MaskedReadWritePort)

  def apply(s: String): Option[MemPort] = MemPort.all.find(_.name == s)

  def fromString(s: String): Seq[MemPort] = {
    s.split(",").toSeq.map(MemPort.apply).map(_ match {
      case Some(x) => x
      case _ => throw new Exception(s"Error parsing MemPort string : ${s}")
    })
  }
}

// This is based on firrtl.passes.memlib.ConfWriter
// TODO standardize this in FIRRTL
case class MemConf(
  name: String,
  depth: Int,
  width: Int,
  ports: Seq[MemPort],
  maskGranularity: Option[Int]
) {

  private def portsStr = ports.map(_.name).mkString(",")
  private def maskGranStr = maskGranularity.map((p) => s"mask_gran $p").getOrElse("")

  override def toString() = s"name ${name} depth ${depth} width ${width} ports ${portsStr} ${maskGranStr} "
}

object MemConf {

  val regex = raw"\s*name\s+(\w+)\s+depth\s+(\d+)\s+width\s+(\d+)\s+ports\s+([^\s]+)\s+(?:mask_gran\s+(\d+))?\s*".r

  def fromString(s: String): Seq[MemConf] = {
    s.split("\n").toSeq.map(_ match {
      case MemConf.regex(name, depth, width, ports, maskGran) => MemConf(name, depth.toInt, width.toInt, MemPort.fromString(ports), Option(maskGran).map(_.toInt))
      case _ => throw new Exception(s"Error parsing MemConf string : ${s}")
    })
  }
}
