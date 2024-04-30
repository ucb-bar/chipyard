package chipyard

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam}

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{ExportDebug, DMI}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci.ClockSinkParameters

case class SpikeCoreParams() extends CoreParams {
  val useVM = true
  val useHypervisor = false
  val useSupervisor = true
  val useUser = true
  val useDebug = true
  val useAtomics = true
  val useAtomicsOnlyForIO = false
  val useCompressed = true
  override val useVector = true
  val useSCIE = false
  val useRVE = false
  val mulDiv = Some(MulDivParams())
  val fpu = Some(FPUParams())
  val nLocalInterrupts = 0
  val useNMI = false
  val nPTECacheEntries = 0
  val nPMPs = 16
  val pmpGranularity = 4
  val nBreakpoints = 0
  val useBPWatch = false
  val mcontextWidth = 0
  val scontextWidth = 0
  val nPerfCounters = 0
  val haveBasicCounters = true
  val haveFSDirty = true
  val misaWritable = true
  val haveCFlush = false
  val nL2TLBEntries = 0
  val nL2TLBWays = 0
  val mtvecInit = None
  val mtvecWritable = true
  val instBits = 16
  val lrscCycles = 1
  val decodeWidth = 1
  val fetchWidth = 1
  val retireWidth = 1
  val bootFreqHz = BigInt(1000000000)
  val rasEntries = 0
  val btbEntries = 0
  val bhtEntries = 0
  val traceHasWdata = false
  val useBitManip = false
  val useBitManipCrypto = false
  val useCryptoNIST = false
  val useCryptoSM = false
  val useConditionalZero = false

  override def vLen = 128
  override def vMemDataBits = 64 //128
}

case class SpikeTileAttachParams(
  tileParams: SpikeTileParams
) extends CanAttachTile {
  type TileType = SpikeTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
  val crossingParams = RocketCrossingParams()
}

case class SpikeTileParams(
  tileId: Int = 0,
  val core: SpikeCoreParams = SpikeCoreParams(),
  icacheParams: ICacheParams = ICacheParams(nWays = 32),
  dcacheParams: DCacheParams = DCacheParams(nWays = 32),
  tcmParams: Option[MasterPortParams] = None // tightly coupled memory
) extends InstantiableTileParams[SpikeTile]
{
  val baseName = "spike_tile"
  val uniqueName = s"${baseName}_$tileId"
  val beuAddr = None
  val blockerCtrlAddr = None
  val btb = None
  val boundaryBuffers = false
  val dcache = Some(dcacheParams)
  val icache = Some(icacheParams)
  val clockSinkParams = ClockSinkParameters()
  def instantiate(crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): SpikeTile = {
    new SpikeTile(this, crossing, lookup)
  }
}

