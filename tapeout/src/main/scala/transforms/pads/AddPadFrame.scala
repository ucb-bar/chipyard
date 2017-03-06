// See LICENSE for license details.

package barstools.tapeout.transforms.pads

import firrtl.annotations._
import firrtl.ir._
import firrtl._
import firrtl.passes._

// Analog is like UInt, SInt; it's not a direction (which is kind of weird)
// WARNING: Analog type is associated with Verilog InOut! i.e. even if digital pads are tri-statable, b/c tristate
// requires an additional ctrl signal, digital pads must be operated in a single "static" condition here; Analog will
// be paired with analog pads

class AddPadFrame(
    topMod: String, 
    padFrameName: String, 
    topInternalName: String, 
    ioPads: Seq[PortIOPad], 
    supplyPads: Seq[TopSupplyPad]) extends Pass {

  def name: String = "Add Padframe"

  def run(c: Circuit): Circuit = {
    // New modules consist of old modules (with top renamed to internal) + padFrame + newTop
    val newMods = c.modules.map {
      case mod: Module if mod.name == topMod => 
        // Original top module is now internal module
        mod.copy(name = topInternalName)
      case m => m
    } ++ Seq(buildPadFrame(), buildTopWrapper())

    // Reparent so circuit top is whatever uses pads!
    // TODO: Can the top level be a blackbox?
    c.copy(modules = newMods, main = topMod)
  }

  def intName(p: PortIOPad) = s"${p.portName}_Int"
  def extName(p: PortIOPad) = s"${p.portName}_Ext"

  def buildTopWrapper(): Module = {
    // outside -> padframe -> internal
    // Top (with same name) contains 1) padframe + 2) internal signals
    val padFrameInst = WDefInstance(padFrameName, padFrameName)
    val topInternalInst = WDefInstance(topInternalName, topInternalName)
    val padFrameRef = WRef(padFrameName)  
    val topInternalRef = WRef(topInternalName)
    val connects = ioPads.map { p => 
      val io = WRef(p.portName)
      val intIo = WSubField(topInternalRef, p.portName) 
      val padFrameIntIo = WSubField(padFrameRef, intName(p))  
      val padFrameExtIo = WSubField(padFrameRef, extName(p))  
      p.port.tpe match {
        case AnalogType(_) => 
          // Analog pads only have 1 port
          // If Analog port doesn't have associated pad, don't hook it up to the padframe
          val analogAttachInt = Seq(Attach(NoInfo, Seq(io, intIo)))
          if (p.pad.isEmpty) analogAttachInt
          else analogAttachInt :+ Attach(NoInfo, Seq(io, padFrameExtIo))
        case _ => p.portDirection match {
          case Input => 
            // input to padframe ; padframe to internal
            Seq(Connect(NoInfo, padFrameExtIo, io), Connect(NoInfo, intIo, padFrameIntIo))
          case Output => 
            // internal to padframe ; padframe to output
            Seq(Connect(NoInfo, padFrameIntIo, intIo), Connect(NoInfo, io, padFrameExtIo))
        }
      }
    }.flatten 
    val stmts = Seq(padFrameInst, topInternalInst) ++ connects
    val ports = ioPads.map(p => p.port)
    Module(NoInfo, topMod, ports = ports, body = Block(stmts))
  }

  def buildPadFrame(): Module = {
    // Internal = connection to original RTL; External = connection to outside world
    // Note that for analog pads, since there's only 1 port, only _Ext is used
    val intPorts = ioPads.map(p => p.port.tpe match {
      case AnalogType(_) => None
      case _ => Some(p.port.copy(name = intName(p), direction = Utils.swap(p.portDirection)))
    }).flatten
    val extPorts = ioPads.map(p => p.port.tpe match {
      // If an analog port doesn't have a pad associated with it, don't add it to the padframe
      case AnalogType(_) if p.pad.isEmpty => None
      case _ => Some(p.port.copy(name = extName(p)))
    } ).flatten
    // Only create pad black boxes for ports that require them
    val ioPadInsts = ioPads.filter(x => !x.pad.isEmpty).map(p => WDefInstance(p.firrtlBBName, p.firrtlBBName))
    // Connect to pad only if used ; otherwise leave dangling for Analog 
    // and just connect through for digital (assumes no supplies)
    val connects = ioPads.map { p => 
      val intRef = WRef(intName(p), p.port.tpe) 
      val extRef = WRef(extName(p), p.port.tpe) 
      p.pad match {
        // No pad needed -- just connect through
        case None => p.port.tpe match {
          case AnalogType(_) => 
            Seq(EmptyStmt)
          case _ =>
            val (lhs, rhs) = p.portDirection match {
              case Input => (intRef, extRef)
              case Output => (extRef, intRef)
            }
            Seq(Connect(NoInfo, lhs, rhs))
        }
        // Add pad
        case Some(x) => 
          val padRef = WRef(p.firrtlBBName)
          p.port.tpe match {
            // Analog type has 1:1 mapping to inout
            case AnalogType(_) =>
              val padIORef = WSubField(padRef, AnalogPad.ioName)
              Seq(Attach(NoInfo, Seq(padIORef, extRef)))
            // Normal verilog in/out can be mapped to uint, sint, or clocktype, so need cast
            case _ => 
              val padBBType = UIntType(getWidth(p.port.tpe))
              val padInRef = WSubField(padRef, DigitalPad.inName, padBBType, UNKNOWNGENDER)
              val padOutRef = WSubField(padRef, DigitalPad.outName, padBBType, UNKNOWNGENDER)
              val (rhsPadIn, lhsPadOut) = p.portDirection match {
                case Input => (extRef, intRef)
                case Output => (intRef, extRef)
              }
              // Pad inputs are treated as UInts, so need to do type conversion
              // from type to UInt pad input; from pad output to type 
              Seq(
                Connect(NoInfo, padInRef, castRhs(padBBType, rhsPadIn)),
                Connect(NoInfo, lhsPadOut, castRhs(p.port.tpe, padOutRef)))
          }
      }
    }.flatten   
    val supplyPadInsts = supplyPads.map(p => p.instNames.map(n => WDefInstance(n, p.firrtlBBName))).flatten
    Module(NoInfo, padFrameName, ports = intPorts ++ extPorts, body = Block(ioPadInsts ++ connects ++ supplyPadInsts))
  }

}