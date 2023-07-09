// See LICENSE for license details

package barstools.iocell.chisel

import chisel3._
import chisel3.util.{Cat, HasBlackBoxResource}
import chisel3.experimental.{Analog, BaseModule, DataMirror, IO}

// The following four IO cell bundle types are bare-minimum functional connections
// for modeling 4 different IO cell scenarios. The intention is that the user
// would create wrapper modules that extend these interfaces with additional
// control signals. These are loosely similar to the sifive-blocks PinCtrl bundles
// (https://github.com/sifive/sifive-blocks/blob/master/src/main/scala/devices/pinctrl/PinCtrl.scala),
// but we want to avoid a dependency on an external libraries.

/** The base IO bundle for an analog signal (typically something with no digital buffers inside)
  *  pad: off-chip (external) connection
  *  core: internal connection
  */
class AnalogIOCellBundle extends Bundle {
  val pad = Analog(1.W) // Pad/bump signal (off-chip)
  val core = Analog(1.W) // core signal (on-chip)
}

/** The base IO bundle for a signal with runtime-controllable direction
  *  pad: off-chip (external) connection
  *  i: input to chip logic (output from IO cell)
  *  ie: enable signal for i
  *  o: output from chip logic (input to IO cell)
  *  oe: enable signal for o
  */
class DigitalGPIOCellBundle extends Bundle {
  val pad = Analog(1.W)
  val i = Output(Bool())
  val ie = Input(Bool())
  val o = Input(Bool())
  val oe = Input(Bool())
}

/** The base IO bundle for a digital output signal
  *  pad: off-chip (external) connection
  *  o: output from chip logic (input to IO cell)
  *  oe: enable signal for o
  */
class DigitalOutIOCellBundle extends Bundle {
  val pad = Output(Bool())
  val o = Input(Bool())
  val oe = Input(Bool())
}

/** The base IO bundle for a digital input signal
  *  pad: off-chip (external) connection
  *  i: input to chip logic (output from IO cell)
  *  ie: enable signal for i
  */
class DigitalInIOCellBundle extends Bundle {
  val pad = Input(Bool())
  val i = Output(Bool())
  val ie = Input(Bool())
}

trait IOCell extends BaseModule {
  var iocell_name: Option[String] = None

  /** Set IOCell name
    * @param s Proposed name for the IOCell
    *
    * @return An inherited IOCell with given the proposed name
    */
  def suggestName(s: String): this.type = {
    iocell_name = Some(s)
    super.suggestName(s)
  }
}

trait AnalogIOCell extends IOCell {
  val io: AnalogIOCellBundle
}

trait DigitalGPIOCell extends IOCell {
  val io: DigitalGPIOCellBundle
}

trait DigitalInIOCell extends IOCell {
  val io: DigitalInIOCellBundle
}

trait DigitalOutIOCell extends IOCell {
  val io: DigitalOutIOCellBundle
}

// The following Generic IO cell black boxes have verilog models that mimic a very simple
// implementation of an IO cell. For building a real chip, it is important to implement
// and use similar classes which wrap the foundry-specific IO cells.

abstract class GenericIOCell extends BlackBox with HasBlackBoxResource {
  addResource("/barstools/iocell/vsrc/IOCell.v")
}

class GenericAnalogIOCell extends GenericIOCell with AnalogIOCell {
  val io = IO(new AnalogIOCellBundle)
}
class GenericDigitalGPIOCell extends GenericIOCell with DigitalGPIOCell {
  val io = IO(new DigitalGPIOCellBundle)
}
class GenericDigitalInIOCell extends GenericIOCell with DigitalInIOCell {
  val io = IO(new DigitalInIOCellBundle)
}
class GenericDigitalOutIOCell extends GenericIOCell with DigitalOutIOCell {
  val io = IO(new DigitalOutIOCellBundle)
}

