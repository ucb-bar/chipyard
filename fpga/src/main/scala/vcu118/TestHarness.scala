package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp}
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy.{InModuleBody, BundleBridgeSource}

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._

class VCU118FPGATestHarness(override implicit val p: Parameters) extends VCU118Shell {

  require(p(PeripheryUARTKey).size >= 1)

  designParameters(UARTOverlayKey).foreach { uok =>
    topDesign match { case td: HasPlatformIO =>
      io_uart_bb))
    }
  }
}

