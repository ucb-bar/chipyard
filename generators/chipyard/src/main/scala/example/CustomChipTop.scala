package chipyard.example

import chisel3._
import chipyard.iobinders._

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{HasTileLinkLocations, PBUS}
import barstools.iocell.chisel._
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

// An example "uart" IO cell
// This would be a BlackBox for a actual VLSI flow
// Note: A "real" multi-bit IOCell would typically not expose the core/pad
// as typed bundles, but as raw bits in a vec. This example exposes the
// raw type for simplicit
class CustomUARTIOCell extends RawModule {
  val io = IO(new Bundle {
    val core = new Bundle {
      val txd = Input(Bool())
      val rxd = Output(Bool())
    }
    val pad = new Bundle {
      val txd = Output(Bool())
      val rxd = Input(Bool())
    }
  })

  io.pad <> io.core
}

class CustomChipTop(implicit p: Parameters) extends LazyModule with BindingScope with HasIOBinders {
  // making the module name ChipTop instead of CustomChipTop means
  // we don't have to set the TOP make variable to CustomChipTop
  override lazy val desiredName = "ChipTop"

  // The system module specified by BuildSystem
  lazy val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("system")

  lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
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

    // custom IO cells (like multi-bit IO "slice" cells) may be instantiated here
    val uart_io_cell = Module(new CustomUARTIOCell)
  }
}

class WithCustomIOCells extends Config((site, here, up) => {
  case IOCellKey => CustomIOCellParams()
})

class WithCustomChipTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new CustomChipTop()(p)
})

class WithCustomChipTopUART extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp, chiptop: CustomChipTop) => {
    require(system.uart.size == 1)
    val port = IO(new UARTPortIO(system.uart(0).c)).suggestName("uart")
    val uart_cell = chiptop.module.uart_io_cell

    port.txd := uart_cell.io.pad.txd
    uart_cell.io.pad.rxd := port.rxd

    uart_cell.io.core.txd := system.uart(0).txd
    system.uart(0).rxd := uart_cell.io.core.txd

    val freqMHz = system.outer.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(PBUS).dtsFrequency.get / 1000000

    (Seq(UARTPort(port, 0, freqMHz.toInt)), Nil)

  }
})