trait IOCellTypeParams {
  def analog(): AnalogIOCell
  def gpio():   DigitalGPIOCell
  def input():  DigitalInIOCell
  def output(): DigitalOutIOCell
}

case class GenericIOCellParams() extends IOCellTypeParams {
  def analog() = Module(new GenericAnalogIOCell)
  def gpio() = Module(new GenericDigitalGPIOCell)
  def input() = Module(new GenericDigitalInIOCell)
  def output() = Module(new GenericDigitalOutIOCell)
}

object IOCell {

  /** From within a RawModule or MultiIOModule context, generate new module IOs from a given
    * signal and return the new IO and a Seq containing all generated IO cells.
    * @param coreSignal The signal onto which to add IO cells
    * @param name An optional name or name prefix to use for naming IO cells
    * @param abstractResetAsAsync When set, will coerce abstract resets to
    *        AsyncReset, and otherwise to Bool (sync reset)
    * @return A tuple of (the generated IO data node, a Seq of all generated IO cell instances)
    */
  def generateIOFromSignal[T <: Data](
    coreSignal:           T,
    name:                 String,
    typeParams:           IOCellTypeParams = GenericIOCellParams(),
    abstractResetAsAsync: Boolean = false
  ): (T, Seq[IOCell]) = {
    val padSignal = IO(DataMirror.internal.chiselTypeClone[T](coreSignal)).suggestName(name)
    val resetFn = if (abstractResetAsAsync) toAsyncReset else toSyncReset
    val iocells = IOCell.generateFromSignal(coreSignal, padSignal, Some(s"iocell_$name"), typeParams, resetFn)
    (padSignal, iocells)
  }

