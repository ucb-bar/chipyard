package org.chipsalliance.diplomacy.nodes

abstract class CycleException(kind: String, loop: Seq[String])
    extends Exception(s"Diplomatic $kind cycle detected involving $loop")
case class StarCycleException(loop: Seq[String] = Nil)     extends CycleException("star", loop)
case class DownwardCycleException(loop: Seq[String] = Nil) extends CycleException("downward", loop)
case class UpwardCycleException(loop: Seq[String] = Nil)   extends CycleException("upward", loop)
