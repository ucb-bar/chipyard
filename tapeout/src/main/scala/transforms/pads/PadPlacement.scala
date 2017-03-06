package barstools.tapeout.transforms.pads

import net.jcazevedo.moultingyaml._

import firrtl._
import firrtl.ir._
import barstools.tapeout.transforms._

case class PadPlacement(
    file: String,
    left: String,
    top: String,
    right: String,
    bottom: String,
    instanceArray: String,
    padLine: String,
    template: String) {

  require(instanceArray contains "{{signal}}", "Instance Array Template should contain {{signal}}")
  require(instanceArray contains "{{idx}}", "Instance Array Template should contain {{idx}}")
  require(padLine contains "{{padInst}}", "Pad line should contain {{padInst}}")
  require(padLine contains "{{side}}", "Pad line should contain {{side}} (Can be in comments)")
  require(padLine contains "{{padIdx}}", "Pad line should contain {{padIdx}} (Can be in comments)")
  require(template contains "{{leftPads}}", "Pad line should contain {{leftPads}}")
  require(template contains "{{rightPads}}", "Pad line should contain {{rightPads}}")
  require(template contains "{{topPads}}", "Pad line should contain {{topPads}}")
  require(template contains "{{bottomPads}}", "Pad line should contain {{bottomPads}}")

  def getSideString(s: PadSide): String = s match {
    case Left => left
    case Right => right
    case Top => top
    case Bottom => bottom
  }

  import com.gilt.handlebars.scala.binding.dynamic._
  import com.gilt.handlebars.scala.Handlebars

  private val instanceArrayTemplate = Handlebars(instanceArray.stripMargin)
  private val padLineTemplate = Handlebars(padLine.stripMargin)
  private val padPlacementTemplate = Handlebars(template.stripMargin)

  def getInstanceArray(p: InstanceArrayParams): String = instanceArrayTemplate(p).stripMargin
  def getPadLine(p: PadLineParams): String = padLineTemplate(p).stripMargin.replace("&quot;", "\"")
  def getPadPlacement(p: PadPlacementParams): String = padPlacementTemplate(p).stripMargin.replace("&quot;", "\"")

}

case class InstanceArrayParams(signal: String, idx: Int)
case class PadLineParams(padInst: String, side: String, padIdx: Int)
case class PadPlacementParams(leftPads: String, rightPads: String, topPads: String, bottomPads: String)

object PadPlacementFile extends DefaultYamlProtocol {
  val exampleResource = "/PadPlacement.yaml"
  implicit val _pad = yamlFormat8(PadPlacement)
  def parse(file: String = ""): PadPlacement = {
    (new YamlFileReader(exampleResource)).parse[PadPlacement](file).head
  }
  def generate(
      techDir: String, 
      targetDir: String, 
      padFrameName: String, 
      portPads: Seq[PortIOPad], 
      supplyPads: Seq[TopSupplyPad]): Unit = {

    val file = techDir + exampleResource
    if(techDir != "" && !(new java.io.File(file)).exists()) 
        throw new Exception("Technology directory must contain PadPlacement.yaml!")
    val template = parse(if (techDir == "") "" else file)

    val leftPads = scala.collection.mutable.ArrayBuffer[String]()
    val rightPads = scala.collection.mutable.ArrayBuffer[String]()
    val topPads = scala.collection.mutable.ArrayBuffer[String]()
    val bottomPads = scala.collection.mutable.ArrayBuffer[String]()

    def sort(side: PadSide, inst: String): Unit = side match {
      case Left => leftPads += inst
      case Right => rightPads += inst
      case Top => topPads += inst
      case Bottom => bottomPads += inst
    }

    // TODO: Be smarter about supply placement (+ grouping?) between signals
    // Supply pad instance name: padFrameName/firrtlBBName_padSide_#num/PAD[#supplySetNum]
    supplyPads foreach { p =>
      val prefixes = p.arrayInstNamePrefix(padFrameName)
      prefixes foreach { prefix => 
        (0 until p.supplySetNum) foreach { idx => 
          sort(p.padSide, template.getInstanceArray(InstanceArrayParams(prefix, idx)))
        }
      }
    }
    // IO pad instance name: padFrameName/firrtlBBName/getPadName[#portWidth]/PAD
    portPads.filter(_.pad.nonEmpty) foreach { p =>
      val prefix = p.arrayInstNamePrefix(padFrameName)
      (0 until p.portWidth).map(idx => 
        template.getInstanceArray(InstanceArrayParams(prefix, idx)) + p.arrayInstNameSuffix
      ) foreach { x => sort(p.padSide, x) }
    }

    def getLines(pads: Seq[String], side: PadSide): String = {
      val seq = pads.zipWithIndex.map{ case (p, idx) => 
        template.getPadLine(PadLineParams(p, template.getSideString(side), idx)) }
      seq.mkString("\n")
    }

    val fileContents = template.getPadPlacement(PadPlacementParams(
      leftPads = getLines(leftPads.toSeq, Left),
      rightPads = getLines(rightPads.toSeq, Right),
      topPads = getLines(topPads.toSeq, Top),
      bottomPads = getLines(bottomPads.toSeq, Bottom)
    ))

    WriteConfig(targetDir, template.file, fileContents)
  }
}