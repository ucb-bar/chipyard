package testchipip.iceblk

import chisel3._
import chisel3.experimental.{IntParam}
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.subsystem.{CacheBlockBytes, BaseSubsystem, TLBusWrapperLocation, PBUS, FBUS}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{ParameterizedBundle, DecoupledHelper, UIntIsOneOf}
import freechips.rocketchip.prci._
import scala.math.max
import testchipip.util.{ClockedIO}

case class BlockDeviceConfig(
  nTrackers: Int = 1
)

case class BlockDeviceAttachParams(
  slaveWhere: TLBusWrapperLocation = PBUS,
  masterWhere: TLBusWrapperLocation = FBUS
)

case object BlockDeviceKey extends Field[Option[BlockDeviceConfig]](None)
case object BlockDeviceAttachKey extends Field[BlockDeviceAttachParams](BlockDeviceAttachParams())

trait HasBlockDeviceParameters {
  val bdParams: BlockDeviceConfig
  def dataBytes = 512
  def sectorBits = 32
  def nTrackers = bdParams.nTrackers
  def tagBits = log2Up(nTrackers)
  def nTrackerBits = log2Up(nTrackers+1)
  def dataBitsPerBeat = 64
  def dataBeats = (dataBytes * 8) / dataBitsPerBeat
  def sectorSize = log2Ceil(sectorBits/8)
  def beatIdxBits = log2Ceil(dataBeats)
  def backendQueueDepth = max(2, nTrackers)
  def backendQueueCountBits = log2Ceil(backendQueueDepth+1)
  def pAddrBits = 64 // TODO: make this configurable
}

abstract class BlockDeviceBundle
  extends Bundle with HasBlockDeviceParameters

abstract class BlockDeviceModule
  extends Module with HasBlockDeviceParameters

class BlockDeviceRequest(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val write = Bool()
  val offset = UInt(sectorBits.W)
  val len = UInt(sectorBits.W)
  val tag = UInt(tagBits.W)
}

class BlockDeviceFrontendRequest(bdParams: BlockDeviceConfig)
    extends BlockDeviceRequest(bdParams) {
  val addr = UInt(pAddrBits.W)
}

class BlockDeviceData(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val data = UInt(dataBitsPerBeat.W)
  val tag = UInt(tagBits.W)
}

class BlockDeviceInfo(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val nsectors = UInt(sectorBits.W)
  val max_req_len = UInt(sectorBits.W)
}

class BlockDeviceIO(val bdParams: BlockDeviceConfig) extends BlockDeviceBundle {
  val req = Decoupled(new BlockDeviceRequest(bdParams))
  val data = Decoupled(new BlockDeviceData(bdParams))
  val resp = Flipped(Decoupled(new BlockDeviceData(bdParams)))
  val info = Input(new BlockDeviceInfo(bdParams))
}

class BlockDeviceArbiter(implicit p: Parameters) extends BlockDeviceModule {
  val bdParams = p(BlockDeviceKey).get
  val io = IO(new Bundle {
    val in = Flipped(Vec(nTrackers, new BlockDeviceIO(bdParams)))
    val out = new BlockDeviceIO(bdParams)
  })

  val reqArb = Module(new RRArbiter(new BlockDeviceRequest(bdParams), nTrackers))
  reqArb.io.in <> io.in.map(_.req)
  io.out.req <> reqArb.io.out
  io.out.req.bits.tag := reqArb.io.chosen

  val dataArb = Module(new RRArbiter(new BlockDeviceData(bdParams), nTrackers))
  dataArb.io.in <> io.in.map(_.data)
  io.out.data <> dataArb.io.out
  io.out.data.bits.tag := dataArb.io.chosen

  io.out.resp.ready := false.B
  io.in.zipWithIndex.foreach { case (in, i) =>
    val me = io.out.resp.bits.tag === i.U
    in.resp.valid := me && io.out.resp.valid
    in.resp.bits := io.out.resp.bits
    when (me) { io.out.resp.ready := in.resp.ready }
    in.info := io.out.info
  }
}