class SpikeTile(
  val spikeTileParams: SpikeTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters) extends BaseTile(spikeTileParams, crossing, lookup, q)
    with SinksExternalInterrupts
    with SourcesExternalNotifications
{
  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(params: SpikeTileParams, crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  // Required TileLink nodes
  val intOutwardNode = None
  val masterNode = visibilityNode
  val slaveNode = TLIdentityNode()

  override def isaDTS = "rv64gcv_Zfh"

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("ucb-bar,spike", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
        cpuProperties ++
        nextLevelCacheProperty ++
        tileProperties)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(tileId))
  }


  val icacheNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    sourceId = IdRange(0, 1),
    name = s"Core ${tileId} ICache")))))

  val dcacheNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name          = s"Core ${tileId} DCache",
    sourceId      = IdRange(0, tileParams.dcache.get.nMSHRs),
    supportsProbe = TransferSizes(p(CacheBlockBytes), p(CacheBlockBytes)))))))

  val mmioNode = TLClientNode((Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name          = s"Core ${tileId} MMIO",
    sourceId      = IdRange(0, 1),
    requestFifo   = true))))))

  tlSlaveXbar.node :*= slaveNode
  val tcmNode = spikeTileParams.tcmParams.map { tcmP =>
    val device = new MemoryDevice
    val base = AddressSet.misaligned(tcmP.base, tcmP.size)
    val tcmNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
      managers = Seq(TLSlaveParameters.v1(
        address = base,
        resources = device.reg,
        regionType = RegionType.IDEMPOTENT, // not cacheable
        executable = true,
        supportsGet = TransferSizes(1, 8),
        supportsPutFull = TransferSizes(1, 8),
        supportsPutPartial = TransferSizes(1, 8),
        fifoId = Some(0)
      )),
      beatBytes = 8
    )))
    connectTLSlave(tcmNode := TLBuffer(), 8)
    tcmNode
  }

  tlOtherMastersNode := TLBuffer() := tlMasterXbar.node
  masterNode :=* tlOtherMastersNode
  tlMasterXbar.node := TLWidthWidget(64) := TLBuffer():= icacheNode
  tlMasterXbar.node := TLWidthWidget(64) := TLBuffer() := dcacheNode
  tlMasterXbar.node := TLWidthWidget(8) := TLBuffer() := mmioNode

  override lazy val module = new SpikeTileModuleImp(this)
  val rocc_sequence = p(BuildRoCC).map(_(p))
  val has_rocc = rocc_sequence.nonEmpty
  val rocc_module = if (has_rocc) rocc_sequence.head else null

  if (has_rocc) {
    val roccCSRs = rocc_sequence.map(_.roccCSRs) // the set of custom CSRs requested by all roccs
    require(roccCSRs.flatten.map(_.id).toSet.size == roccCSRs.flatten.size,
    "LazyRoCC instantiations require overlapping CSRs")
    rocc_sequence.map(_.atlNode).foreach { atl => tlMasterXbar.node :=* atl }
    rocc_sequence.map(_.tlNode).foreach { tl => tlOtherMastersNode :=* tl }
    // rocc_sequence.map(_.stlNode).foreach { stl => stl :*= tlSlaveXbar.node }

    // nPTWPorts += rocc_sequence.map(_.nPTWPorts).sum
    // nDCachePorts += rocc_sequence.size
  }
}

