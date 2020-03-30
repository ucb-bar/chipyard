// See LICENSE for license details

package barstools.iocell.chisel

import chisel3._
import chisel3.util.{Cat, HasBlackBoxResource}
import chisel3.experimental.{Analog, DataMirror, IO}

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

class GenericAnalogIOCell extends AnalogIOCell {
  val io = IO(new AnalogIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class GenericDigitalGPIOCell extends DigitalGPIOCell {
  val io = IO(new DigitalGPIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class GenericDigitalInIOCell extends DigitalInIOCell {
  val io = IO(new DigitalInIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class GenericDigitalOutIOCell extends DigitalOutIOCell {
  val io = IO(new DigitalOutIOCellBundle)
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

object IOCell {

  def genericAnalog() = Module(new GenericAnalogIOCell)
  def genericGPIO() = Module(new GenericDigitalGPIOCell)
  def genericInput() = Module(new GenericDigitalInIOCell)
  def genericOutput() = Module(new GenericDigitalOutIOCell)

  def generateIOFromSignal[T <: Data](coreSignal: T, name: Option[String] = None,
    inFn: () => DigitalInIOCell = IOCell.genericInput,
    outFn: () => DigitalOutIOCell = IOCell.genericOutput,
    anaFn: () => AnalogIOCell = IOCell.genericAnalog): (T, Seq[IOCell]) =
  {
    val padSignal = IO(DataMirror.internal.chiselTypeClone[T](coreSignal))
    val iocells = IOCell.generateFromSignal(coreSignal, padSignal, name, inFn, outFn, anaFn)
    (padSignal, iocells)
  }

  def generateFromSignal[T <: Data](coreSignal: T, padSignal: T, name: Option[String] = None,
    inFn: () => DigitalInIOCell = IOCell.genericInput,
    outFn: () => DigitalOutIOCell = IOCell.genericOutput,
    anaFn: () => AnalogIOCell = IOCell.genericAnalog): Seq[IOCell] =
  {
    coreSignal match {
      case coreSignal: Analog => {
        if (coreSignal.getWidth == 0) {
          Seq()
        } else {
          require(coreSignal.getWidth == 1, "Analogs wider than 1 bit are not supported because we can't bit-select Analogs (https://github.com/freechipsproject/chisel3/issues/536)")
          val iocell = anaFn()
          name.foreach(n => iocell.suggestName(n))
          iocell.io.core <> coreSignal
          padSignal <> iocell.io.pad
          Seq(iocell)
        }
      }
      case coreSignal: Clock => {
        DataMirror.directionOf(coreSignal) match {
          case ActualDirection.Input => {
            val iocell = inFn()
            name.foreach(n => iocell.suggestName(n))
            coreSignal := iocell.io.i.asClock
            iocell.io.ie := true.B
            iocell.io.pad := padSignal.asUInt.asBool
            Seq(iocell)
          }
          case ActualDirection.Output => {
            val iocell = outFn()
            name.foreach(n => iocell.suggestName(n))
            iocell.io.o := coreSignal.asUInt.asBool
            iocell.io.oe := true.B
            padSignal := iocell.io.pad.asClock
            Seq(iocell)
          }
          case _ => throw new Exception("Unknown direction")
        }
      }
      case coreSignal: Bits => {
        require(padSignal.getWidth == coreSignal.getWidth, "padSignal and coreSignal must be the same width")
        if (padSignal.getWidth == 0) {
          // This dummy assignment will prevent invalid firrtl from being emitted
          DataMirror.directionOf(coreSignal) match {
            case ActualDirection.Input => coreSignal := 0.U
            case _ => {}
          }
          Seq()
        } else {
          DataMirror.directionOf(coreSignal) match {
            case ActualDirection.Input => {
              // this type cast is safe because we guarantee that padSignal and coreSignal are the same type (T), but the compiler is not smart enough to know that
              val iocells = padSignal.asInstanceOf[Bits].asBools.zipWithIndex.map { case (w, i) =>
                val iocell = inFn()
                name.foreach(n => iocell.suggestName(n + "_" + i))
                iocell.io.pad := w
                iocell.io.ie := true.B
                iocell
              }
              coreSignal := Cat(iocells.map(_.io.i).reverse)
              iocells
            }
            case ActualDirection.Output => {
              val iocells = coreSignal.asBools.zipWithIndex.map { case (w, i) =>
                val iocell = outFn()
                name.foreach(n => iocell.suggestName(n + "_" + i))
                iocell.io.o := w
                iocell.io.oe := true.B
                iocell
              }
              padSignal := Cat(iocells.map(_.io.pad).reverse)
              iocells
            }
            case _ => throw new Exception("Unknown direction")
          }
        }
      }
      case coreSignal: Vec[Data] => {
        // this type cast is safe because we guarantee that padSignal and coreSignal are the same type (T), but the compiler is not smart enough to know that
        val padSignal2 = padSignal.asInstanceOf[Vec[Data]]
        require(padSignal2.size == coreSignal.size, "size of Vec for padSignal and coreSignal must be the same")
        coreSignal.zip(padSignal2).zipWithIndex.foldLeft(Seq.empty[IOCell]) { case (total, ((core, pad), i)) =>
          val ios = IOCell.generateFromSignal(core, pad, name.map(_ + "_" + i), inFn, outFn, anaFn)
          total ++ ios
        }
      }
      case coreSignal: Record => {
        // this type cast is safe because we guarantee that padSignal and coreSignal are the same type (T), but the compiler is not smart enough to know that
        val padSignal2 = padSignal.asInstanceOf[Record]
        coreSignal.elements.foldLeft(Seq.empty[IOCell]) { case (total, (eltName, core)) =>
          val pad = padSignal2.elements(eltName)
          val ios = IOCell.generateFromSignal(core, pad, name.map(_ + "_" + eltName), inFn, outFn, anaFn)
          total ++ ios
        }
      }
      case _ => { throw new Exception("Oops, I don't know how to handle this signal.") }
    }
  }

}
