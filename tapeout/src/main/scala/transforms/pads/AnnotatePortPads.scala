package barstools.tapeout.transforms.pads

import firrtl.annotations._
import firrtl._
import firrtl.ir._
import firrtl.passes._
import barstools.tapeout.transforms._

// TODO: Make some trait with commonalities between IO Pad + supply pad

// Pads associated with IO Ports! (Not supplies!)
case class PortIOPad(
    pad: Option[FoundryPad],
    padSide: PadSide,
    port: Port) {

  def arrayInstNamePrefix(mod: String): String = Seq(mod, firrtlBBName, getPadName).mkString("/")
  def arrayInstNameSuffix: String = pad match {
    case None => throw new Exception("Port needs to use pad to get array instance name!")
    case Some(x) => "/" + x.padInstName
  }

  def portName = port.name
  def portWidth = bitWidth(port.tpe).intValue
  def portDirection = port.direction
  def padOrientation = padSide.orientation
  def padType = pad match {
    case None => NoPad
    case Some(x) => x.padType
  }

  def widthParamName = "WIDTH"
  def getPadName: String = pad match {
    case None => throw new Exception("Cannot get pad name when no pad specified!")
    case Some(x) => x.getName(portDirection, padOrientation)
  }
  def getPadArrayName: String = Seq(getPadName, "array").mkString("_")
  // Firrtl black box name must be unique, even though the parameterized Verilog modules don't
  // need to have separate names
  def firrtlBBName = Seq(getPadArrayName, portName).mkString("_")

  // Note: This includes both the pad wrapper + an additional wrapper for n-bit wide to 
  // multiple pad conversion!
  def createPadInline(): String = {
    // For blackboxing bit extraction/concatenation (with module arrays)
    def io(): String = padType match {
      case DigitalPad => 
        s"""|  input [${widthParamName}-1:0] ${DigitalPad.inName},
            |  output reg [${widthParamName}-1:0] ${DigitalPad.outName}""".stripMargin
      case AnalogPad => 
        s"  inout [${widthParamName}-1:0] ${AnalogPad.ioName}"
      case _ => throw new Exception("IO pad can only be digital or analog")
    }
    def assignIO(): String = padType match {
      case DigitalPad => 
        s"""|    .${DigitalPad.inName}(${DigitalPad.inName}),
            |    .${DigitalPad.outName}(${DigitalPad.outName})""".stripMargin
      case AnalogPad => 
        s"    .${AnalogPad.ioName}(${AnalogPad.ioName})"
      case _ => throw new Exception("IO pad can only be digital or analog") 
    }
    def getPadVerilog(): String = pad match {
      case None => throw new Exception("Cannot get Verilog when no pad specified!")
      case Some(x) => x.getVerilog(portDirection, padOrientation)
    }
    s"""inline
      |${getPadArrayName}.v
      |${getPadVerilog}
      |module ${getPadArrayName} #(
      |  parameter int ${widthParamName}=1
      |)(
      |${io}
      |);
      |  ${getPadName} ${getPadName}[${widthParamName}-1:0](
      |${assignIO}
      |  );  
      |endmodule""".stripMargin
  }
}

object AnnotatePortPads {
  def apply(
      c: Circuit,
      topMod: String, 
      pads: Seq[FoundryPad], 
      componentAnnos: Seq[TargetIOPadAnnoF], 
      defaultSide: PadSide): Seq[PortIOPad] = {

    def lowerAnnotations(): Seq[TargetIOPadAnnoF] = {
      componentAnnos map { x => x.target match {
        case c: ComponentName => x.copy(target = c.copy(name = LowerName(c.name)))
        case _ => throw new Exception("Not a component annotation! Can't lower!")
      }}
    }

    // Make annotations match low form
    val annos = lowerAnnotations()

    def getPortIOPad(port: Port): PortIOPad = {
      val portAnnos = annos.find(_.targetName == port.name)      
      // Ports can only be digital or analog
      val padTypeRequired = port.tpe match {
        case AnalogType(_) => AnalogPad
        case _ => DigitalPad
      }
      val validPads = pads.filter(_.padType == padTypeRequired)
      require(validPads.length > 0, s"No ${padTypeRequired.serialize} pads specified in the config yaml file!")
      portAnnos match {
        case None => 
          // If no pad-related annotation is found on a port, use defaults based off of port type
          PortIOPad(Some(validPads.head), defaultSide, port)
        case Some(x) =>
          x.anno match {
            case NoIOPadAnnotation(_) => 
              // Some ports might not want attached pads
              PortIOPad(None, defaultSide, port)
            case IOPadAnnotation(padSide, padName) if padName.isEmpty => 
              // If no pad name is used, select the first valid pad based off of port type
              PortIOPad(Some(validPads.head), HasPadAnnotation.getSide(padSide), port)
            case IOPadAnnotation(padSide, padName) =>
              // If name doesn't match any provided -- maybe someone typoed?
              validPads.find(_.name == padName) match {
                case None => 
                  throw new Exception(
                    s"Pad name associated with ${port.name} doesn't match valid pad names. Did you typo?")
                case Some(x) =>
                  PortIOPad(Some(x), HasPadAnnotation.getSide(padSide), port)
              }
          }
      }
    }
    // Top MUST be internal module
    c.modules.filter(_.name == topMod).head.ports.map(x => getPortIOPad(x))
  }
}