class BlockDeviceTracker(id: Int)(implicit p: Parameters)
    extends LazyModule with HasBlockDeviceParameters {
  val bdParams = p(BlockDeviceKey).get
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = s"blkdev-tracker$id", sourceId = IdRange(0, 1))))))

  lazy val module = new BlockDeviceTrackerModule(this)
}

class BlockDeviceTrackerIO(implicit p: Parameters) extends BlockDeviceBundle {
  val bdParams = p(BlockDeviceKey).get
  val req = Decoupled(new BlockDeviceFrontendRequest(bdParams))
  val complete = Flipped(Decoupled(Bool()))
}

class BlockDeviceTrackerModule(outer: BlockDeviceTracker)(implicit p: Parameters)
    extends LazyModuleImp(outer) with HasBlockDeviceParameters {
  val bdParams = p(BlockDeviceKey).get
  val io = IO(new Bundle {
    val front = Flipped(new BlockDeviceTrackerIO)
    val bdev = new BlockDeviceIO(bdParams)
  })

  val (tl, edge) = outer.node.out(0)
  val req = Reg(new BlockDeviceFrontendRequest(bdParams))

  require (tl.a.bits.data.getWidth == dataBitsPerBeat)
  require (edge.manager.minLatency > 0)

  val (s_idle :: s_bdev_req :: s_bdev_read_data ::
       s_bdev_write_data :: s_bdev_write_resp ::
       s_mem_write_resp :: s_mem_read_req ::
       s_complete :: Nil) = Enum(8)
  val state = RegInit(s_idle)

  val cacheBlockBytes = p(CacheBlockBytes)
  val blocksPerSector = dataBytes / cacheBlockBytes
  val beatsPerBlock = (cacheBlockBytes * 8) / dataBitsPerBeat

  val get_acq = edge.Get(
    fromSource = 0.U,
    toAddress = req.addr,
    lgSize = log2Ceil(cacheBlockBytes).U)._2
  val put_acq = edge.Put(
    fromSource = 0.U,
    toAddress = req.addr,
    lgSize = log2Ceil(cacheBlockBytes).U,
    data = io.bdev.resp.bits.data)._2

  io.front.req.ready := state === s_idle
  io.bdev.req.valid := state === s_bdev_req
  io.bdev.req.bits := req
  io.bdev.req.bits.tag := 0.U
  io.bdev.data.valid := (state === s_bdev_write_data) && tl.d.valid
  io.bdev.data.bits.data := tl.d.bits.data
  io.bdev.data.bits.tag := 0.U
  tl.d.ready := (state === s_bdev_write_data && io.bdev.data.ready) ||
                (state === s_mem_write_resp)
  io.bdev.resp.ready := (state === s_bdev_write_resp) ||
                        (state === s_bdev_read_data && tl.a.ready)
  tl.a.valid := (state === s_mem_read_req) ||
                (state === s_bdev_read_data && io.bdev.resp.valid)
  tl.a.bits := Mux(state === s_mem_read_req, get_acq, put_acq)
  io.front.complete.valid := state === s_complete
  io.front.complete.bits := DontCare

  tl.b.ready := false.B
  tl.c.valid := false.B
  tl.e.valid := false.B

  when (io.front.req.fire) {
    req := io.front.req.bits
    state := s_bdev_req
  }

  when (io.bdev.req.fire) {
    when (req.write) {
      state := s_mem_read_req
    } .otherwise {
      state := s_bdev_read_data
    }
  }

  when (tl.a.ready && state === s_mem_read_req) {
    state := s_bdev_write_data
  }

  val (read_beat, read_blk_done) = Counter(io.bdev.data.fire, beatsPerBlock)
  val (read_block, read_sector_done) = Counter(read_blk_done, blocksPerSector)

  when (read_blk_done) {
    req.addr := req.addr + cacheBlockBytes.U
    state := s_mem_read_req
  }
  when (read_sector_done) {
    req.len := req.len - 1.U
    req.offset := req.offset + 1.U
    when (req.len === 1.U) { state := s_bdev_write_resp }
  }

  when (io.bdev.resp.valid && state === s_bdev_write_resp) {
    state := s_complete
  }

  val (write_beat, write_blk_done) = Counter(
    io.bdev.resp.fire && state === s_bdev_read_data, beatsPerBlock)
  when (write_blk_done) { state := s_mem_write_resp }

  val tl_write_d_fire = tl.d.valid && state === s_mem_write_resp
  val (write_block, write_sector_done) = Counter(tl_write_d_fire, blocksPerSector)

  when (tl_write_d_fire) {
    req.addr := req.addr + cacheBlockBytes.U
    state := s_bdev_read_data
  }

  when (write_sector_done) {
    req.len := req.len - 1.U
    req.offset := req.offset + 1.U
    when (req.len === 1.U) { state := s_complete }
  }

  when (io.front.complete.fire) { state := s_idle }
}

