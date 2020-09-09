package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{attach, IO}

import freechips.rocketchip.util._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem.{NExtTopInterrupts}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.pwm._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.mockaon._
import sifive.blocks.devices.jtag._
import sifive.blocks.devices.pinctrl._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipsalliance.rocketchip.config._
import sifive.fpgashells.shell._

import chipyard.iobinders.{OverrideIOBinder, GetSystemParameters}
import chipyard.{HasHarnessSignalReferences}
import freechips.rocketchip.diplomacy._

class WithUARTConnection1 extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {

    implicit val p: Parameters = GetSystemParameters(system)

    val io_uart_pins = p(PeripheryUARTKey).zipWithIndex map { case (c, i) => IO(new UARTPortIO(c)).suggestName(s"uart_$i") }
    (io_uart_pins zip system.uart) map { case (p, r) => p <> r }

    val harnessFn = (th: HasHarnessSignalReferences) => {
      println(th)
      println("Got here - --  - - - ")
      Nil
    }
    //val harnessFn = (baseTh: HasHarnessSignalReferences) => {
    //  println("DEBUG: ---------------------- 0")
    //  baseTh match { case th: VCU118Shell =>
    //    println("DEBUG: ---------------------- 1")

    //    val io_uart_pins_bb = p(PeripheryUARTKey) map { c => BundleBridgeSource(() => (new UARTPortIO(c))) }

    //    InModuleBody {
    //      (io_uart_pins_bb zip io_uart_pins) map { case (p, r) => p.bundle <> r }
    //    }

    //    require(p(PeripheryUARTKey).size >= 1)

    //    println("DEBUG: ---------------------- 2")

    //    th.designParameters(UARTOverlayKey).foreach { uok =>
    //      println("DEBUG: ---------------------- 3")
    //      uok.place(UARTDesignInput(io_uart_pins_bb(0))).overlayOutput
    //    }

    //    Nil
    //  }
    //}

    Seq((Nil, Nil, Some(harnessFn)))
  }
})

