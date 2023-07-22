package chipyard.example

import chisel3._
import chipyard.iobinders._

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy.{InModuleBody}
import barstools.iocell.chisel._
import chipyard._
import chipyard.harness.{BuildTop}

// A "custom" IOCell with additional I/O
// The IO don't do anything here in this example
class CustomDigitalInIOCellBundle extends DigitalInIOCellBundle {
  val custom_out = Output(Bool())
  val custom_in = Input(Bool())
}

// Using a custom digital in iocell instead of the default one
class CustomDigitalInIOCell extends RawModule with DigitalInIOCell {
  val io = IO(new CustomDigitalInIOCellBundle)
  io.i := io.pad
  io.custom_out := io.pad
}

case class CustomIOCellParams() extends IOCellTypeParams {
  def analog() = Module(new GenericAnalogIOCell)
  def gpio() = Module(new GenericDigitalGPIOCell)
  def input() = Module(new CustomDigitalInIOCell)
  def output() = Module(new GenericDigitalOutIOCell)
}

class CustomChipTop(implicit p: Parameters) extends ChipTop {
  // making the module name ChipTop instead of CustomChipTop means
  // we don't have to set the TOP make variable to CustomChipTop
  override lazy val desiredName = "ChipTop"

  // InModuleBody blocks are executed within the LazyModuleImp of this block
  InModuleBody {
    iocellMap.foreach { case (interface, cells) => {
      cells.foreach { _ match {
        case c: CustomDigitalInIOCell => {
          c.io.custom_in := false.B
        }
        case c: GenericDigitalOutIOCell => {
          // do nothing
        }
        case c => {
          require(false, "Unsupported iocell type ${c.getClass}")
        }
      }}
    }}

    // demonstrate accessing the iocellMap directly
    val serialTLIOCells = iocellMap("interface testchipip.CanHavePeripheryTLSerial")
  }
}

class WithCustomIOCells extends Config((site, here, up) => {
  case IOCellKey => CustomIOCellParams()
})

class WithCustomChipTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new CustomChipTop()(p)
})