class BlockDeviceBackendIO(implicit p: Parameters) extends BlockDeviceBundle {
  val bdParams = p(BlockDeviceKey).get
  val req = Decoupled(new BlockDeviceFrontendRequest(bdParams))
  val allocate = Flipped(Decoupled(UInt(tagBits.W)))
  val nallocate = Input(UInt(backendQueueCountBits.W))
  val complete = Flipped(Decoupled(UInt(tagBits.W)))
  val ncomplete = Input(UInt(backendQueueCountBits.W))
}

class BlockDeviceRouter(implicit p: Parameters) extends BlockDeviceModule {
  val bdParams = p(BlockDeviceKey).get
  val io = IO(new Bundle {
    val in = Flipped(new BlockDeviceBackendIO)
    val out = Vec(nTrackers, new BlockDeviceTrackerIO)
  })

  val outReadyAll = io.out.map(_.req.ready)
  val outReadyOH = PriorityEncoderOH(outReadyAll)
  val outReady = outReadyAll.reduce(_ || _)

  val allocQueue = Module(new Queue(UInt(tagBits.W), backendQueueDepth))
  io.in.allocate <> allocQueue.io.deq
  io.in.nallocate := PopCount(outReadyAll)

  val helper = DecoupledHelper(
    outReady,
    io.in.req.valid,
    allocQueue.io.enq.ready)

  io.in.req.ready := helper.fire(io.in.req.valid)
  allocQueue.io.enq.valid := helper.fire(allocQueue.io.enq.ready)
  allocQueue.io.enq.bits := OHToUInt(outReadyOH)

  io.out.zipWithIndex.foreach { case (out, i) =>
    out.req.valid := helper.fire(outReady, outReadyOH(i))
    out.req.bits := io.in.req.bits
  }

  val completeQueue = Module(new Queue(UInt(tagBits.W), backendQueueDepth))
  val completeArb = Module(new RRArbiter(Bool(), nTrackers))
  completeArb.io.in <> io.out.map(_.complete)
  completeQueue.io.enq.valid := completeArb.io.out.valid
  completeQueue.io.enq.bits := completeArb.io.chosen
  completeArb.io.out.ready := completeQueue.io.enq.ready
  io.in.complete <> completeQueue.io.deq
  io.in.ncomplete := completeQueue.io.count
}

case class BlockDeviceFrontendParams(address: BigInt, beatBytes: Int)

class BlockDeviceFrontend(val c: BlockDeviceFrontendParams)(implicit p: Parameters)
    extends RegisterRouter(RegisterRouterParams("blkdev-controller", Seq("ucb-bar,blkdev"),
      c.address, beatBytes=c.beatBytes, concurrency=1))
    with HasTLControlRegMap
    with HasInterruptSources
    with HasBlockDeviceParameters {
  override def nInterrupts = 1
  val bdParams = p(BlockDeviceKey).get
  def tlRegmap(mapping: RegField.Map*): Unit = regmap(mapping:_*)
  override lazy val module = new BlockDeviceFrontendModuleImp(this)
}

class BlockDeviceFrontendModuleImp(outer: BlockDeviceFrontend)(implicit p: Parameters) extends LazyModuleImp(outer) with HasBlockDeviceParameters {
  val io = IO(new Bundle {
    val back = new BlockDeviceBackendIO
    val info = Input(new BlockDeviceInfo(bdParams))
  })
  val params = outer.c
  val bdParams = p(BlockDeviceKey).get
  val dataBits = params.beatBytes * 8

