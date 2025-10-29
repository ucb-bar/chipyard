package testchipip.test

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.tilelink.{DevNullParams, TLTestRAM, TLROM, TLError}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tilelink._
import freechips.rocketchip.unittest._
import freechips.rocketchip.util._
import scala.math.max

// TODO UnitTests should reside with the sub-projects
import testchipip.iceblk._
import testchipip.serdes._
import testchipip.soc._
import testchipip.util._
import testchipip.tsi._

class BlockDeviceTrackerTestDriver(nSectors: Int)(implicit p: Parameters)
    extends LazyModule with HasBlockDeviceParameters {
  val bdParams = p(BlockDeviceKey).get
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "blkdev-testdriver", sourceId = IdRange(0, 1))))))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val start = Input(Bool())
      val finished = Output(Bool())
      val front = new BlockDeviceTrackerIO
    })

    val req = io.front.req
    val complete = io.front.complete
    val (tl, edge) = node.out(0)

    val (s_start :: s_bdev_write_req :: s_bdev_write_complete ::
         s_bdev_read_req :: s_bdev_read_complete ::
         s_mem_read_req :: s_mem_read_resp :: s_done :: Nil) = Enum(8)
    val state = RegInit(s_start)

    when (io.start && state === s_start) { state := s_bdev_write_req }
    when (state === s_bdev_write_req && req.ready) {
      state := s_bdev_write_complete
    }
    when (state === s_bdev_write_complete && complete.valid) {
      state := s_bdev_read_req
    }
    when (state === s_bdev_read_req && req.ready) {
      state := s_bdev_read_complete
    }
    when (state === s_bdev_read_complete && complete.valid) {
      state := s_mem_read_req
    }

    when (tl.a.fire) { state := s_mem_read_resp }
    val (read_beat, read_sector_done) = Counter(tl.d.fire, dataBeats)
    val (read_sector, read_all_done) = Counter(read_sector_done, nSectors)
    when (read_sector_done) { state := s_mem_read_req }
    when (read_all_done) { state := s_done }

    req.valid := state.isOneOf(s_bdev_write_req, s_bdev_read_req)
    req.bits.addr := Mux(state === s_bdev_write_req, 0x10000.U, 0x0.U)
    req.bits.offset := 0.U
    req.bits.len := nSectors.U
    req.bits.write := state === s_bdev_write_req
    req.bits.tag := DontCare
    complete.ready := state.isOneOf(s_bdev_write_complete, s_bdev_read_complete)

    val dataSize = log2Ceil(dataBytes)
    tl.a.valid := state === s_mem_read_req
    tl.a.bits := edge.Get(0.U, read_sector << dataSize.U, dataSize.U)._2
    tl.d.ready := state === s_mem_read_resp

    tl.b.ready := false.B
    tl.c.valid := false.B
    tl.e.valid := false.B

    io.finished := state === s_done

    val beatBytes = dataBitsPerBeat / 8
    val full_beat = WireInit(UInt(8.W), init = Cat(read_sector, read_beat))
    val expected_data = Fill(beatBytes, full_beat)

    assert(!tl.d.valid || tl.d.bits.data === expected_data,
      "Unexpected data read\n")
  }
}

class BlockDeviceTrackerTest(implicit p: Parameters) extends LazyModule
    with HasBlockDeviceParameters {
  val bdParams = p(BlockDeviceKey).get
  val nSectors = 4
  val beatBytes = dataBitsPerBeat / 8

  val testBytes = Seq.tabulate(nSectors * dataBeats)(
    i => Seq.fill(beatBytes) { i.toByte }).flatten

  val testram = LazyModule(new TLTestRAM(
    address = AddressSet(0x0, 0xffff),
    beatBytes = beatBytes))
  val testrom = LazyModule(new TLROM(
    0x10000, 64 * dataBytes, testBytes,
    beatBytes = beatBytes))

  val tracker = LazyModule(new BlockDeviceTracker(0))
  val driver = LazyModule(new BlockDeviceTrackerTestDriver(nSectors))
  val xbar = LazyModule(new TLXbar)

  xbar.node := driver.node
  xbar.node := tracker.node
  testram.node := TLBuffer() := TLFragmenter(beatBytes, dataBytes) := xbar.node
  testrom.node := TLBuffer() := TLFragmenter(beatBytes, dataBytes) := xbar.node

  lazy val module = new LazyModuleImp(this) with HasUnitTestIO {
    val io = IO(new Bundle with UnitTestIO)
    val blkdev = Module(new BlockDeviceModel(nSectors, bdParams))
    blkdev.io <> tracker.module.io.bdev
    tracker.module.io.front <> driver.module.io.front
    driver.module.io.start := io.start
    io.finished := driver.module.io.finished
  }
}

