package testchipip.tsi

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config}
import sifive.blocks.devices.uart.{UARTParams}

// Attach a TSI-over-UART-to-TileLink device to this system
class WithUARTTSIClient(initBaudRate: BigInt = BigInt(115200)) extends Config((site, here, up) => {
  case UARTTSIClientKey => Some(UARTTSIClientParams(UARTParams(0, initBaudRate=initBaudRate)))
})
