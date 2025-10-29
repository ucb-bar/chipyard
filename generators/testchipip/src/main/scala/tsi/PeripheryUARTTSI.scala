package testchipip.tsi

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam}

import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._

import sifive.blocks.devices.uart._
import testchipip.serdes._
import testchipip.uart.{UARTToSerial}


case class UARTTSIClientParams(
  uartParams: UARTParams = UARTParams(0),
  tlbus: TLBusWrapperLocation = FBUS
)

case object UARTTSIClientKey extends Field[Option[UARTTSIClientParams]](None)

class UARTTSIIO(val uartParams: UARTParams) extends Bundle {
  val uart = new UARTPortIO(uartParams)
  val dropped = Output(Bool())
  val tsi2tl_state = Output(UInt())
}

// This trait adds a UART port to the subsystem that transports TSI.
// It is supposed to be used for FPGA-harnesses or FPGA prototypes
// This should not be used for ASIC implemnetations
trait CanHavePeripheryUARTTSI { this: BaseSubsystem =>
  val uart_tsi = p(UARTTSIClientKey).map { params =>
    val tlbus = locateTLBusWrapper(params.tlbus)
    val uartParams = params.uartParams
    val tsi2tl = tlbus { LazyModule(new TSIToTileLink) }
    tlbus.coupleFrom("uart_tsi") { _ := tsi2tl.node }
    val uart_bus_io = tlbus { InModuleBody {
      val uart_to_serial = Module(new UARTToSerial(tlbus.dtsFrequency.get, uartParams))
      val width_adapter = Module(new SerialWidthAdapter(8, TSI.WIDTH))
      tsi2tl.module.io.tsi.flipConnect(width_adapter.io.wide)
      width_adapter.io.narrow.flipConnect(uart_to_serial.io.serial)
      val uart_tsi_io = IO(new UARTTSIIO(uartParams))
      uart_tsi_io.uart <> uart_to_serial.io.uart
      uart_tsi_io.dropped := uart_to_serial.io.dropped
      uart_tsi_io.tsi2tl_state := tsi2tl.module.io.state
      uart_tsi_io
    } }

    val uart_tsi_io = InModuleBody {
      val uart_tsi_io = IO(new UARTTSIIO(uartParams))
      uart_tsi_io <> uart_bus_io
      uart_tsi_io
    }
    uart_tsi_io
  }
}
