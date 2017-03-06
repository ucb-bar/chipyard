package barstools.tapeout.transforms.pads

import net.jcazevedo.moultingyaml._

import firrtl._
import firrtl.ir._
import barstools.tapeout.transforms._

case class FoundryPad(
    tpe: String, 
    name: String, 
    width: Int,             
    height: Int,
    supplySetNum: Option[Int],             
    verilog: String) {

  def padInstName = "PAD"

  require(verilog.contains("{{#if isHorizontal}}"), "All pad templates must contain '{{#if isHorizontal}}'")
  require(verilog.contains("{{name}}"), "All pad templates must contain module name '{{name}}'")
  require(verilog.contains(padInstName), s"All pad templates should have instances called ${padInstName}")

  def getSupplySetNum = supplySetNum.getOrElse(1)

  val padType = tpe match {
    case "digital" => 
      require(verilog.contains(DigitalPad.inName), "Digital pad template must contain input called 'in'")
      require(verilog.contains(DigitalPad.outName), "Digital pad template must contain output called 'out'")
      require(verilog.contains("{{#if isInput}}"), "Digital pad template must contain '{{#if isInput}}'")
      DigitalPad
    case "analog" => 
      require(verilog.contains(AnalogPad.ioName), "Analog pad template must contain inout called 'io'")
      require(!verilog.contains("{{#if isInput}}"), "Analog pad template must not contain '{{#if isInput}}'")
      AnalogPad
    case "supply" => 
      // Supply pads don't have IO
      require(!verilog.contains("{{#if isInput}}"), "Supply pad template must not contain '{{#if isInput}}'")
      require(
        verilog.contains(s"${padInstName}["), "All supply pad templates should have instance arrays" +
        " called ${padInstName}[n:0], where n = ${getSupplySetNum-1}")
      require(supplySetNum.nonEmpty, "# of grouped supply pads 'supplySetNum' should be specified!")
      SupplyPad
    case _ => throw new Exception("Illegal pad type in config!")
  }

  import com.gilt.handlebars.scala.binding.dynamic._
  import com.gilt.handlebars.scala.Handlebars
  private val template = Handlebars(verilog)

  // Make sure names don't have spaces in Verilog!
  private[barstools] val correctedName = name.replace(" ", "_")

  case class TemplateParams(
      // isInput only used with digital pads
      isInput: Boolean,
      isHorizontal: Boolean) {

    private val orient = if (isHorizontal) Horizontal.serialize else Vertical.serialize
    private val dir = padType match {
      case AnalogPad => InOut.serialize
      case SupplyPad => NoDirection.serialize
      case DigitalPad => if (isInput) Input.serialize else Output.serialize
    }
    val name = {
      val start = Seq("pad", tpe, correctedName, orient)
      if (padType == DigitalPad) start :+ dir
      else start
    }.mkString("_")
  }

  // Note: Analog + supply don't use direction
  private def getTemplateParams(dir: Direction, orient: PadOrientation): TemplateParams = 
    TemplateParams(isInput = (dir == Input), isHorizontal = (orient == Horizontal))

  def getVerilog(dir: Direction, orient: PadOrientation): String = {
    val p = getTemplateParams(dir, orient)
    template(p).stripMargin
  }

  def getName(dir: Direction, orient: PadOrientation): String = getTemplateParams(dir, orient).name
}

object FoundryPadsYaml extends DefaultYamlProtocol {
  val exampleResource = "/FoundryPads.yaml"
  implicit val _pad = yamlFormat6(FoundryPad)
  def parse(techDir: String): Seq[FoundryPad] = {
    val file = techDir + exampleResource
    if(techDir != "" && !(new java.io.File(file)).exists()) 
      throw new Exception("Technology directory must contain FoundryPads.yaml!")
    val out = (new YamlFileReader(exampleResource)).parse[FoundryPad](if (techDir == "") "" else file)
    val padNames = out.map(x => x.correctedName)
    require(padNames.distinct.length == padNames.length, "Pad names must be unique!")
    out
  }
}