class BlockDeviceTrackerTestWrapper(implicit p: Parameters) extends UnitTest {
  val testParams = p.alterPartial({
    case BlockDeviceKey => Some(BlockDeviceConfig())
  })
  val test = Module(LazyModule(
    new BlockDeviceTrackerTest()(testParams)).module)
  test.io.start := io.start
  io.finished := test.io.finished
}

class SerdesTest(implicit p: Parameters) extends LazyModule {
  val idBits = 2
  val beatBytes = 8
  val lineBytes = 64
  val serWidth = 32

  val fuzzer = LazyModule(new TLFuzzer(
    nOperations = 32,
    inFlight = 1 << idBits))

  val serdes = LazyModule(new TLSerdesser(
    flitWidth = serWidth,
    clientPortParams = None,
    managerPortParams = Some(TLSlavePortParameters.v1(
      beatBytes = beatBytes,
      managers = Seq(TLSlaveParameters.v1(
        address = Seq(AddressSet(0, 0xffff)),
        regionType = RegionType.UNCACHED,
        supportsGet = TransferSizes(1, lineBytes),
        supportsPutFull = TransferSizes(1, lineBytes)))
    ))
  ))

  val desser = LazyModule(new TLSerdesser(
    flitWidth = serWidth,
    managerPortParams = None,
    clientPortParams = Some(TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = "tl-desser",
        sourceId = IdRange(0, 1 << idBits)))
    ))
  ))

  val testram = LazyModule(new TLTestRAM(
    address = AddressSet(0, 0xffff),
    beatBytes = beatBytes))

  serdes.managerNode.get := TLBuffer() := fuzzer.node
  testram.node := TLBuffer() := TLFragmenter(beatBytes, lineBytes) := desser.clientNode.get

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle { val finished = Output(Bool()) })

    val qDepth = 5

    for (i <- 0 until 5) {
      desser.module.io.ser(i).in <> Queue(serdes.module.io.ser(i).out, qDepth)
      serdes.module.io.ser(i).in <> Queue(desser.module.io.ser(i).out, qDepth)
    }
    io.finished := fuzzer.module.io.finished
  }
}

class SerdesTestWrapper(implicit p: Parameters) extends UnitTest {
  val testReset = RegInit(true.B)
  val test = Module(LazyModule(new SerdesTest).module)
  io.finished := test.io.finished
  test.reset := testReset || reset.asBool

  when (testReset && io.start) { testReset := false.B }
}

class BidirectionalSerdesTest(phyParams: SerialPhyParams)(implicit p: Parameters) extends LazyModule {
  val idBits = 2
  val beatBytes = 8
  val lineBytes = 64

  val fuzzer = Seq.fill(2) { LazyModule(new TLFuzzer(
    nOperations = 32,
    inFlight = 1 << idBits)) }