  require (dataBits >= 64)
  require (pAddrBits <= 64)
  require (sectorBits <= 32)
  require (nTrackers < 256)

  val addr = Reg(UInt(pAddrBits.W))
  val offset = Reg(UInt(sectorBits.W))
  val len = Reg(UInt(sectorBits.W))
  val write = Reg(Bool())

  val allocRead = Wire(new RegisterReadIO(UInt(tagBits.W)))
  io.back.req.valid := allocRead.request.valid
  io.back.req.bits.addr := addr
  io.back.req.bits.offset := offset
  io.back.req.bits.len := len
  io.back.req.bits.write := write
  io.back.req.bits.tag := DontCare
  allocRead.request.ready := io.back.req.ready
  allocRead.request.bits := DontCare
  allocRead.response <> io.back.allocate

  outer.interrupts(0) := io.back.complete.valid

  outer.tlRegmap(
    0x00 -> Seq(RegField(pAddrBits, addr)),
    0x08 -> Seq(RegField(sectorBits, offset)),
    0x0C -> Seq(RegField(sectorBits, len)),
    0x10 -> Seq(RegField(1, write)),
    0x11 -> Seq(RegField.r(tagBits, allocRead)),
    0x12 -> Seq(RegField.r(backendQueueCountBits, io.back.nallocate)),
    0x13 -> Seq(RegField.r(tagBits, io.back.complete)),
    0x14 -> Seq(RegField.r(backendQueueCountBits, io.back.ncomplete)),
    0x18 -> Seq(RegField.r(sectorBits, io.info.nsectors)),
    0x1C -> Seq(RegField.r(sectorBits, io.info.max_req_len)))
}

class BlockDeviceController(address: BigInt, beatBytes: Int)(implicit p: Parameters)
    extends LazyModule with HasBlockDeviceParameters {
  val bdParams = p(BlockDeviceKey).get
  val mmio = TLIdentityNode()
  val mem = TLIdentityNode()
  val trackers = Seq.tabulate(nTrackers)(
    id => LazyModule(new BlockDeviceTracker(id)))
  val frontend = LazyModule(new BlockDeviceFrontend(
    BlockDeviceFrontendParams(address, beatBytes)))

  frontend.node := mmio
  val intnode = frontend.intXing(NoCrossing)
  trackers.foreach { tr => mem := TLWidthWidget(dataBitsPerBeat/8) := tr.node }

  lazy val module = new BlockDeviceControllerModule
  class BlockDeviceControllerModule extends LazyModuleImp(this) {
    val bdParams = p(BlockDeviceKey).get
    val io = IO(new Bundle {
      val bdev = new BlockDeviceIO(bdParams)
    })

    val router = Module(new BlockDeviceRouter)
    val arbiter = Module(new BlockDeviceArbiter)

    frontend.module.io.info := io.bdev.info
    router.io.in <> frontend.module.io.back
    trackers.map(_.module).zip(router.io.out).foreach {
      case (tracker, out) => tracker.io.front <> out
    }
    arbiter.io.in <> trackers.map(_.module.io.bdev)
    io.bdev <> arbiter.io.out
  }
}

class BlockDeviceModel(nSectors: Int, val bdParams: BlockDeviceConfig) extends BlockDeviceModule {
  val io = IO(Flipped(new BlockDeviceIO(bdParams)))

  val blocks = Mem(nSectors, Vec(dataBeats, UInt(dataBitsPerBeat.W)))
  val requests = Reg(Vec(nTrackers, new BlockDeviceRequest(bdParams)))
  val beatCounts = Reg(Vec(nTrackers, UInt(beatIdxBits.W)))
  val reqValid = RegInit(0.U(nTrackers.W))

  when (io.req.fire) {
    requests(io.req.bits.tag) := io.req.bits
    beatCounts(io.req.bits.tag) := 0.U
  }