class SpikeBlackBox(
  hartId: Int,
  isa: String,
  pmpregions: Int,
  icache_sets: Int,
  icache_ways: Int,
  dcache_sets: Int,
  dcache_ways: Int,
  dcache_sourceids: Int,
  cacheable_regions: String,
  uncacheable_regions: String,
  readonly_uncacheable_regions: String,
  executable_regions: String,
  tcm_base: BigInt,
  tcm_size: BigInt,
  use_dtm: Boolean,
  ) extends BlackBox(Map(
    "HARTID" -> IntParam(hartId),
    "ISA" -> StringParam(isa),
    "PMPREGIONS" -> IntParam(pmpregions),
    "ICACHE_SETS" -> IntParam(icache_sets),
    "ICACHE_WAYS" -> IntParam(icache_ways),
    "DCACHE_SETS" -> IntParam(dcache_sets),
    "DCACHE_WAYS" -> IntParam(dcache_ways),
    "ICACHE_SOURCEIDS" -> IntParam(1),
    "DCACHE_SOURCEIDS" -> IntParam(dcache_sourceids),
    "UNCACHEABLE" -> StringParam(uncacheable_regions),
    "READONLY_UNCACHEABLE" -> StringParam(readonly_uncacheable_regions),
    "CACHEABLE" -> StringParam(cacheable_regions),
    "EXECUTABLE" -> StringParam(executable_regions),
    "TCM_BASE" -> IntParam(tcm_base),
    "TCM_SIZE" -> IntParam(tcm_size)
  )) with HasBlackBoxResource {

  val io = IO(new Bundle {
    val clock = Input(Bool())
    val reset = Input(Bool())
    val reset_vector = Input(UInt(64.W))
    val ipc = Input(UInt(64.W))
    val cycle = Input(UInt(64.W))
    val insns_retired = Output(UInt(64.W))
    val has_rocc = Input(Bool())

    val debug = Input(Bool())
    val mtip = Input(Bool())
    val msip = Input(Bool())
    val meip = Input(Bool())
    val seip = Input(Bool())

    val icache = new Bundle {
      val a = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val address = Output(UInt(64.W))
        val sourceid = Output(UInt(64.W))
      }
      val d = new Bundle {
        val valid = Input(Bool())
        val sourceid = Input(UInt(64.W))
        val data = Input(Vec(8, UInt(64.W)))
      }
    }

    val dcache = new Bundle {
      val a = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val address = Output(UInt(64.W))
        val sourceid = Output(UInt(64.W))
        val state_old = Output(Bool())
        val state_new = Output(Bool())
      }
      val b = new Bundle {
        val valid = Input(Bool())
        val address = Input(UInt(64.W))
        val source = Input(UInt(64.W))
        val param = Input(UInt(32.W))
      }
      val c = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val address = Output(UInt(64.W))
        val sourceid = Output(UInt(64.W))
        val param = Output(UInt(32.W))
        val voluntary = Output(Bool())
        val has_data = Output(Bool())
        val data = Output(Vec(8, UInt(64.W)))
      }
      val d = new Bundle {
        val valid = Input(Bool())
        val sourceid = Input(UInt(64.W))
        val data = Input(Vec(8, UInt(64.W)))
        val has_data = Input(Bool())
        val grantack = Input(Bool())
      }
    }

    val mmio = new Bundle {
      val a = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val address = Output(UInt(64.W))
        val data = Output(UInt(64.W))
        val store = Output(Bool())
        val size = Output(UInt(32.W))
      }
      val d = new Bundle {
        val valid = Input(Bool())
        val data = Input(UInt(64.W))
      }
    }

    val tcm = new Bundle {
      val a = new Bundle {
        val valid = Input(Bool())
        val address = Input(UInt(64.W))
        val data = Input(UInt(64.W))
        val mask = Input(UInt(32.W))
        val opcode = Input(UInt(32.W))
        val size = Input(UInt(32.W))
      }
      val d = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val data = Output(UInt(64.W))
      }
    }

    /* RoCC Interface control signals
    For the memory interface, some signals that are unnecessary from the software 
    perspective are included from the RoCC spec but tied off internally */
    val rocc = new Bundle {
      val busy = Input(Bool())
      val request = new Bundle {
        val ready = Input(Bool())
        val valid = Output(Bool())
        val insn = Output(UInt(64.W))
        val rs1 = Output(UInt(64.W))
        val rs2 = Output(UInt(64.W))
      }
      val response = new Bundle {
        val valid = Input(Bool())
        val rd = Input(UInt(64.W))
        val data = Input(UInt(64.W))
      }
      val mem_request = new Bundle {
        val valid = Input(Bool())
        val addr = Input(UInt(64.W))
        val tag = Input(UInt(10.W))
        val cmd = Input(UInt(5.W))
        val size = Input(UInt(3.W))
        val phys = Input(Bool())
        val data = Input(UInt(64.W))
        val mask = Input(UInt(8.W))
      }
      val mem_response = new Bundle {        
        val valid = Output(Bool())
        val addr = Output(UInt(64.W))
        val tag = Output(UInt(10.W))
        val cmd = Output(UInt(5.W))
        val size = Output(UInt(3.W))
        val data = Output(UInt(64.W))
        val replay = Output(Bool())
        val has_data = Output(Bool())
        val word_bypass = Output(UInt(64.W))
        val store_data = Output(UInt(64.W))
        val mask = Output(UInt(8.W))
      }
    }
  })
  addResource("/vsrc/spiketile.v")
  addResource("/csrc/spiketile.cc")
  if (use_dtm) {
    addResource("/csrc/spiketile_dtm.h")
  } else {
    addResource("/csrc/spiketile_tsi.h")
  }
}

