// See LICENSE for license details

package barstools.iocell.chisel

import chisel3._
import chisel3.util.{Cat, HasBlackBoxResource}
import chisel3.experimental.{Analog, DataMirror}

class AnalogIOCellBundle extends Bundle {
  val pad = Analog(1.W)
  val core = Analog(1.W)
}

class DigitalGPIOCellBundle extends Bundle {
  val pad = Analog(1.W)
  val i = Output(Bool())
  val ie = Input(Bool())
  val o = Input(Bool())
  val oe = Input(Bool())
}

class DigitalOutIOCellBundle extends Bundle {
  val pad = Output(Bool())
  val o = Input(Bool())
  val oe = Input(Bool())
}

class DigitalInIOCellBundle extends Bundle {
  val pad = Input(Bool())
  val i = Output(Bool())
  val ie = Input(Bool())
}

abstract class IOCell extends BlackBox with HasBlackBoxResource

abstract class AnalogIOCell extends IOCell {
  val io: AnalogIOCellBundle
}

abstract class DigitalGPIOCell extends IOCell {
  val io: DigitalGPIOCellBundle
}

abstract class DigitalInIOCell extends IOCell {
  val io: DigitalInIOCellBundle
}

abstract class DigitalOutIOCell extends IOCell {
  val io: DigitalOutIOCellBundle
}

class ExampleAnalogIOCell extends AnalogIOCell {
  val io = IO(new AnalogIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class ExampleDigitalGPIOCell extends DigitalGPIOCell {
  val io = IO(new DigitalGPIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class ExampleDigitalInIOCell extends DigitalInIOCell {
  val io = IO(new DigitalInIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class ExampleDigitalOutIOCell extends DigitalOutIOCell {
  val io = IO(new DigitalOutIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

object IOCell {

  def exampleAnalog() = Module(new ExampleAnalogIOCell)
  def exampleGPIO() = Module(new ExampleDigitalGPIOCell)
  def exampleInput() = Module(new ExampleDigitalInIOCell)
  def exampleOutput() = Module(new ExampleDigitalOutIOCell)

  def generateRaw[T <: Data](signal: T,
    inFn: () => DigitalInIOCell = IOCell.exampleInput,
    outFn: () => DigitalOutIOCell = IOCell.exampleOutput,
    anaFn: () => AnalogIOCell = IOCell.exampleAnalog): (T, Seq[IOCell]) =
  {
    (signal match {
      case signal: Analog => {
        require(signal.getWidth <= 1, "Analogs wider than 1 bit are not supported because we can't bit Analogs (https://github.com/freechipsproject/chisel3/issues/536)")
        if (signal.getWidth == 0) {
          (Analog(0.W), Seq())
        } else {
          val iocell = anaFn()
          iocell.io.core <> signal
          (iocell.io.pad, Seq(iocell))
        }
      }
      case signal: Clock => {
        DataMirror.specifiedDirectionOf(signal) match {
          case SpecifiedDirection.Input => {
            val iocell = inFn()
            signal := iocell.io.i.asClock
            iocell.io.ie := true.B
            val ck = Wire(Clock())
            iocell.io.pad := ck.asUInt.asBool
            (ck, Seq(iocell))
          }
          case SpecifiedDirection.Output => {
            val iocell = outFn()
            iocell.io.o := signal.asUInt.asBool
            iocell.io.oe := true.B
            (iocell.io.pad.asClock, Seq(iocell))
          }
          case _ => throw new Exception("Unknown direction")
        }
      }
      // TODO we may not actually need Bool (it is probably covered by Bits)
      case signal: Bool => {
        DataMirror.specifiedDirectionOf(signal) match {
          case SpecifiedDirection.Input => {
            val iocell = inFn()
            signal := iocell.io.i
            iocell.io.ie := true.B
            (iocell.io.pad, Seq(iocell))
          }
          case SpecifiedDirection.Output => {
            val iocell = outFn()
            iocell.io.o := signal
            iocell.io.oe := true.B
            (iocell.io.pad, Seq(iocell))
          }
          case _ => throw new Exception("Unknown direction")
        }
      }
      case signal: Bits => {
        DataMirror.specifiedDirectionOf(signal) match {
          case SpecifiedDirection.Input => {
            val wire = Wire(chiselTypeOf(signal))
            val iocells = wire.asBools.map { w =>
              val iocell = inFn()
              iocell.io.pad := w
              iocell.io.ie := true.B
              iocell
            }
            if (iocells.size > 0) {
              signal := Cat(iocells.map(_.io.i).reverse)
            }
            (wire, iocells)
          }
          case SpecifiedDirection.Output => {
            val iocells = signal.asBools.map { b =>
              val iocell = outFn()
              iocell.io.o := b
              iocell.io.oe := true.B
              iocell
            }
            if (iocells.size > 0) {
              (Cat(iocells.map(_.io.pad).reverse), iocells)
            } else {
              (Wire(Bits(0.W)), iocells)
            }
          }
          case _ => throw new Exception("Unknown direction")
        }
      }
      case signal: Vec[_] => {
        val wire = Wire(chiselTypeOf(signal))
        val iocells = signal.zip(wire).foldLeft(Seq.empty[IOCell]) { case (total, (sig, w)) =>
          val (pad, ios) = IOCell.generateRaw(sig, inFn, outFn, anaFn)
          w <> pad
          total ++ ios
        }
        (wire, iocells)
      }
      case signal: Record => {
        val wire = Wire(chiselTypeOf(signal))
        val iocells = signal.elements.foldLeft(Seq.empty[IOCell]) { case (total, (name, sig)) =>
          val (pad, ios) = IOCell.generateRaw(sig, inFn, outFn, anaFn)
          wire.elements(name) <> pad
          total ++ ios
        }
        (wire, iocells)
      }
      case _ => { throw new Exception("Oops, I don't know how to handle this signal.") }
    }).asInstanceOf[(T, Seq[IOCell])]
  }

}