  val serdes = Seq.fill(2) { LazyModule(new TLSerdesser(
    flitWidth = phyParams.flitWidth,
    clientPortParams = Some(TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = "tl-desser",
        sourceId = IdRange(0, 1 << idBits)))
    )),
    managerPortParams = Some(TLSlavePortParameters.v1(
      managers = Seq(TLSlaveParameters.v1(
        address = Seq(AddressSet(0, 0xffff)),
        regionType = RegionType.UNCACHED,
        supportsGet = TransferSizes(1, lineBytes),
        supportsPutFull = TransferSizes(1, lineBytes))
      ),
      beatBytes = 8
    ))
  )) }

  val testram = Seq.fill(2) { LazyModule(new TLTestRAM(
    address = AddressSet(0, 0xffff),
    beatBytes = beatBytes))
  }

  serdes(0).managerNode.get := TLBuffer() := fuzzer(0).node
  serdes(1).managerNode.get := TLBuffer() := fuzzer(1).node
  testram(0).node := TLBuffer() := TLFragmenter(beatBytes, lineBytes) := serdes(0).clientNode.get
  testram(1).node := TLBuffer() := TLFragmenter(beatBytes, lineBytes) := serdes(1).clientNode.get

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle { val finished = Output(Bool()) })

    phyParams match {
      case params: DecoupledInternalSyncSerialPhyParams => {
        val phys = Seq.fill(2) {
          val phy = Module(new DecoupledSerialPhy(5, params))
          phy.io.outer_clock := clock
          phy.io.inner_clock := clock
          phy.io.outer_reset := reset
          phy.io.inner_reset := reset
          phy
        }
        phys(0).io.inner_ser <> serdes(0).module.io.ser
        phys(1).io.inner_ser <> serdes(1).module.io.ser
        phys(0).io.outer_ser.in <> phys(1).io.outer_ser.out
        phys(1).io.outer_ser.in <> phys(0).io.outer_ser.out
      }
      case params: DecoupledExternalSyncSerialPhyParams => {
        val phys = Seq.fill(2) {
          val phy = Module(new DecoupledSerialPhy(5, params))
          phy.io.outer_clock := clock
          phy.io.inner_clock := clock
          phy.io.outer_reset := reset
          phy.io.inner_reset := reset
          phy
        }
        phys(0).io.inner_ser <> serdes(0).module.io.ser
        phys(1).io.inner_ser <> serdes(1).module.io.ser
        phys(0).io.outer_ser.in <> phys(1).io.outer_ser.out
        phys(1).io.outer_ser.in <> phys(0).io.outer_ser.out
      }
      case params: CreditedSourceSyncSerialPhyParams => {
        val phys = Seq.fill(2) {
          val phy = Module(new CreditedSerialPhy(5, params))
          phy.io.outgoing_clock := clock
          phy.io.incoming_clock := clock
          phy.io.inner_clock := clock
          phy.io.outgoing_reset := reset
          phy.io.incoming_reset := reset
          phy.io.inner_reset := reset
          phy
        }
        phys(0).io.inner_ser <> serdes(0).module.io.ser
        phys(1).io.inner_ser <> serdes(1).module.io.ser
        phys(0).io.outer_ser.in <> phys(1).io.outer_ser.out
        phys(1).io.outer_ser.in <> phys(0).io.outer_ser.out
      }
    }
    io.finished := fuzzer.map(_.module.io.finished).andR
  }
}

class BidirectionalSerdesTestWrapper(phyParams: SerialPhyParams, timeout: Int = 4096)(implicit p: Parameters) extends UnitTest(timeout) {
  val testReset = RegInit(true.B)
  val test = Module(LazyModule(new BidirectionalSerdesTest(phyParams)).module)
  io.finished := test.io.finished
  test.reset := testReset || reset.asBool

  when (testReset && io.start) { testReset := false.B }
}

class StreamWidthAdapterTest extends UnitTest {
  val smaller = Wire(new StreamIO(16))
  val larger = Wire(new StreamIO(64))

  val data = VecInit(
    0xab13.U, 0x71ff.U, 0x6421.U, 0x9123.U,
    0xbbdd.U, 0x1542.U, 0x8912.U)

  val keep = VecInit(
    "b11".U, "b10".U, "b11".U, "b00".U,
    "b11".U, "b01".U, "b11".U)

  val (inIdx, inDone)   = Counter(smaller.in.fire,  data.size)
  val (outIdx, outDone) = Counter(smaller.out.fire, data.size)

  val started = RegInit(false.B)
  val sending = RegInit(false.B)
  val receiving = RegInit(false.B)

  smaller.out.valid := sending
  smaller.out.bits.data := data(outIdx)
  smaller.out.bits.keep := keep(outIdx)
  smaller.out.bits.last := outIdx === (data.size - 1).U
  smaller.in.ready := receiving

  StreamWidthAdapter(larger, smaller)
  larger.in <> Queue(larger.out, 2)

  when (io.start && !started) {
    started := true.B
    sending := true.B
    receiving := true.B
  }

  when (outDone)  { sending   := false.B }
  when (inDone) { receiving := false.B }

  io.finished := !sending && !receiving

