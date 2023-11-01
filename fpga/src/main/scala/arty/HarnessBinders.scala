package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.devices.debug.{HasPeripheryDebug}
import freechips.rocketchip.jtag.{JTAGIO}

import sifive.blocks.devices.uart.{UARTPortIO, HasPeripheryUARTModuleImp}
import sifive.blocks.devices.jtag.{JTAGPins, JTAGPinsFromPort}
import sifive.blocks.devices.pinctrl.{BasePin}

import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipyard.harness.{HarnessBinder}
import chipyard.iobinders._

class WithArtyDebugResetHarnessBinder extends HarnessBinder({
  case (th: ArtyFPGATestHarness, port: DebugResetPort) => {
    th.dut_ndreset := port.io // Debug module reset
  }
})

class WithArtyJTAGResetHarnessBinder extends HarnessBinder({
  case (th: ArtyFPGATestHarness, port: JTAGResetPort) => {
    port.io := PowerOnResetFPGAOnly(th.clock_32MHz) // JTAG module reset
  }
})

class WithArtyJTAGHarnessBinder extends HarnessBinder({
  case (th: ArtyFPGATestHarness, port: JTAGPort) => {
    val jtag_wire = Wire(new JTAGIO)
    jtag_wire.TDO.data := port.io.TDO
    jtag_wire.TDO.driven := true.B
    port.io.TCK := jtag_wire.TCK
    port.io.TMS := jtag_wire.TMS
    port.io.TDI := jtag_wire.TDI

    val io_jtag = Wire(new JTAGPins(() => new BasePin(), false)).suggestName("jtag")

    JTAGPinsFromPort(io_jtag, jtag_wire)

    io_jtag.TCK.i.ival := IBUFG(IOBUF(th.jd_2).asClock).asBool

    IOBUF(th.jd_5, io_jtag.TMS)
    PULLUP(th.jd_5)

    IOBUF(th.jd_4, io_jtag.TDI)
    PULLUP(th.jd_4)

    IOBUF(th.jd_0, io_jtag.TDO)

    // mimic putting a pullup on this line (part of reset vote)
    th.SRST_n := IOBUF(th.jd_6)
    PULLUP(th.jd_6)

    // ignore the po input
    io_jtag.TCK.i.po.map(_ := DontCare)
    io_jtag.TDI.i.po.map(_ := DontCare)
    io_jtag.TMS.i.po.map(_ := DontCare)
    io_jtag.TDO.i.po.map(_ := DontCare)
  }
})

class WithArtyUARTHarnessBinder extends HarnessBinder({
  case (th: ArtyFPGATestHarness, port: UARTPort) => {
    withClockAndReset(th.clock_32MHz, th.ck_rst) {
      IOBUF(th.uart_rxd_out,  port.io.txd)
      port.io.rxd := IOBUF(th.uart_txd_in)
    }
  }
})
