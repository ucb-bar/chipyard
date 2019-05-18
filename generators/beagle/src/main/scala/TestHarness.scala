package beagle

import chisel3._
import chisel3.experimental._

import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}

import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp

class BeagleTestHarness(implicit val p: Parameters) extends Module
{
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

   // force Chisel to rename module
  override def desiredName = "TestHarness"

  val inner = Module(LazyModule(new BeagleTestHarnessBackend).module)
  io.success := inner.io.success
}

class BeagleTestHarnessBackend(implicit val p: Parameters) extends LazyModule
{
  val adapter = LazyModule(new SerialAdapter)
  val serdes = LazyModule(new TLSerdesser(
    w = 4,
    clientParams = ,
    managerParams = ,
    beatBytes = ,
    onTarget = ))

  // harness RAM should not be used...
  // TODO: Is there a way to just "tie off a clientNode connection"
  val harnessRam = LazyModule(new TLTestRam(
    address = ,
    beatBytes = ))

  serdes.managerNode := TLBuffer() := adapter.node
  harnessRam.node := TLFragmenter(..., ...) := TLBuffer() := serdes.clientNode

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val success = Output(Bool())
    })

    val dut = Module(new BeagleChipTop)

    dut.reset := reset
    dut.boot := true.B
    dut.alt_clks.foreach { _ := clock }
    dut.alt_clk_sel := 0.U
    dut.gpio := DontCare
    dut.i2c  := DontCare
    dut.spi  := DontCare
    dut.uart := DontCare
    dut.jtag := DontCare

    // SimSerial <-> SerialAdapter <-> Serdes <--ChipConnection--> Lbwif
    val sim = Module(new testchipip.SimSerial(4))

    sim.io.clock := dut.lbwif_serial_clk
    sim.io.reset := reset
    adapter.io.clock := dut.lbwif_serial_clk
    adapter.io.reset := reset

    sim.io.serial.out <> Queue(adapter.io.serial.out)
    adapter.io.serial.in <> Queue(sim.io.serial.in)

    serdes.module.io.ser <> dut.lbwif_serial

    io.success := sim.io.exit
  }
}
