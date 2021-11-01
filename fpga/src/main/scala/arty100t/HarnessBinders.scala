package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.jtag._
import sifive.blocks.devices.pinctrl._

import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipyard.harness.{ComposeHarnessBinder, OverrideHarnessBinder}
import chipyard.iobinders.JTAGChipIO

class WithArtyResetHarnessBinder extends ComposeHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: ArtyFPGATestHarness, ports: Seq[Bool]) => {
    require(ports.size == 2)

    withClockAndReset(th.clock_32MHz, th.ck_rst) {
      // Debug module reset
      th.dut_ndreset := ports(0)

      // JTAG reset
      ports(1) := PowerOnResetFPGAOnly(th.clock_32MHz)
    }
  }
})

class WithArtyJTAGHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: ArtyFPGATestHarness, ports: Seq[Data]) => {
    ports.map {
      case j: JTAGChipIO =>
        withClockAndReset(th.buildtopClock, th.hReset) {
          val jtag_wire = Wire(new JTAGIO)
          jtag_wire.TDO.data := j.TDO
          jtag_wire.TDO.driven := true.B
          j.TCK := jtag_wire.TCK
          j.TMS := jtag_wire.TMS
          j.TDI := jtag_wire.TDI

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
    }
  }
})

class WithArtyUARTHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: ArtyFPGATestHarness, ports: Seq[UARTPortIO]) => {
    withClockAndReset(th.clock_32MHz, th.ck_rst) {
      IOBUF(th.uart_rxd_out,  ports.head.txd)
      ports.head.rxd := IOBUF(th.uart_txd_in)
    }
  }
})
