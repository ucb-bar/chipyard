package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.diplomacy.{InModuleBody, BundleBridgeSource}
import freechips.rocketchip.config.{Parameters}

import chipyard.{BuildSystem}

import sifive.blocks.devices.uart._

trait HasPlatformIO {
  val io_uart_bb: BundleBridgeSource[UARTPortIO]
}

class VCU118Platform(override implicit val p: Parameters) extends LazyModule
  with HasPlatformIO {

  val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("system")

  // BundleBridgeSource is a was for Diplomacy to connect something from very deep in the design
  // to somewhere much, much higher. For ex. tunneling trace from the tile to the very top level.
  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(p(PeripheryUARTKey)(0))))

  override lazy val module = new VCU118PlatformModule(this)
}

class VCU118PlatformModule[+L <: VCU118Platform](_outer: L) extends LazyModuleImp(_outer) {

  _outer.lazySystem.module match { case sys: HasPeripheryUARTModuleImp =>
    // create UART pins in Platform
    //val io_uart_pins_temp = p(PeripheryUARTKey).zipWithIndex map { case (c, i) => IO(new UARTPortIO(c)).suggestName(s"uart_$i") }

   //(io_uart_pins_temp zip sys.uart) map { case (p, r) => p <> r }
   _outer.io_uart_bb.bundle <> sys.uart(0)
  }

}