  assert(!smaller.in.valid ||
    (smaller.in.bits.data === data(inIdx) &&
     smaller.in.bits.keep === keep(inIdx) &&
     smaller.in.bits.last === inDone),
    "StreamWidthAdapterTest: Data, keep, or last does not match")
}

class SwitcherDummy(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    "dummy", IdRange(0, 1))))))

  lazy val module = new LazyModuleImp(this) {
    val (tl, edge) = node.out(0)

    tl.a.valid := false.B
    tl.a.bits  := DontCare
    tl.b.ready := false.B
    tl.c.valid := false.B
    tl.c.bits  := DontCare
    tl.d.ready := false.B
    tl.e.valid := false.B
    tl.e.bits  := DontCare
  }
}

class SwitcherTest(implicit p: Parameters) extends LazyModule {
  val inIdBits = 3
  val beatBytes = 8
  val lineBytes = 64
  val inChannels = 4
  val outChannels = 2
  val outIdBits = inIdBits + log2Ceil(inChannels)
  val address = Seq(AddressSet(0x0, 0xffff))

  val fuzzers = Seq.fill(outChannels) {
    LazyModule(new TLFuzzer(
      nOperations = 32,
      inFlight = 1 << inIdBits))
  }

  val dummies = Seq.fill(outChannels) {
    Seq.fill(inChannels/outChannels-1) {
      LazyModule(new SwitcherDummy)
    }
  }

  val switcher = LazyModule(new TLSwitcher(
    inChannels, Seq(1, outChannels), Seq.fill(inChannels)(address),
    beatBytes = beatBytes, lineBytes = lineBytes, idBits = outIdBits))

  val error = LazyModule(new TLError(
    DevNullParams(address, beatBytes, lineBytes), beatBytes = beatBytes))

  val rams = Seq.fill(outChannels) {
    LazyModule(new TLTestRAM(
      address = address.head,
      beatBytes = beatBytes))
  }

  fuzzers.zip(dummies).foreach { case (fuzzer, dummy) =>
    dummy.foreach(switcher.innode := _.node)
    switcher.innode := fuzzer.node
  }

  error.node := switcher.outnodes(0)
  rams.foreach(
    _.node :=
    TLBuffer() :=
    TLFragmenter(beatBytes, lineBytes) :=
    switcher.outnodes(1))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle with UnitTestIO)

    io.finished := fuzzers.map(_.module.io.finished).reduce(_ && _)
    switcher.module.io.sel := 1.U
  }
}

class SwitchTestWrapper(implicit p: Parameters) extends UnitTest {
  val test = Module(LazyModule(new SwitcherTest).module)
  test.io.start := io.start
  io.finished := test.io.finished
}

class TLRingNetworkTest(implicit p: Parameters) extends LazyModule {
  val beatBytes = 8
  val blockBytes = p(CacheBlockBytes)

  val fuzzers = Seq.tabulate(2) { i =>
    LazyModule(new TLFuzzer(
      nOperations = 64,
      overrideAddress = Some(AddressSet(i * 0x2000, 0x1fff))))
  }
  val rams = Seq.tabulate(4) { i =>
    LazyModule(new TLTestRAM(
      address = AddressSet(i * 0x1000, 0xfff),
      beatBytes = 8))
  }
  val ring = LazyModule(new TLRingNetwork(
    inputMap = Some(Seq(1, 0)),
    outputMap = Some(Seq(0, 2, 1, 3))))

  fuzzers.foreach(ring.node := _.node)
  rams.foreach(_.node := TLFragmenter(beatBytes, blockBytes) := ring.node)

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle with UnitTestIO)

    io.finished := fuzzers.map(_.module.io.finished).reduce(_ && _)
  }
}

class TLRingNetworkTestWrapper(implicit p: Parameters) extends UnitTest {
  val test = Module(LazyModule(new TLRingNetworkTest).module)
  test.io.start := io.start
  io.finished := test.io.finished
}

class NetworkXbarTestDriver(nOut: Int, streams: Seq[(Int, Seq[Int])]) extends Module {
  val bundleType = new NetworkBundle(nOut, UInt(32.W))
  val io = IO(new Bundle with UnitTestIO {
    val out = Decoupled(bundleType)
  })

  val maxLength = streams.map(_._2.length).reduce(max(_, _))
  val streamIdx = RegInit(0.U(log2Ceil(maxLength).W))
  val (curStream, streamDone) = Counter(io.out.fire && io.out.bits.last, streams.length)

