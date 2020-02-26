package barstools.tapeout.transforms.pads

import firrtl.annotations._
import firrtl._
import firrtl.ir._
import firrtl.passes._

case class TopSupplyPad(
    pad: FoundryPad,
    padSide: PadSide,
    num: Int
) {

  // TODO: These should be pulled into some common trait (supply + io)!

  def arrayInstNamePrefix(mod: String): Seq[String] = {
    instNames.map(n => Seq(mod, n, pad.padInstName).mkString("/"))
  }
  def supplySetNum = pad.getSupplySetNum

  def padType = pad.padType
  require(pad.padType == SupplyPad)

  def padOrientation = padSide.orientation
  def getPadName = pad.getName(Output/*Should be None*/, padOrientation)
  def firrtlBBName = getPadName
  private def instNamePrefix = Seq(firrtlBBName, padSide.serialize).mkString("_")
  def instNames = (0 until num).map(i => Seq(instNamePrefix, i.toString).mkString("_"))

  def createPadInline(): String = {
  def getPadVerilog(): String = pad.getVerilog(Output/*Should be None*/, padOrientation)
    s"""inline
      |${getPadName}.v
      |${getPadVerilog}""".stripMargin
  }
}

object AnnotateSupplyPads {
  def apply(
      pads: Seq[FoundryPad],
      supplyAnnos: Seq[SupplyAnnotation]
  ): Seq[TopSupplyPad] = {
    supplyAnnos.map( a =>
      pads.find(_.name == a.padName) match {
        case None =>
          throw new Exception(s"Supply pad ${a.padName} not found in Yaml file!")
        case Some(x) =>
          Seq(
            TopSupplyPad(x, Left, a.leftSide),
            TopSupplyPad(x, Right, a.rightSide),
            TopSupplyPad(x, Top, a.topSide),
            TopSupplyPad(x, Bottom, a.bottomSide))
      }
    ).flatten.filter(_.num > 0)
  }
}
