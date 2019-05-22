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

  println("TESTHARNESS")
  val lbwif = LazyModule(new TLSerdesser(
    w = p(LbwifBitWidth),
    clientParams = TLClientParameters(
      name = "tl_serdes_control",
      sourceId = IdRange(0, (1 << 9)), // match DUT source bits
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

  val harnessRam = LazyModule(new TLTestRAM(
    address = p(HbwifTLKey).managerAddressSet,
    beatBytes = p(ExtMem).get.master.beatBytes))

  lbwif.managerNode := TLBuffer() := adapter.node
  harnessRam.node := TLFragmenter(p(ExtMem).get.master.beatBytes, p(CacheBlockBytes)) := TLBuffer() := lbwif.clientNode

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
    dut.hbwif := DontCare
    dut.hbwif_diff_clks := DontCare

    // SimSerial <-> SerialAdapter <-> Serdes <--ChipConnection--> Lbwif

    println(s"DEBUG: SerialWidth: ${SerialAdapter.SERIAL_IF_WIDTH}")
    val sim = Module(new SimSerial(SerialAdapter.SERIAL_IF_WIDTH))

    sim.io.clock := clock
    sim.io.reset := reset
    //adapter.module.io.clock := clock
    //adapter.module.io.reset := reset

    val lbwif_tx_queue = Module(new AsyncQueue(chiselTypeOf(lbwif.module.io.ser.out.bits)))
    val lbwif_rx_queue = Module(new AsyncQueue(chiselTypeOf(lbwif.module.io.ser.in.bits)))

    lbwif_tx_queue.io.enq <> lbwif.module.io.ser.out
    lbwif_tx_queue.io.enq_clock <> clock
    lbwif_tx_queue.io.enq_reset <> reset

    lbwif.module.io.ser.in <> lbwif_rx_queue.io.deq
    lbwif_rx_queue.io.deq_clock <> clock
    lbwif_rx_queue.io.deq_reset <> reset

    dut.lbwif_serial.in <> lbwif_tx_queue.io.deq
    lbwif_tx_queue.io.deq_clock := dut.lbwif_serial_clk
    lbwif_tx_queue.io.deq_reset := reset // TODO: should be onchip reset

    lbwif_rx_queue.io.enq <> dut.lbwif_serial.out
    lbwif_rx_queue.io.enq_clock := dut.lbwif_serial_clk
    lbwif_rx_queue.io.enq_reset := reset // TODO: should be onchip reset

    sim.io.serial.out <> Queue(adapter.module.io.serial.out)
    adapter.module.io.serial.in <> Queue(sim.io.serial.in)

    io.success := sim.io.exit
  }
}
