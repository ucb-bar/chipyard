package chipyard.fpga.arty

import chisel3._
import chisel3.experimental.{Analog}

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.subsystem._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import barstools.iocell.chisel._

import testchipip._

import chipyard.harness.OverrideHarnessBinder
import chipyard.HasHarnessSignalReferences
import chipyard.iobinders.GetSystemParameters

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

class WithArtyJTAGHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: ArtyFPGATestHarness, ports: Seq[JTAGIO]) => {
  // (system: HasPeripheryDebugModuleImp, th: ArtyFPGATestHarness, ports: Seq[Data]) => {
    // ports.map {
    //   case d: ClockedDMIIO =>
    //     // Want to error here.
    //   case j: JTAGIO =>
    //     //val dtm_success = WireInit(false.B)
    //     //when (dtm_success) { th.success := true.B }
    //     //val jtag = Module(new SimJTAG(tickDelay=3)).connect(j, th.harnessClock, th.harnessReset.asBool, ~(th.harnessReset.asBool), dtm_success)

    //     j.TCK.i.ival := IBUFG(IOBUF(th.jd_2).asClock).asUInt

    //     IOBUF(th.jd_5, j.TMS)
    //     PULLUP(th.jd_5)

    //     IOBUF(th.jd_4, j.TDI)
    //     PULLUP(th.jd_4)

    //     IOBUF(th.jd_0, j.TDO)

    //     // mimic putting a pullup on this line (part of reset vote)
    //     th.SRST_n := IOBUF(th.jd_6)
    //     PULLUP(th.jd_6)

    //     IOBUF(th.jd_1, j.TRSTn)
    //     PULLUP(th.jd_1)
    // }
    Nil
  }
})

class WithArtyUARTHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: ArtyFPGATestHarness, ports: Seq[UARTPortIO]) => {
  // (system: HasPeripheryUARTModuleImp, th: ArtyFPGATestHarness, ports: Seq[UARTPortIO]) => {
    // UARTAdapter.connect(ports)(system.p)
    // IOBUF(th.ck_io(2),  ports.txd)
    // IOBUF(th.ck_io(3),  ports.rxd)
    Nil
  }
})