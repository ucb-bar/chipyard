package chipyard

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam, IO}

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
  override def vMemDataBits = 128
}

case class SpikeTileAttachParams(
  tileParams: SpikeTileParams
) extends CanAttachTile {
  type TileType = SpikeTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
  val crossingParams = RocketCrossingParams()
}

case class SpikeTileParams(
  hartId: Int = 0,
  val core: SpikeCoreParams = SpikeCoreParams(),
  icacheParams: ICacheParams = ICacheParams(nWays = 32),
  dcacheParams: DCacheParams = DCacheParams(nWays = 32),
  tcmParams: Option[MasterPortParams] = None // tightly coupled memory
) extends InstantiableTileParams[SpikeTile]
{
  val name = Some("spike_tile")
  val beuAddr = None
  val blockerCtrlAddr = None
  val btb = None
  val boundaryBuffers = false
  val dcache = Some(dcacheParams)
  val icache = Some(icacheParams)
  val clockSinkParams = ClockSinkParameters()
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): SpikeTile = {
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
  def this(params: SpikeTileParams, crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  // Required TileLink nodes
  val intOutwardNode = IntIdentityNode()
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
    Resource(cpuDevice, "reg").bind(ResourceAddress(hartId))
  }


  val icacheNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    sourceId = IdRange(0, 1),
    name = s"Core ${staticIdForMetadataUseOnly} ICache")))))

  val dcacheNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} DCache",
    sourceId      = IdRange(0, tileParams.dcache.get.nMSHRs),
    supportsProbe = TransferSizes(p(CacheBlockBytes), p(CacheBlockBytes)))))))

  val mmioNode = TLClientNode((Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} MMIO",
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
  use_dtm: Boolean) extends BlackBox(Map(
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
  val spike = Module(new SpikeBlackBox(hartId, isaDTS, tileParams.core.nPMPs,
    tileParams.icache.get.nSets, tileParams.icache.get.nWays,
    tileParams.dcache.get.nSets, tileParams.dcache.get.nWays,
    tileParams.dcache.get.nMSHRs,
    cacheable_regions, uncacheable_regions, readonly_uncacheable_regions, executable_regions,
    outer.spikeTileParams.tcmParams.map(_.base).getOrElse(0),
    outer.spikeTileParams.tcmParams.map(_.size).getOrElse(0),
    useDTM
  ))
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
}

class WithNSpikeCores(n: Int = 1, tileParams: SpikeTileParams = SpikeTileParams(),
  overrideIdOffset: Option[Int] = None) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    // Calculate the next available hart ID (since hart ID cannot be duplicated)
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    // Create TileAttachParams for every core to be instantiated
    (0 until n).map { i =>
      SpikeTileAttachParams(
        tileParams = tileParams.copy(hartId = i + idOffset)
      )
    } ++ prev
  }
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
  case BankedL2Key => up(BankedL2Key).copy(nBanks = 0)
})