class SpikeTileModuleImp(outer: SpikeTile) extends BaseTileModuleImp(outer) {
  val tileParams = outer.tileParams
  // We create a bundle here and decode the interrupt.
  val int_bundle = Wire(new TileInterrupts())
  outer.decodeCoreInterrupts(int_bundle)
  val managers = outer.visibilityNode.edges.out.flatMap(_.manager.managers)
  val cacheable_regions = AddressRange.fromSets(managers.filter(_.supportsAcquireB).flatMap(_.address))
    .map(a => s"${a.base} ${a.size}").mkString(" ")
  val uncacheable_regions = AddressRange.fromSets(managers.filter(!_.supportsAcquireB).flatMap(_.address))
    .map(a => s"${a.base} ${a.size}").mkString(" ")
  val readonly_uncacheable_regions = AddressRange.fromSets(managers.filter {
    m => !m.supportsAcquireB && !m.supportsPutFull && m.regionType == RegionType.UNCACHED
  }.flatMap(_.address))
    .map(a => s"${a.base} ${a.size}").mkString(" ")
  val executable_regions = AddressRange.fromSets(managers.filter(_.executable).flatMap(_.address))
    .map(a => s"${a.base} ${a.size}").mkString(" ")

  val (icache_tl, icacheEdge) = outer.icacheNode.out(0)
  val (dcache_tl, dcacheEdge) = outer.dcacheNode.out(0)
  val (mmio_tl, mmioEdge) = outer.mmioNode.out(0)

  // Note: This assumes that if the debug module exposes the ClockedDMI port,
  // then the DTM-based bringup with SimDTM will be used. This isn't required to be
  // true, but it usually is
  val useDTM = p(ExportDebug).protocols.contains(DMI)
  val spike = Module(new SpikeBlackBox(outer.tileId, outer.isaDTS, tileParams.core.nPMPs,
    tileParams.icache.get.nSets, tileParams.icache.get.nWays,
    tileParams.dcache.get.nSets, tileParams.dcache.get.nWays,
    tileParams.dcache.get.nMSHRs,
    cacheable_regions, uncacheable_regions, readonly_uncacheable_regions, executable_regions,
    outer.spikeTileParams.tcmParams.map(_.base).getOrElse(0),
    outer.spikeTileParams.tcmParams.map(_.size).getOrElse(0),
    useDTM
  ))
  spike.io.has_rocc := outer.has_rocc.asBool
  spike.io.clock := clock.asBool
  val cycle = RegInit(0.U(64.W))
  cycle := cycle + 1.U
  spike.io.reset := reset
  spike.io.cycle := cycle
  dontTouch(spike.io.insns_retired)
  val reset_vector = Wire(UInt(64.W))
  reset_vector := outer.resetVectorSinkNode.bundle
  spike.io.reset_vector := reset_vector
  spike.io.debug := int_bundle.debug
  spike.io.mtip := int_bundle.mtip
  spike.io.msip := int_bundle.msip
  spike.io.meip := int_bundle.meip
  spike.io.seip := int_bundle.seip.get
  spike.io.ipc := PlusArg("spike-ipc", width=32, default=10000)

  val blockBits = log2Ceil(p(CacheBlockBytes))
  spike.io.icache.a.ready := icache_tl.a.ready
  icache_tl.a.valid := spike.io.icache.a.valid
  icache_tl.a.bits := icacheEdge.Get(
    fromSource = spike.io.icache.a.sourceid,
    toAddress = (spike.io.icache.a.address >> blockBits) << blockBits,
    lgSize = blockBits.U)._2
  icache_tl.d.ready := true.B
  spike.io.icache.d.valid := icache_tl.d.valid
  spike.io.icache.d.sourceid := icache_tl.d.bits.source
  spike.io.icache.d.data := icache_tl.d.bits.data.asTypeOf(Vec(8, UInt(64.W)))