  /** Connect two identical signals together by adding IO cells between them and return a Seq
    * containing all generated IO cells.
    * @param coreSignal The core-side (internal) signal onto which to connect/add IO cells
    * @param padSignal The pad-side (external) signal onto which to connect IO cells
    * @param name An optional name or name prefix to use for naming IO cells
    * @return A Seq of all generated IO cell instances
    */
  val toSyncReset:  (Reset) => Bool = _.asBool()
  val toAsyncReset: (Reset) => AsyncReset = _.asAsyncReset
  def generateFromSignal[T <: Data, R <: Reset](
    coreSignal:        T,
    padSignal:         T,
    name:              Option[String] = None,
    typeParams:        IOCellTypeParams = GenericIOCellParams(),
    concretizeResetFn: (Reset) => R = toSyncReset
  ): Seq[IOCell] = {
    def genCell[T <: Data](
      castToBool:   (T) => Bool,
      castFromBool: (Bool) => T
    )(coreSignal:   T,
      padSignal:    T
    ): Seq[IOCell] = {
      DataMirror.directionOf(coreSignal) match {
        case ActualDirection.Input => {
          val iocell = typeParams.input()
          name.foreach(n => {
            iocell.suggestName(n)
          })
          coreSignal := castFromBool(iocell.io.i)
          iocell.io.ie := true.B
          iocell.io.pad := castToBool(padSignal)
          Seq(iocell)
        }
        case ActualDirection.Output => {
          val iocell = typeParams.output()
          name.foreach(n => {
            iocell.suggestName(n)
          })
          iocell.io.o := castToBool(coreSignal)
          iocell.io.oe := true.B
          padSignal := castFromBool(iocell.io.pad)
          Seq(iocell)
        }
        case _ => throw new Exception(s"Signal does not have a direction and cannot be matched to an IOCell")
      }
    }
    def genCellForClock = genCell[Clock](_.asUInt.asBool, _.asClock) _
    def genCellForAsyncReset = genCell[AsyncReset](_.asBool, _.asAsyncReset) _
    def genCellForAbstractReset = genCell[Reset](_.asBool, concretizeResetFn) _

    (coreSignal, padSignal) match {
      case (coreSignal: Analog, padSignal: Analog) => {
        if (coreSignal.getWidth == 0) {
          Seq()
        } else {
          require(
            coreSignal.getWidth == 1,
            "Analogs wider than 1 bit are not supported because we can't bit-select Analogs (https://github.com/freechipsproject/chisel3/issues/536)"
          )
          val iocell = typeParams.analog()
          name.foreach(n => iocell.suggestName(n))
          iocell.io.core <> coreSignal
          padSignal <> iocell.io.pad
          Seq(iocell)
        }
      }
      case (coreSignal: Clock, padSignal: Clock) => genCellForClock(coreSignal, padSignal)
      case (coreSignal: AsyncReset, padSignal: AsyncReset) => genCellForAsyncReset(coreSignal, padSignal)
      case (coreSignal: Bits, padSignal: Bits) => {
        require(padSignal.getWidth == coreSignal.getWidth, "padSignal and coreSignal must be the same width")
        if (padSignal.getWidth == 0) {
          // This dummy assignment will prevent invalid firrtl from being emitted
          DataMirror.directionOf(coreSignal) match {
            case ActualDirection.Input => coreSignal := 0.U
            case _                     => {}
          }
          Seq()
        } else {
          DataMirror.directionOf(coreSignal) match {
            case ActualDirection.Input => {
              val iocells = padSignal.asBools.zipWithIndex.map { case (sig, i) =>
                val iocell = typeParams.input()
                // Note that we are relying on chisel deterministically naming this in the index order (which it does)
                // This has the side-effect of naming index 0 with no _0 suffix, which is how chisel names other signals
                // An alternative solution would be to suggestName(n + "_" + i)
                name.foreach(n => {
                  iocell.suggestName(n)
                })
                iocell.io.pad := sig
                iocell.io.ie := true.B
                iocell
              }
              // Note that the reverse here is because Cat(Seq(a,b,c,d)) yields abcd, but a is index 0 of the Seq
              coreSignal := Cat(iocells.map(_.io.i).reverse)
              iocells
            }
            case ActualDirection.Output => {
              val iocells = coreSignal.asBools.zipWithIndex.map { case (sig, i) =>
                val iocell = typeParams.output()
                // Note that we are relying on chisel deterministically naming this in the index order (which it does)
                // This has the side-effect of naming index 0 with no _0 suffix, which is how chisel names other signals
                // An alternative solution would be to suggestName(n + "_" + i)
                name.foreach(n => {
                  iocell.suggestName(n)
                })
                iocell.io.o := sig
                iocell.io.oe := true.B
                iocell
              }
              // Note that the reverse here is because Cat(Seq(a,b,c,d)) yields abcd, but a is index 0 of the Seq
              padSignal := Cat(iocells.map(_.io.pad).reverse)
              iocells
            }
            case _ => throw new Exception("Bits signal does not have a direction and cannot be matched to IOCell(s)")
          }
        }
      }
      case (coreSignal: Reset, padSignal: Reset) => genCellForAbstractReset(coreSignal, padSignal)
      case (coreSignal: Vec[_], padSignal: Vec[_]) => {
        require(padSignal.size == coreSignal.size, "size of Vec for padSignal and coreSignal must be the same")
        coreSignal.zip(padSignal).zipWithIndex.foldLeft(Seq.empty[IOCell]) { case (total, ((core, pad), i)) =>
          val ios = IOCell.generateFromSignal(core, pad, name.map(_ + "_" + i), typeParams)
          total ++ ios
        }
      }
      case (coreSignal: Record, padSignal: Record) => {
        coreSignal.elements.foldLeft(Seq.empty[IOCell]) { case (total, (eltName, core)) =>
          val pad = padSignal.elements(eltName)
          val ios = IOCell.generateFromSignal(core, pad, name.map(_ + "_" + eltName), typeParams)
          total ++ ios
        }
      }
      case _ => { throw new Exception("Oops, I don't know how to handle this signal.") }
    }
  }

}
