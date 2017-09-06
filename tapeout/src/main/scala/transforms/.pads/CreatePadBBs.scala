package barstools.tapeout.transforms.pads

import firrtl.annotations._
import firrtl._
import firrtl.ir._
import firrtl.transforms._

object CreatePadBBs {

  private [barstools] case class UsedPadInfo(
      // The following are found with both supply + io pads
      padInline: String,    // Verilog txt
      padName: String,      // Pad module name
      padType: PadType,     // Pad type: supply, analog, digital
      // The following only affects io pads (due to using parameterized modules for bit extraction / cat)
      padArrayName: String, // Name of parameterized pad wrapper (that does bit extract/cat)
      firrtlBBName: String, // Unique Firrtl name of each parameterized pad wrapper
      portWidth: Int        // Port width for analog/digital
      )

  def convertToUsedPad(p: PortIOPad): UsedPadInfo = {
    UsedPadInfo(
        padInline = p.createPadInline,
        padName = p.getPadName,
        padType = p.padType,
        padArrayName = p.getPadArrayName,
        firrtlBBName = p.firrtlBBName,
        portWidth = p.portWidth)
  }

   def convertToUsedPad(p: TopSupplyPad): UsedPadInfo = {
    UsedPadInfo(
        padInline = p.createPadInline,
        padName = p.getPadName,
        padType = p.padType,
        // Supply pads don't require bit extraction / cat so don't care
        padArrayName = p.getPadName,
        firrtlBBName = p.getPadName,
        portWidth = 0)
  }

  def checkLegalPadName(namespace: Namespace, usedPads: Seq[UsedPadInfo]): Unit = {
    usedPads foreach { x => 
      if (namespace contains x.padName)
        throw new Exception(s"Pad name ${x.padName} already used!")
      if (namespace contains x.padArrayName)
        throw new Exception(s"Pad array ${x.padArrayName} name already used!")
      if (namespace contains x.firrtlBBName)
        throw new Exception(s"Firrtl black box ${x.firrtlBBName} name already used!")
    }
  }

  def apply(
      c: Circuit,
      ioPads: Seq[PortIOPad],
      supplyPads: Seq[TopSupplyPad]): (Circuit, Seq[Annotation]) = {

    // Add black boxes for both supply + (used) io pads
    val usedPads = ioPads.filter(x => x.pad.nonEmpty).map(convertToUsedPad(_)) ++ supplyPads.map(convertToUsedPad(_))
    checkLegalPadName(Namespace(c), usedPads)

    // Note that we need to check for Firrtl name uniqueness here! (due to parameterization)
    val uniqueExtMods = scala.collection.mutable.ArrayBuffer[UsedPadInfo]()
    usedPads foreach { x => 
      if (uniqueExtMods.find(_.firrtlBBName == x.firrtlBBName).isEmpty)
        uniqueExtMods += x
    }

    // Collecting unique parameterized black boxes 
    // (for io, they're wrapped pads; for supply, they're pad modules directly)
    val uniqueParameterizedBBs = scala.collection.mutable.ArrayBuffer[UsedPadInfo]()
    uniqueExtMods foreach { x => 
      if (uniqueParameterizedBBs.find(_.padArrayName == x.padArrayName).isEmpty)
        uniqueParameterizedBBs += x
    }

    // Note: Firrtl is silly and doesn't implement true parameterization -- each module with 
    // parameterization that potentially affects # of IO needs to be uniquely identified 
    // (but only in Firrtl)
    val bbs = uniqueExtMods.map(x => {
      // Supply pads don't have ports
      val ports = x.padType match {
        case AnalogPad => Seq(Port(NoInfo, AnalogPad.ioName, Input, AnalogType(IntWidth(x.portWidth))))
        case DigitalPad => Seq(
          Port(NoInfo, DigitalPad.inName, Input, UIntType(IntWidth(x.portWidth))),
          Port(NoInfo, DigitalPad.outName, Output, UIntType(IntWidth(x.portWidth)))
        )
        case SupplyPad => Seq.empty
        case _ => throw new Exception("Port pad type invalid!")
      }
      // Supply black boxes are not parameterized
      val params = x.padType match {
        case AnalogPad | DigitalPad => Seq(IntParam(ioPads.head.widthParamName, x.portWidth))
        case SupplyPad => Seq()
        case _ => throw new Exception("Port pad type invalid!")
      }
      // Firrtl name is unique
      ExtModule(NoInfo, x.firrtlBBName, ports, x.padArrayName, params)
    } ).toSeq

    // Add annotations to black boxes to inline Verilog from template
    // Again, note the weirdness in parameterization -- just need to hook to one matching Firrtl instance
    val annos = uniqueParameterizedBBs.map(x => 
      BlackBoxSourceAnnotation(ModuleName(x.firrtlBBName, CircuitName(c.main)), x.padInline)
    ).toSeq
    (c.copy(modules = c.modules ++ bbs), annos)
  }

}