  spike.io.dcache.a.ready := dcache_tl.a.ready
  dcache_tl.a.valid := spike.io.dcache.a.valid
  if (dcacheEdge.manager.anySupportAcquireB) {
    dcache_tl.a.bits := dcacheEdge.AcquireBlock(
      fromSource = spike.io.dcache.a.sourceid,
      toAddress = (spike.io.dcache.a.address >> blockBits) << blockBits,
      lgSize = blockBits.U,
      growPermissions = Mux(spike.io.dcache.a.state_old, 2.U, Mux(spike.io.dcache.a.state_new, 1.U, 0.U)))._2
  } else {
    dcache_tl.a.bits := DontCare
  }
  dcache_tl.b.ready := true.B
  spike.io.dcache.b.valid := dcache_tl.b.valid
  spike.io.dcache.b.address := dcache_tl.b.bits.address
  spike.io.dcache.b.source := dcache_tl.b.bits.source
  spike.io.dcache.b.param := dcache_tl.b.bits.param

  spike.io.dcache.c.ready := dcache_tl.c.ready
  dcache_tl.c.valid := spike.io.dcache.c.valid
  if (dcacheEdge.manager.anySupportAcquireB) {
    dcache_tl.c.bits := Mux(spike.io.dcache.c.voluntary,
      dcacheEdge.Release(
        fromSource = spike.io.dcache.c.sourceid,
        toAddress = spike.io.dcache.c.address,
        lgSize = blockBits.U,
        shrinkPermissions = spike.io.dcache.c.param,
        data = spike.io.dcache.c.data.asUInt)._2,
      Mux(spike.io.dcache.c.has_data,
        dcacheEdge.ProbeAck(
          fromSource = spike.io.dcache.c.sourceid,
          toAddress = spike.io.dcache.c.address,
          lgSize = blockBits.U,
          reportPermissions = spike.io.dcache.c.param,
          data = spike.io.dcache.c.data.asUInt),
        dcacheEdge.ProbeAck(
          fromSource = spike.io.dcache.c.sourceid,
          toAddress = spike.io.dcache.c.address,
          lgSize = blockBits.U,
          reportPermissions = spike.io.dcache.c.param)
      ))
  } else {
    dcache_tl.c.bits := DontCare
  }

  val has_data = dcacheEdge.hasData(dcache_tl.d.bits)
  val should_finish = dcacheEdge.isRequest(dcache_tl.d.bits)
  val can_finish = dcache_tl.e.ready
  dcache_tl.d.ready := can_finish
  spike.io.dcache.d.valid := dcache_tl.d.valid && can_finish
  spike.io.dcache.d.has_data := has_data
  spike.io.dcache.d.grantack := dcache_tl.d.bits.opcode.isOneOf(TLMessages.Grant, TLMessages.GrantData)
  spike.io.dcache.d.sourceid := dcache_tl.d.bits.source
  spike.io.dcache.d.data := dcache_tl.d.bits.data.asTypeOf(Vec(8, UInt(64.W)))

  dcache_tl.e.valid := dcache_tl.d.valid && should_finish
  dcache_tl.e.bits := dcacheEdge.GrantAck(dcache_tl.d.bits)

  spike.io.mmio.a.ready := mmio_tl.a.ready
  mmio_tl.a.valid := spike.io.mmio.a.valid
  val log_size = (0 until 4).map { i => Mux(spike.io.mmio.a.size === (1 << i).U, i.U, 0.U) }.reduce(_|_)
  mmio_tl.a.bits := Mux(spike.io.mmio.a.store,
    mmioEdge.Put(0.U, spike.io.mmio.a.address, log_size, spike.io.mmio.a.data)._2,
    mmioEdge.Get(0.U, spike.io.mmio.a.address, log_size)._2)

  mmio_tl.d.ready := true.B
  spike.io.mmio.d.valid := mmio_tl.d.valid
  spike.io.mmio.d.data := mmio_tl.d.bits.data

