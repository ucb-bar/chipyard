// See LICENSE for license details.
package chipyard.fpga.nexysvideo

import chisel3._

import freechips.rocketchip.subsystem.{PeripheryBusKey}
import freechips.rocketchip.tilelink.{TLBundle}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.diplomacy.{LazyRawModuleImp}
import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}

import sifive.blocks.devices.uart.{UARTPortIO, HasPeripheryUARTModuleImp, UARTParams}
import sifive.blocks.devices.jtag.{JTAGPins, JTAGPinsFromPort}
import sifive.blocks.devices.pinctrl.{BasePin}

//import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._

import chipyard._
import chipyard.harness._
import chipyard.iobinders._

class WithNexysVideoUARTTSI(uartBaudRate: BigInt = 115200) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val nexysvideoth = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    nexysvideoth.io_uart_bb.bundle <> port.io.uart
    nexysvideoth.other_leds(1) := port.io.dropped
    nexysvideoth.other_leds(2) := port.io.tsi2tl_state(0)
    nexysvideoth.other_leds(3) := port.io.tsi2tl_state(1)
    nexysvideoth.other_leds(4) := port.io.tsi2tl_state(2)
    nexysvideoth.other_leds(5) := port.io.tsi2tl_state(3)
  }
})

class WithNexysVideoDDRTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    val bundles = nexysTh.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

// Uses PMOD JA/JB
class WithNexysVideoSerialTLToGPIO extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort) => {
    val nexysTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[NexysVideoHarness]
    val harnessIO = IO(port.io.cloneType).suggestName("serial_tl")
    harnessIO <> port.io
    val clkIO = IOPin(harnessIO.clock)
    val packagePinsWithPackageIOs = Seq(
      ("AB22", clkIO),
      ("AB21", IOPin(harnessIO.bits.out.valid)),
      ("AB20", IOPin(harnessIO.bits.out.ready)),
      ("AB18", IOPin(harnessIO.bits.in.valid)),
      ("Y21", IOPin(harnessIO.bits.in.ready)),
      ("AA21", IOPin(harnessIO.bits.out.bits, 0)),
      ("AA20", IOPin(harnessIO.bits.out.bits, 1)),
      ("AA18", IOPin(harnessIO.bits.out.bits, 2)),
      ("V9", IOPin(harnessIO.bits.out.bits, 3)),
      ("V8", IOPin(harnessIO.bits.in.bits, 0)),
      ("V7", IOPin(harnessIO.bits.in.bits, 1)),
      ("W7", IOPin(harnessIO.bits.in.bits, 2)),
      ("W9", IOPin(harnessIO.bits.in.bits, 3))
    )
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      nexysTh.xdc.addPackagePin(io, pin)
      nexysTh.xdc.addIOStandard(io, "LVCMOS33")
    }}

    // Don't add IOB to the clock, if its an input
    if (DataMirror.directionOf(port.io.clock) == Direction.Input) {
      packagePinsWithPackageIOs foreach { case (pin, io) => {
        nexysTh.xdc.addIOB(io)
      }}
    }

    nexysTh.sdc.addClock("ser_tl_clock", clkIO, 100)
    nexysTh.sdc.addGroup(pins = Seq(clkIO))
    nexysTh.xdc.clockDedicatedRouteFalse(clkIO)
  }
})

