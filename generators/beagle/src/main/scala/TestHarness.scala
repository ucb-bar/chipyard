package beagle

import chisel3._
import chisel3.util._
import chisel3.experimental._

import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp
import freechips.rocketchip.devices.tilelink.{TLTestRAM}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{AsyncQueue}

import testchipip.{SerialAdapter, SimSerial, TLSerdesser}

import hbwif.tilelink.{HbwifTLKey}

class BeagleTestHarness(implicit val p: Parameters) extends Module
{
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

   // force Chisel to rename module
  override def desiredName = "TestHarness"

  val inner = Module(LazyModule(new BeagleTestHarnessInner).module)
  io.success := inner.io.success
}

class BeagleTestHarnessInner(implicit p: Parameters) extends LazyModule
{
  val adapter = LazyModule(new SerialAdapter(1 << 4))

  val lbwif = LazyModule(new TLSerdesser(
    w = p(LbwifBitWidth),
    clientParams = TLClientParameters(
      name = "tl_serdes_control",
      sourceId = IdRange(0, (1 << 13)), // match DUT source bits
      requestFifo = true),
    managerParams = TLManagerParameters(
      address = Seq(AddressSet(p(ExtMem).get.master.base, p(ExtMem).get.master.size-1)),
      regionType = RegionType.UNCACHED, // cacheable
      executable = true,
      fifoId = Some(0),
      supportsGet        = TransferSizes(1, p(CacheBlockBytes)),
      supportsPutFull    = TransferSizes(1, p(CacheBlockBytes)),
      supportsPutPartial = TransferSizes(1, p(CacheBlockBytes))),
     beatBytes = p(ExtMem).get.master.beatBytes,
     endSinkId = 0))

  val harness_rams = p(HbwifTLKey).managerAddressSet.map(addrSet =>
    LazyModule(new TLTestRAM(
      address = addrSet,
      beatBytes = p(ExtMem).get.master.beatBytes,
      trackCorruption = false)))

  val mem_xbar = LazyModule(new TLXbar)
  harness_rams.foreach { ram =>
    ram.node := TLFragmenter(p(ExtMem).get.master.beatBytes, p(CacheBlockBytes)) := mem_xbar.node
  }

  lbwif.managerNode := TLBuffer() := adapter.node
  mem_xbar.node := TLBuffer() := lbwif.clientNode

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val success = Output(Bool())
    })

    val dut = Module(new BeagleChipTop)

    val harness_clk_divider = Module(new testchipip.ClockDivider(2))
    harness_clk_divider.io.divisor := 2.U
    val harness_slow_clk = harness_clk_divider.io.clockOut

    val harness_fast_clk = hbwif.ClockToDifferential(clock)

    dut.reset := reset
    dut.boot := true.B
    dut.single_clks.foreach { _ := harness_slow_clk }
    dut.diff_clks.foreach { _ := DontCare }
    dut.diff_clks.foreach { diff_clk =>
      attach(diff_clk.p, harness_fast_clk.p)
      attach(diff_clk.n, harness_fast_clk.n)
    }
    dut.bh_clk_sel := 0.U
    dut.rs_clk_sel := 0.U
    dut.uncore_clk_sel := 0.U
    dut.gpio := DontCare
    dut.i2c  := DontCare
    dut.spi  := DontCare
    dut.uart := DontCare
    dut.jtag := DontCare
    dut.hbwif := DontCare
    dut.hbwif_diff_clks.foreach { _ := DontCare }
    dut.hbwif_diff_clks.foreach { diff_clk =>
      attach(diff_clk.p, harness_fast_clk.p)
      attach(diff_clk.n, harness_fast_clk.n)
    }

    // SimSerial <-> SerialAdapter <-> Serdes <--ChipConnection--> Lbwif

    val sim = Module(new SimSerial(SerialAdapter.SERIAL_IF_WIDTH))

    sim.io.clock := clock
    sim.io.reset := reset

    val lbwif_tx_queue = Module(new AsyncQueue(chiselTypeOf(lbwif.module.io.ser.out.bits)))
    val lbwif_rx_queue = Module(new AsyncQueue(chiselTypeOf(lbwif.module.io.ser.in.bits)))

    lbwif_tx_queue.io.enq <> lbwif.module.io.ser.out
    lbwif_tx_queue.io.enq_clock <> clock
    lbwif_tx_queue.io.enq_reset <> reset

    lbwif.module.io.ser.in <> lbwif_rx_queue.io.deq
    lbwif_rx_queue.io.deq_clock <> clock
    lbwif_rx_queue.io.deq_reset <> reset

    dut.lbwif_serial.in <> lbwif_tx_queue.io.deq
    lbwif_tx_queue.io.deq_clock := dut.lbwif_clk_out
    lbwif_tx_queue.io.deq_reset := reset // TODO: should be onchip reset

    lbwif_rx_queue.io.enq <> dut.lbwif_serial.out
    lbwif_rx_queue.io.enq_clock := dut.lbwif_clk_out
    lbwif_rx_queue.io.enq_reset := reset // TODO: should be onchip reset

    sim.io.serial.out <> Queue(adapter.module.io.serial.out)
    adapter.module.io.serial.in <> Queue(sim.io.serial.in)

    io.success := sim.io.exit
  }
}