  spike.io.tcm := DontCare
  spike.io.tcm.a.valid := false.B
  spike.io.tcm.d.ready := true.B
  outer.tcmNode.map { tcmNode =>
    val (tcm_tl, tcmEdge) = tcmNode.in(0)
    val debug_tcm_tl = WireInit(tcm_tl)
    dontTouch(debug_tcm_tl)
    tcm_tl.a.ready := true.B
    spike.io.tcm.a.valid := tcm_tl.a.valid
    spike.io.tcm.a.address := tcm_tl.a.bits.address
    spike.io.tcm.a.data := tcm_tl.a.bits.data
    spike.io.tcm.a.mask := tcm_tl.a.bits.mask
    spike.io.tcm.a.opcode := tcm_tl.a.bits.opcode
    spike.io.tcm.a.size := tcm_tl.a.bits.size

    spike.io.tcm.d.ready := tcm_tl.d.ready
    tcm_tl.d.bits := tcmEdge.AccessAck(RegNext(tcm_tl.a.bits))
    when (RegNext(tcm_tl.a.bits.opcode === TLMessages.Get)) {
      tcm_tl.d.bits.opcode := TLMessages.AccessAckData
    }
    tcm_tl.d.valid := spike.io.tcm.d.valid
    tcm_tl.d.bits.data := spike.io.tcm.d.data
  }

