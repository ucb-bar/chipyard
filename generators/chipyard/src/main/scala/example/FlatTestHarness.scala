package chipyard.example

import chisel3._
import chisel3.experimental.{Analog, BaseModule, DataMirror, Direction}
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.prci.{ClockSourceAtFreqFromPlusArg, ClockBundle, ClockBundleParameters}
import freechips.rocketchip.util.{PlusArg}
import freechips.rocketchip.subsystem.{CacheBlockBytes}
import freechips.rocketchip.devices.debug.{SimJTAG}
import freechips.rocketchip.jtag.{JTAGIO}
import testchipip.{SerialTLKey, UARTAdapter, SimDRAM, TSIHarness, SimTSI}
import chipyard.harness.{BuildTop}

// A "flat" TestHarness that doesn't use IOBinders
// use with caution.
// This example is hard-coded to work only for FlatChipTop, and the ChipLikeRocketConfig
class FlatTestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  // This only works with FlatChipTop
  val lazyDut = LazyModule(new FlatChipTop).suggestName("chiptop")
  val dut = Module(lazyDut.module)

  // Clock
  val clock_source = Module(new ClockSourceAtFreqFromPlusArg("slow_clk_freq_mhz"))
  clock_source.io.power := true.B
  clock_source.io.gate := false.B
  dut.clock_pad := clock_source.io.clk

  // Reset
  dut.reset_pad := reset.asAsyncReset

  // Custom boot
  dut.custom_boot_pad := PlusArg("custom_boot_pin", width=1)

  // Serialized TL
  val sVal = p(SerialTLKey).get
  val serialTLManagerParams = sVal.serialTLManagerParams.get
  require(serialTLManagerParams.isMemoryDevice)

  withClockAndReset(clock, reset) {
    val serial_bits = dut.serial_tl_pad.bits
    if (DataMirror.directionOf(dut.serial_tl_pad.clock) == Direction.Input) {
      dut.serial_tl_pad.clock := clock
    }
    val harnessRAM = TSIHarness.connectRAM(
      lazyDut.system.serdesser.get,
      serial_bits,
      reset)
    io.success := SimTSI.connect(Some(harnessRAM.module.io.tsi), clock, reset)

  }

  // JTAG
  val jtag_wire = Wire(new JTAGIO)
  jtag_wire.TDO.data := dut.jtag_pad.TDO
  jtag_wire.TDO.driven := true.B
  dut.jtag_pad.TCK := jtag_wire.TCK
  dut.jtag_pad.TMS := jtag_wire.TMS
  dut.jtag_pad.TDI := jtag_wire.TDI
  val dtm_success = WireInit(false.B)
  val jtag = Module(new SimJTAG(tickDelay=3)).connect(jtag_wire, clock, reset.asBool, ~(reset.asBool), dtm_success)

  // UART
  UARTAdapter.connect(Seq(dut.uart_pad))
}