  when (io.out.fire) {
    streamIdx := Mux(io.out.bits.last, 0.U, streamIdx + 1.U)
  }

  val outs = VecInit(streams.map { case (outId, streamInit) =>
    val streamData = VecInit(streamInit.map(_.U(32.W)))
    val netData = Wire(bundleType)
    netData.netId := outId.U
    netData.payload := streamData(streamIdx)
    netData.last := streamIdx === (streamInit.length-1).U
    netData
  })

  val (s_start :: s_send :: s_done :: Nil) = Enum(3)
  val state = RegInit(s_start)

  when (state === s_start && io.start) { state := s_send }
  when (streamDone) { state := s_done }

  io.out.valid := state === s_send
  io.out.bits := outs(curStream)
  io.finished := state === s_done
}

class NetworkXbarTestChecker(nOut: Int, id: Int, streams: Seq[Seq[Int]]) extends Module {
  val bundleType = new NetworkBundle(nOut, UInt(32.W))
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(bundleType))
    val finished = Output(Bool())
  })

  val maxLength = streams.map(_.length).reduce(max(_, _))
  val streamIdx = RegInit(0.U(log2Ceil(maxLength).W))
  val (curStream, streamDone) = Counter(io.in.fire && io.in.bits.last, streams.length)

  when (io.in.fire) {
    streamIdx := Mux(io.in.bits.last, 0.U, streamIdx + 1.U)
  }

  streams.zipWithIndex.foreach { case (streamInit, i) =>
    val streamExpect = VecInit(streamInit.map(_.U(32.W)))
    val streamLast = streamIdx === (streamInit.length-1).U

    when (curStream === i.U && io.in.valid) {
      assert(io.in.bits.payload === streamExpect(streamIdx), s"Unexpected data at output ${id}")
      assert(io.in.bits.last === streamLast, s"Unexpect last at output ${id}")
    }
  }

  assert(!io.in.valid || io.in.bits.netId === id.U, s"Output ${id} got data intended for another")

  val finished = RegInit(false.B)

  when (streamDone) { finished := true.B }

  io.in.ready := !finished
  io.finished := finished
}

class NetworkXbarTest extends UnitTest {
  val nIn = 2
  val nOut = 2
  val driverStreams = Seq(
    Seq((1, Seq(0x43, 0x21, 0x55, 0x34)),
        (0, Seq(0x11, 0x53, 0x20))),
    Seq((0, Seq(0xa2, 0xb7, 0x4d, 0x18, 0xce)),
        (1, Seq(0x89, 0x9A))))
  val checkerStreams = Seq(
    Seq(driverStreams(1)(0)._2, driverStreams(0)(1)._2),
    Seq(driverStreams(0)(0)._2, driverStreams(1)(1)._2))

  val drivers = driverStreams.map(
    streams => Module(new NetworkXbarTestDriver(nOut, streams)))

  val checkers = Seq.tabulate(nOut) { id =>
    val streams = checkerStreams(id)
    Module(new NetworkXbarTestChecker(nOut, id, streams))
  }

  val xbar = Module(new NetworkXbar(nIn, nOut, UInt(32.W)))
  xbar.io.in <> drivers.map(_.io.out)
  checkers.zip(xbar.io.out).foreach { case (checker, out) =>
    checker.io.in <> out
  }

  val finished = drivers.map(_.io.finished) ++ checkers.map(_.io.finished)

  drivers.foreach(_.io.start := io.start)
  io.finished := finished.reduce(_ && _)
}

object TestChipUnitTests {
  def apply(implicit p: Parameters): Seq[UnitTest] =
    Seq(
      Module(new BlockDeviceTrackerTestWrapper),
      Module(new SerdesTestWrapper),
      Module(new BidirectionalSerdesTestWrapper(DecoupledInternalSyncSerialPhyParams(), 5000)),
      Module(new BidirectionalSerdesTestWrapper(CreditedSourceSyncSerialPhyParams(), 10000)),
      Module(new SwitchTestWrapper),
      Module(new StreamWidthAdapterTest),
      Module(new NetworkXbarTest),
      Module(new TLRingNetworkTestWrapper)
    )
}