  /* Begin RoCC Section */
  if (outer.has_rocc) {
    val to_rocc_req_enq_bits = IO(new Bundle{
      val rs2 = UInt(64.W)
      val rs1 = UInt(64.W)
      val insn = UInt(64.W)
    }) //Bundle for enqueuing RoCC requests

    val to_rocc_req_q = Module(new Queue(UInt(192.W), 1, flow=true, pipe=true)) //Queue for RoCC requests
    spike.io.rocc.request.ready := to_rocc_req_q.io.enq.ready && to_rocc_req_q.io.count === 0.U // TODO: Currently only one request allowed to be in-flight
    // Attach signals coming from C++ side
    to_rocc_req_q.io.enq.valid := spike.io.rocc.request.valid
    to_rocc_req_enq_bits.insn := spike.io.rocc.request.insn
    to_rocc_req_enq_bits.rs1 := spike.io.rocc.request.rs1
    to_rocc_req_enq_bits.rs2 := spike.io.rocc.request.rs2
    to_rocc_req_q.io.enq.bits := to_rocc_req_enq_bits.asUInt

    outer.rocc_module.module.io.cmd.valid := to_rocc_req_q.io.deq.valid
    to_rocc_req_q.io.deq.ready := outer.rocc_module.module.io.cmd.ready

    // Set individual instruction fields
    val insn = Wire(new RoCCInstruction()) 
    insn.funct := to_rocc_req_q.io.deq.bits(31,25)
    insn.rs2 := to_rocc_req_q.io.deq.bits(24,20)
    insn.rs1 := to_rocc_req_q.io.deq.bits(19,15)
    insn.xd := to_rocc_req_q.io.deq.bits(14)
    insn.xs1 := to_rocc_req_q.io.deq.bits(13)
    insn.xs2 := to_rocc_req_q.io.deq.bits(12)
    insn.rd := to_rocc_req_q.io.deq.bits(11,7)
    insn.opcode := to_rocc_req_q.io.deq.bits(6,0)

    // Attach cmd signals correctly
    val cmd = Wire(new RoCCCommand())
    cmd.inst := insn
    cmd.rs1 := to_rocc_req_q.io.deq.bits(127,64)
    cmd.rs2 := to_rocc_req_q.io.deq.bits(191,128)
    cmd.status := DontCare
    outer.rocc_module.module.io.cmd.bits := cmd
    outer.rocc_module.module.io.mem.req.ready := true.B
    spike.io.rocc.busy := outer.rocc_module.module.io.busy
    spike.io.rocc.mem_request.valid := outer.rocc_module.module.io.mem.req.valid
    spike.io.rocc.mem_request.addr := outer.rocc_module.module.io.mem.req.bits.addr
    spike.io.rocc.mem_request.tag := outer.rocc_module.module.io.mem.req.bits.tag
    spike.io.rocc.mem_request.size := outer.rocc_module.module.io.mem.req.bits.size
    spike.io.rocc.mem_request.cmd := outer.rocc_module.module.io.mem.req.bits.cmd
    spike.io.rocc.mem_request.phys := outer.rocc_module.module.io.mem.req.bits.phys
    spike.io.rocc.mem_request.data := outer.rocc_module.module.io.mem.req.bits.data
    spike.io.rocc.mem_request.mask := outer.rocc_module.module.io.mem.req.bits.mask
    
    // printf(cf"Mem resp valid?: ${spike.io.rocc.mem_response.valid}\n")
    // printf(cf"Mem resp addr: ${spike.io.rocc.mem_response.addr}\n")
    // printf(cf"Mem resp tag: ${spike.io.rocc.mem_response.tag}\n")
    // printf(cf"Mem resp cmd: ${spike.io.rocc.mem_response.cmd}\n")
    // printf(cf"Mem resp size: ${spike.io.rocc.mem_response.size}\n")
    // printf(cf"Mem resp data: ${spike.io.rocc.mem_response.data}\n")
    // printf(cf"Mem resp replay: ${spike.io.rocc.mem_response.replay}\n")
    // printf(cf"Mem resp has_data: ${spike.io.rocc.mem_response.has_data}\n")
    // printf(cf"Mem resp word_bypass: ${spike.io.rocc.mem_response.word_bypass}\n")
    outer.rocc_module.module.io.mem.resp.valid := spike.io.rocc.mem_response.valid
    outer.rocc_module.module.io.mem.resp.bits.addr := spike.io.rocc.mem_response.addr
    outer.rocc_module.module.io.mem.resp.bits.tag := spike.io.rocc.mem_response.tag
    outer.rocc_module.module.io.mem.resp.bits.cmd := spike.io.rocc.mem_response.cmd
    outer.rocc_module.module.io.mem.resp.bits.size := spike.io.rocc.mem_response.size
    outer.rocc_module.module.io.mem.resp.bits.data := spike.io.rocc.mem_response.data
    outer.rocc_module.module.io.mem.resp.bits.data_raw := spike.io.rocc.mem_response.data
    outer.rocc_module.module.io.mem.resp.bits.replay := spike.io.rocc.mem_response.replay
    outer.rocc_module.module.io.mem.resp.bits.has_data := spike.io.rocc.mem_response.has_data
    outer.rocc_module.module.io.mem.resp.bits.data_word_bypass := spike.io.rocc.mem_response.word_bypass
    outer.rocc_module.module.io.mem.resp.bits.store_data := spike.io.rocc.mem_response.store_data
    outer.rocc_module.module.io.mem.resp.bits.mask := spike.io.rocc.mem_response.mask

    //Tie off unused signals, will probably be used as interface develops further.
    outer.rocc_module.module.io.ptw := DontCare
    outer.rocc_module.module.io.mem.resp.bits.signed := false.B
    outer.rocc_module.module.io.mem.resp.bits.dprv := false.B
    outer.rocc_module.module.io.mem.resp.bits.dv := false.B
    outer.rocc_module.module.io.mem.s2_nack := false.B
    outer.rocc_module.module.io.mem.s2_uncached := false.B
    outer.rocc_module.module.io.mem.s2_paddr := 0.U
    outer.rocc_module.module.io.mem.replay_next := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.ma.ld := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.ma.st := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.pf.ld := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.pf.st := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.ae.ld := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.ae.st := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.gf.ld := false.B
    outer.rocc_module.module.io.mem.s2_xcpt.gf.st := false.B
    outer.rocc_module.module.io.mem.s2_gpa := 0.U
    outer.rocc_module.module.io.mem.ordered := false.B
    outer.rocc_module.module.io.mem.perf.acquire := false.B
    outer.rocc_module.module.io.mem.perf.release := false.B
    outer.rocc_module.module.io.mem.perf.grant := false.B
    outer.rocc_module.module.io.exception := false.B
    outer.rocc_module.module.io.mem.clock_enabled := true.B
    outer.rocc_module.module.io.mem.perf.storeBufferEmptyAfterStore := false.B
    outer.rocc_module.module.io.mem.perf.storeBufferEmptyAfterLoad := false.B
    outer.rocc_module.module.io.mem.perf.canAcceptLoadThenLoad := false.B
    outer.rocc_module.module.io.mem.perf.canAcceptStoreThenLoad := false.B
    outer.rocc_module.module.io.mem.perf.canAcceptStoreThenRMW := false.B
    outer.rocc_module.module.io.mem.s2_nack_cause_raw := 0.U
    outer.rocc_module.module.io.mem.s2_gpa_is_pte := false.B
    outer.rocc_module.module.io.mem.perf.tlbMiss := false.B
    outer.rocc_module.module.io.mem.perf.blocked := false.B

    outer.rocc_module.module.io.fpu_req.ready := false.B
    outer.rocc_module.module.io.fpu_resp.valid := false.B
    outer.rocc_module.module.io.fpu_resp.bits := DontCare

    val from_rocc_result_enq_bits = IO(new Bundle {
      val rd = UInt(64.W)
      val resp = UInt(64.W)
    })

    val from_rocc_q = Module(new Queue(UInt(128.W), 1, flow=true, pipe=true)) //rd and data stitched together
    outer.rocc_module.module.io.resp.ready := from_rocc_q.io.enq.ready && from_rocc_q.io.count === 0.U
    from_rocc_q.io.enq.valid := outer.rocc_module.module.io.resp.valid

    from_rocc_result_enq_bits.rd := outer.rocc_module.module.io.resp.bits.rd
    from_rocc_result_enq_bits.resp := outer.rocc_module.module.io.resp.bits.data
    from_rocc_q.io.enq.bits := from_rocc_result_enq_bits.asUInt
    spike.io.rocc.response.valid := false.B
    from_rocc_q.io.deq.ready := true.B
    spike.io.rocc.response.rd := from_rocc_q.io.deq.bits(127,64)
    spike.io.rocc.response.data := 0.U

    when (from_rocc_q.io.deq.fire) {
      spike.io.rocc.response.valid := true.B
      spike.io.rocc.response.data := from_rocc_q.io.deq.bits(63,0)
    }
  } else {
    spike.io.rocc.request.ready := false.B
    spike.io.rocc.response.valid := false.B
    spike.io.rocc.response.data := 0.U
    spike.io.rocc.response.rd := 0.U
  }
  /* End RoCC Section */
}

