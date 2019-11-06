package example

import chisel3._
import chisel3.experimental._

import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}

import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp
import freechips.rocketchip.devices.debug.{Debug}

// -------------------------------
// BOOM and/or Rocket Test Harness
// -------------------------------

case object BuildTop extends Field[(Clock, Bool, Parameters) => TopModule[Top]]
case object BuildTopWithDTM extends Field[(Clock, Bool, Parameters) => TopWithDTMModule[TopWithDTM]]

/**
 * Test harness using TSI to bringup the system
 */
class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  // force Chisel to rename module
  override def desiredName = "TestHarness"

  val dut = p(BuildTop)(clock, reset.toBool, p)

  dut.debug := DontCare
  dut.connectSimAXIMem()
  dut.connectSimAXIMMIO()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  dut.l2_frontend_bus_axi4.foreach(axi => {
    axi.tieoff()
    experimental.DataMirror.directionOf(axi.ar.ready) match {
      case core.ActualDirection.Input =>
        axi.r.bits := DontCare
        axi.b.bits := DontCare
      case core.ActualDirection.Output =>
        axi.aw.bits := DontCare
        axi.ar.bits := DontCare
        axi.w.bits := DontCare
    }
  })

  io.success := dut.connectSimSerial()
}

/**
 * Test harness using the Debug Test Module (DTM) to bringup the system
 */
class TestHarnessWithDTM(implicit p: Parameters) extends Module
{
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  // force Chisel to rename module
  override def desiredName = "TestHarness"

  val dut = p(BuildTopWithDTM)(clock, reset.toBool, p)

  dut.reset := reset.asBool | dut.debug.ndreset
  dut.connectSimAXIMem()
  dut.connectSimAXIMMIO()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  dut.l2_frontend_bus_axi4.foreach(axi => {
    axi.tieoff()
    experimental.DataMirror.directionOf(axi.ar.ready) match {
      case core.ActualDirection.Input =>
        axi.r.bits := DontCare
        axi.b.bits := DontCare
      case core.ActualDirection.Output =>
        axi.aw.bits := DontCare
        axi.ar.bits := DontCare
        axi.w.bits := DontCare
    }
  })

  Debug.connectDebug(dut.debug, clock, reset.asBool, io.success)
}
