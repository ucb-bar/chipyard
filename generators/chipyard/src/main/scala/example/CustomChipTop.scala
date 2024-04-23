package chipyard.example

import chisel3._
import chipyard.iobinders._

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy.{InModuleBody}
import freechips.rocketchip.subsystem.{PBUS, HasTileLinkLocations}
import chipyard.iocell._
import chipyard._
import chipyard.harness.{BuildTop}
import sifive.blocks.devices.uart._

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

class WithBrokenOutUARTIO extends OverrideIOBinder({
  (system: HasPeripheryUART) => {
    val uart_txd = IO(Output(Bool()))
    val uart_rxd = IO(Input(Bool()))
    system.uart(0).rxd := uart_rxd
    uart_txd := system.uart(0).txd
    val where = PBUS // TODO fix
    val bus = system.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(where)
    val freqMHz = bus.dtsFrequency.get / 1000000
    (Seq(UARTPort(() => {
      val uart_wire = Wire(new UARTPortIO(system.uart(0).c))
      uart_wire.txd := uart_txd
      uart_rxd := uart_wire.rxd
      uart_wire
    }, 0, freqMHz.toInt)), Nil)
  }
})