  val dataReq = requests(io.data.bits.tag)
  val dataBeat = beatCounts(io.data.bits.tag)
  when (io.data.fire) {
    blocks(dataReq.offset).apply(dataBeat) := io.data.bits.data
    when (dataBeat === (dataBeats-1).U) {
      requests(io.data.bits.tag).offset := dataReq.offset + 1.U
      requests(io.data.bits.tag).len := dataReq.len - 1.U
      beatCounts(io.data.bits.tag) := 0.U
    } .otherwise {
      beatCounts(io.data.bits.tag) := dataBeat + 1.U
    }
  }

  val respReq = requests(io.resp.bits.tag)
  val respBeat = beatCounts(io.resp.bits.tag)
  when (io.resp.fire && !respReq.write) {
    when (respBeat === (dataBeats-1).U) {
      requests(io.resp.bits.tag).offset := respReq.offset + 1.U
      requests(io.resp.bits.tag).len := respReq.len - 1.U
      beatCounts(io.resp.bits.tag) := 0.U
    } .otherwise {
      beatCounts(io.resp.bits.tag) := respBeat + 1.U
    }
  }

  val respValid = reqValid & Cat(
    requests.reverse.map(req => !req.write || (req.len === 0.U)))
  val respValidOH = PriorityEncoderOH(respValid)
  val respFinished = io.resp.fire && (respReq.write ||
    (respBeat === (dataBeats-1).U && respReq.len === 1.U))

  reqValid := (reqValid |
    Mux(io.req.fire, UIntToOH(io.req.bits.tag), 0.U)) &
    ~Mux(respFinished, respValidOH, 0.U)

  io.req.ready := !reqValid.andR
  io.data.ready := (reqValid >> io.data.bits.tag)(0) && dataReq.write
  io.resp.valid := respValid.orR
  io.resp.bits.tag := OHToUInt(respValidOH)
  io.resp.bits.data := blocks(respReq.offset).apply(respBeat)
  io.info.nsectors := nSectors.U
  io.info.max_req_len := ~0.U(sectorBits.W)
}

object SimBlockDeviceParamMap {
  def apply(config: BlockDeviceConfig) = {
    Map("TAG_BITS" -> IntParam(log2Up(config.nTrackers)))
  }
}

class SimBlockDevice(config: BlockDeviceConfig)
  extends BlackBox(SimBlockDeviceParamMap(config)) with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val bdev = Flipped(new BlockDeviceIO(config))
  })

  addResource("/testchipip/vsrc/SimBlockDevice.v")
  addResource("/testchipip/csrc/SimBlockDevice.cc")
  addResource("/testchipip/csrc/blkdev.cc")
  addResource("/testchipip/csrc/blkdev.h")
}

trait CanHavePeripheryBlockDevice { this: BaseSubsystem =>
  private val portName = "blkdev-controller"

  val bdev = p(BlockDeviceKey).map { params =>
    val manager = locateTLBusWrapper(p(BlockDeviceAttachKey).slaveWhere) // The bus for which the controller acts as a manager
    val client = locateTLBusWrapper(p(BlockDeviceAttachKey).masterWhere) // The bus for which the controller acts as a client
    // TODO: currently the controller is in the clock domain of the bus which masters it
    // we assume this is same as the clock domain of the bus the controller masters
    val domain = manager.generateSynchronousDomain.suggestName("block_device_domain")

    val controller = domain { LazyModule(new BlockDeviceController(
      0x10015000, manager.beatBytes))
    }

    manager.coupleTo(portName) { controller.mmio := TLFragmenter(manager.beatBytes, manager.blockBytes) := _ }
    client.coupleFrom(portName) { _ := controller.mem }
    ibus.fromSync := controller.intnode


    val inner_io = domain { InModuleBody {
      val inner_io = IO(new BlockDeviceIO(params)).suggestName("bdev")
      inner_io <> controller.module.io.bdev
      inner_io
    } }

    val outer_io = InModuleBody {
      val outer_io = IO(new ClockedIO(new BlockDeviceIO(params))).suggestName("bdev")
      outer_io.bits <> inner_io
      outer_io.clock := domain.module.clock
      outer_io
    }
    outer_io
  }
}