class WithNSpikeCores(n: Int = 1, tileParams: SpikeTileParams = SpikeTileParams()
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    // Calculate the next available hart ID (since hart ID cannot be duplicated)
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = up(NumTiles)
    // Create TileAttachParams for every core to be instantiated
    (0 until n).map { i =>
      SpikeTileAttachParams(
        tileParams = tileParams.copy(tileId = i + idOffset)
      )
    } ++ prev
  }
  case NumTiles => up(NumTiles) + n

})

class WithSpikeTCM extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem))
    require(prev.size == 1)
    val spike = prev(0).asInstanceOf[SpikeTileAttachParams]
    Seq(spike.copy(tileParams = spike.tileParams.copy(
      tcmParams = Some(up(ExtMem).get.master)
    )))
  }
  case ExtMem => None
  case SubsystemBankedCoherenceKey => up(SubsystemBankedCoherenceKey).copy(nBanks = 0)
})

/**
 * Config fragments to enable different RoCCs
 */
class WithAdderRoCC extends Config((site, here, up) => {
  case BuildRoCC => List(
    (p: Parameters) => {
        val adder = LazyModule(new AdderExample(OpcodeSet.custom0)(p))
        adder
    }
  )
})

class WithCharCountRoCC extends Config((site, here, up) => {
  case BuildRoCC => List(
    (p: Parameters) => {
        val charCounter = LazyModule(new CharacterCountExample(OpcodeSet.all)(p))
        charCounter
    }
  )
})
