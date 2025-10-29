package shuttle.common

import chisel3._
import chisel3.util._

import scala.collection.mutable.{ListBuffer}

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._
import shuttle.dmem.{ShuttleSGTCMParams, SGTCM}
import shuttle.ifu._
import shuttle.exu._
import shuttle.dmem._
import freechips.rocketchip.trace.{TraceEncoderParams, TraceEncoderController, TraceSinkArbiter}

trait TCMParams {
  def base: BigInt
  def size: BigInt
  def addressSet = AddressSet(base, size-1)
}

case class ShuttleTCMParams(
  base: BigInt,
  size: BigInt,
  banks: Int) extends TCMParams

case class ShuttleTileParams(
  core: ShuttleCoreParams = ShuttleCoreParams(),
  icache: Option[ICacheParams] = Some(ICacheParams(prefetch=true)),
  dcacheParams: ShuttleDCacheParams = ShuttleDCacheParams(),
  trace: Boolean = false,
  name: Option[String] = Some("shuttle_tile"),
  btb: Option[BTBParams] = Some(BTBParams()),
  tcm: Option[ShuttleTCMParams] = None,
  sgtcm: Option[ShuttleSGTCMParams] = None,
  tileId: Int = 0,
  tileBeatBytes: Int = 8,
  boundaryBuffers: Boolean = false,
  traceParams: Option[TraceEncoderParams] = None
  ) extends InstantiableTileParams[ShuttleTile]
{
  require(icache.isDefined)
  def instantiate(crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): ShuttleTile = {
    new ShuttleTile(this, crossing, lookup)
  }

  val beuAddr: Option[BigInt] = None
  val blockerCtrlAddr: Option[BigInt] = None
  val dcache = Some(DCacheParams(rowBits=64, nSets=dcacheParams.nSets, nWays=dcacheParams.nWays, nMSHRs=dcacheParams.nMSHRs, nMMIOs=dcacheParams.nMMIOs))
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  val baseName = name.getOrElse("shuttle_tile")
  val uniqueName = s"${baseName}_$tileId"
}

case class ShuttleCrossingParams(
  crossingType: ClockCrossingType = SynchronousCrossing(),
  master: HierarchicalElementPortParamsLike = HierarchicalElementMasterPortParams(),
  slave: HierarchicalElementSlavePortParams = HierarchicalElementSlavePortParams(where=SBUS),
  mmioBaseAddressPrefixWhere: TLBusWrapperLocation = SBUS,
  resetCrossingType: ResetCrossingType = NoResetCrossing(),
  forceSeparateClockReset: Boolean = false
) extends HierarchicalElementCrossingParamsLike

case class ShuttleTileAttachParams(
  tileParams: ShuttleTileParams,
  crossingParams: ShuttleCrossingParams
) extends CanAttachTile {
  type TileType = ShuttleTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
}

class ShuttleTile private(
  val shuttleParams: ShuttleTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters)
    extends BaseTile(shuttleParams, crossing, lookup, q)
    with HasTileParameters
    with SinksExternalInterrupts
    with SourcesExternalNotifications
{
  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(params: ShuttleTileParams, crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  val intOutwardNode = None
  val masterNode = visibilityNode
  val slaveNode = TLIdentityNode()

  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("ucb-bar,shuttle", "riscv")) {
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

  // Put this on the edges between masters on this tile, and the masterxbar
  def tcmAdjusterNode(params: Option[TCMParams]): TLNode = params.map { tcmParams =>
    val replicationSize = (1 << log2Ceil(p(NumTiles))) * tcmParams.size
    val tcm_adjuster = LazyModule(new AddressOffsetter(tcmParams.size-1, replicationSize))
    InModuleBody { tcm_adjuster.module.io.base := tcmParams.base.U + tcmParams.size.U * hartIdSinkNode.bundle }
    tcm_adjuster.node
  } .getOrElse { TLEphemeralNode() }

  // Put this on the edge between the slave xbar and the tile's slave port
  def tcmSlaveReplicator(params: Option[TCMParams]): TLNode = params.map { tcmParams =>
    val replicationSize = (1 << log2Ceil(p(NumTiles))) * tcmParams.size
    val tcm_slave_replicator = LazyModule(new RegionReplicator(ReplicatedRegion(
      tcmParams.addressSet,
      tcmParams.addressSet.widen(replicationSize - tcmParams.size)
    )))
    val prefix_slave_source = BundleBridgeSource[UInt](() => UInt(1.W))
    tcm_slave_replicator.prefix := prefix_slave_source
    InModuleBody { prefix_slave_source.bundle := 0.U }
    tcm_slave_replicator.node
  } .getOrElse { TLEphemeralNode() }

  // Put this on the edge between the slave xbar and the master xbar
  def tcmMasterReplicator(params: Option[TCMParams]): TLNode = params.map { tcmParams =>
    val replicationSize = (1 << log2Ceil(p(NumTiles))) * tcmParams.size
    val tcm_master_replicator = LazyModule(new RegionReplicator(ReplicatedRegion(
      tcmParams.addressSet,
      tcmParams.addressSet.widen((replicationSize << 1) - tcmParams.size)
    )))
    val prefix_master_source = BundleBridgeSource[UInt](() => UInt(1.W))
    tcm_master_replicator.prefix := prefix_master_source
    InModuleBody { prefix_master_source.bundle := 0.U }
    tcm_master_replicator.node := TLFilter(TLFilter.mSubtract(AddressSet(tcmParams.base, replicationSize-1)))
  } .getOrElse { TLEphemeralNode() }



  val roccs = p(BuildRoCC).map(_(p))

  roccs.map(_.atlNode).foreach { atl => tlMasterXbar.node :=* tcmAdjusterNode(shuttleParams.tcm) :=* tcmAdjusterNode(shuttleParams.sgtcm) :=* atl }
  roccs.map(_.tlNode).foreach { tl => tlOtherMastersNode :=* tl }
  val roccCSRs = roccs.map(_.roccCSRs)
  require(roccCSRs.flatten.map(_.id).toSet.size == roccCSRs.flatten.size,
    "LazyRoCC instantiations require overlapping CSRs")

  val frontend = LazyModule(new ShuttleFrontend(tileParams.icache.get, tileId))
  (tlMasterXbar.node
    := TLBuffer()
    := tcmAdjusterNode(shuttleParams.tcm)
    := tcmAdjusterNode(shuttleParams.sgtcm)
    := TLWidthWidget(tileParams.icache.get.fetchBytes)
    := frontend.masterNode)
  frontend.resetVectorSinkNode := resetVectorNexusNode

  val nPTWPorts = 2 + roccs.map(_.nPTWPorts).sum
  val dcache = LazyModule(new ShuttleDCache(tileId, ShuttleDCacheParams())(p))
  (tlMasterXbar.node
    := TLBuffer()
    := tcmAdjusterNode(shuttleParams.tcm)
    := tcmAdjusterNode(shuttleParams.sgtcm)
    := TLWidthWidget(tileParams.dcache.get.rowBits/8)
    := dcache.node)

  val vector_unit = shuttleParams.core.vector.map(v => LazyModule(v.build(p)))
  vector_unit.foreach { vu => (tlMasterXbar.node
    :=* TLBuffer()
    :=* tcmAdjusterNode(shuttleParams.tcm)
    :=* tcmAdjusterNode(shuttleParams.sgtcm)
    :=* vu.atlNode)
  }
  vector_unit.foreach(vu => tlOtherMastersNode :=* vu.tlNode)

  val tcmSlaveXbar = ((shuttleParams.tcm.isDefined || shuttleParams.sgtcm.isDefined)).option(TLXbar())

  shuttleParams.tcm.foreach { tcmParams => DisableMonitors { implicit p =>
    val device = new MemoryDevice
    for (b <- 0 until tcmParams.banks) {
      val base = tcmParams.base + b * p(CacheBlockBytes)
      val mask = tcmParams.size - 1 - (tcmParams.banks - 1) * p(CacheBlockBytes)
      val tcm = LazyModule(new TLRAM(
        address = AddressSet(base, mask),
        beatBytes = shuttleParams.tileBeatBytes,
        atomics = true,
        devOverride = Some(device),
        devName = Some(s"Core $tileId TCM bank $b")
      ))
      tcm.node := TLFragmenter(shuttleParams.tileBeatBytes, p(CacheBlockBytes)) := TLBuffer() := tcmSlaveXbar.get
    }
  }}

  val sgtcmXbar = LazyModule(new TLXbar())
  shuttleParams.sgtcm.foreach { sgtcmParams => DisableMonitors { implicit p =>
    val device = new MemoryDevice
    val base = sgtcmParams.base
    val mask = sgtcmParams.size - 1
    val sgtcm = LazyModule(new SGTCM(
      address = AddressSet(base, mask),
      beatBytes = sgtcmParams.banks,
      devOverride = Some(device),
      devName = Some(s"Core $tileId SGTCM")
    ))
    sgtcm.node := TLWidthWidget(shuttleParams.tileBeatBytes) := tcmSlaveXbar.get
    sgtcm.sgnode :*= sgtcmXbar.node
    vector_unit.foreach { vu => sgtcmXbar.node :=* vu.sgNode.get }
  }}

  val trace_encoder_controller = shuttleParams.traceParams.map { t =>
    val trace_encoder_controller = LazyModule(new TraceEncoderController(t.encoderBaseAddr, shuttleParams.tileBeatBytes))
    connectTLSlave(trace_encoder_controller.node, shuttleParams.tileBeatBytes)
    trace_encoder_controller
  }

  val trace_encoder = shuttleParams.traceParams match {
    case Some(t) => Some(t.buildEncoder(p))
    case None => None
  }

  val (trace_sinks, traceSinkIds) = shuttleParams.traceParams match {
    case Some(t) => t.buildSinks.map {_(p)}.unzip
    case None => (Nil, Nil)
  }

  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  tcmSlaveXbar.map { tcmSlaveXbar =>
    // Connect to slavebar to the slaveport into the tile
    (tcmSlaveXbar
      := tcmSlaveReplicator(shuttleParams.tcm)
      := tcmSlaveReplicator(shuttleParams.sgtcm)
      := TLFilter({m =>
        // Expose only the part of the TCM's address space which corresponds to our tile
        val tcmMatch: Option[TLSlaveParameters]   = if (m.name.contains(" TCM"))  shuttleParams.tcm  .map(t =>
          m.v1copy(address=m.address.map(_.intersect(AddressSet(t.base + tileId * t.size, t.size-1))).flatten)
        ) else None
        // Expose only the part of the SGTCM's address space which corresponds to our tile
        val sgtcmMatch: Option[TLSlaveParameters] = if (m.name.contains("SGTCM")) shuttleParams.sgtcm.map(t =>
          m.v1copy(address=m.address.map(_.intersect(AddressSet(t.base + tileId * t.size, t.size-1))).flatten)
        ) else None
        Some(tcmMatch.getOrElse(sgtcmMatch.getOrElse(m)))
      })
      := tlSlaveXbar.node)

    // Connect the slavebar to the master bar
    (tcmSlaveXbar
      := tcmMasterReplicator(shuttleParams.tcm)
      := tcmMasterReplicator(shuttleParams.sgtcm)
      := tlMasterXbar.node)
  }


  DisableMonitors { implicit p => (tlOtherMastersNode
    := TLBuffer()
    := TLWidthWidget(shuttleParams.tileBeatBytes)
    := tlMasterXbar.node)
  }
  masterNode :=* tlOtherMastersNode

  override lazy val module = new ShuttleTileModuleImp(this)

  override def makeMasterBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) = TLBuffer(
    if (shuttleParams.boundaryBuffers) BufferParams.default else BufferParams.none
  )

  override def makeSlaveBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) = TLBuffer(
    if (shuttleParams.boundaryBuffers) BufferParams.default else BufferParams.none
  )
}

class ShuttleTileModuleImp(outer: ShuttleTile) extends BaseTileModuleImp(outer)
{
  val core = Module(new ShuttleCore(outer, outer.dcache.module.edge)(outer.p))
  outer.vector_unit.foreach { v =>
    core.io.vector.get <> v.module.io
    val sgtcmParams = outer.shuttleParams.sgtcm
    v.module.io_sg_base := sgtcmParams.map { sgtcm =>
      sgtcm.base.U + outer.hartIdSinkNode.bundle * sgtcm.size.U
    }.getOrElse(0.U)
  }

  val dcachePorts = Wire(Vec(2, new ShuttleDCacheIO))
  val ptwPorts = Wire(Vec(outer.nPTWPorts, new TLBPTWIO))
  val edge = outer.dcache.node.edges.out(0)
  ptwPorts(0) <> core.io.ptw_tlb
  ptwPorts(1) <> outer.frontend.module.io.ptw

  val ptw = Module(new PTW(outer.nPTWPorts)(edge, outer.p))

  if (outer.usingPTW) {
    dcachePorts(0).req.valid := ptw.io.mem.req.valid
    dcachePorts(0).req.bits.addr := ptw.io.mem.req.bits.addr
    dcachePorts(0).req.bits.tag := ptw.io.mem.req.bits.tag
    dcachePorts(0).req.bits.cmd := ptw.io.mem.req.bits.cmd
    dcachePorts(0).req.bits.size := ptw.io.mem.req.bits.size
    dcachePorts(0).req.bits.signed := false.B
    dcachePorts(0).req.bits.data := 0.U
    dcachePorts(0).req.bits.mask := 0.U
    ptw.io.mem.req.ready := dcachePorts(0).req.ready

    dcachePorts(0).s1_paddr := RegEnable(ptw.io.mem.req.bits.addr, ptw.io.mem.req.valid)
    dcachePorts(0).s1_kill := ptw.io.mem.s1_kill
    dcachePorts(0).s1_data := ptw.io.mem.s1_data
    ptw.io.mem.s2_nack := dcachePorts(0).s2_nack
    dcachePorts(0).s2_kill := ptw.io.mem.s2_kill

    ptw.io.mem.resp.valid := dcachePorts(0).resp.valid
    ptw.io.mem.resp.bits := DontCare
    ptw.io.mem.resp.bits.has_data := true.B
    ptw.io.mem.resp.bits.tag := dcachePorts(0).resp.bits.tag
    ptw.io.mem.resp.bits.data := dcachePorts(0).resp.bits.data
    ptw.io.mem.resp.bits.size := dcachePorts(0).resp.bits.size
    ptw.io.mem.ordered := dcachePorts(0).ordered
    dcachePorts(0).keep_clock_enabled := ptw.io.mem.keep_clock_enabled
    ptw.io.mem.clock_enabled := dcachePorts(0).clock_enabled
    ptw.io.mem.perf := dcachePorts(0).perf
    ptw.io.mem.s2_nack_cause_raw := false.B
    ptw.io.mem.s2_uncached := false.B
    ptw.io.mem.replay_next := false.B
    ptw.io.mem.s2_gpa := false.B
    ptw.io.mem.s2_gpa_is_pte := false.B
    ptw.io.mem.store_pending := false.B

    val ptw_s2_addr = Pipe(ptw.io.mem.req.fire, ptw.io.mem.req.bits.addr, 2).bits
    val ptw_s2_legal = edge.manager.findSafe(ptw_s2_addr).reduce(_||_)
    ptw.io.mem.s2_paddr := ptw_s2_addr
    ptw.io.mem.s2_xcpt.ae.ld := !(ptw_s2_legal &&
      edge.manager.fastProperty(ptw_s2_addr, p => TransferSizes.asBool(p.supportsGet), (b: Boolean) => b.B))
    ptw.io.mem.s2_xcpt.ae.st := false.B
    ptw.io.mem.s2_xcpt.pf.ld := false.B
    ptw.io.mem.s2_xcpt.pf.st := false.B
    ptw.io.mem.s2_xcpt.gf.ld := false.B
    ptw.io.mem.s2_xcpt.gf.st := false.B
    ptw.io.mem.s2_xcpt.ma.ld := false.B
    ptw.io.mem.s2_xcpt.ma.st := false.B
  }

  outer.decodeCoreInterrupts(core.io.interrupts) // Decode the interrupt vector

  // Pass through various external constants and reports that were bundle-bridged into the tile
  outer.traceSourceNode.bundle <> core.io.trace
  core.io.hartid := outer.hartIdSinkNode.bundle
  require(core.io.hartid.getWidth >= outer.hartIdSinkNode.bundle.getWidth,
    s"core hartid wire (${core.io.hartid.getWidth}) truncates external hartid wire (${outer.hartIdSinkNode.bundle.getWidth}b)")

  // Connect the core pipeline to other intra-tile modules
  outer.frontend.module.io.cpu <> core.io.imem
  dcachePorts(1) <> core.io.dmem // TODO outer.dcachePorts += () => module.core.io.dmem ??

  // Connect the coprocessor interfaces
  core.io.rocc := DontCare
  if (outer.roccs.size > 0) {
    val (respArb, cmdRouter) = {
      val respArb = Module(new RRArbiter(new RoCCResponse()(outer.p), outer.roccs.size))
      val cmdRouter = Module(new RoccCommandRouter(outer.roccs.map(_.opcodes))(outer.p))
      var ptwPortId = 2
      outer.roccs.zipWithIndex.foreach { case (rocc, i) =>
        for (j <- 0 until rocc.nPTWPorts) {
          ptwPorts(ptwPortId) <> rocc.module.io.ptw(j)
          ptwPortId += 1
        }
        rocc.module.io.cmd <> cmdRouter.io.out(i)
        rocc.module.io.mem := DontCare
        rocc.module.io.mem.req.ready := false.B
        assert(!rocc.module.io.mem.req.valid)
        respArb.io.in(i) <> Queue(rocc.module.io.resp)
        rocc.module.io.fpu_req.ready := false.B
        rocc.module.io.fpu_resp.valid := false.B
        rocc.module.io.fpu_resp.bits := DontCare
      }
      val nFPUPorts = outer.roccs.count(_.usesFPU)
      if (nFPUPorts > 0) {
        val fpu = Module(new FPU(outer.tileParams.core.fpu.get)(outer.p))
        fpu.io := DontCare
        fpu.io.fcsr_rm := core.io.fcsr_rm
        fpu.io.ll_resp_val := false.B
        fpu.io.valid := false.B
        fpu.io.killx := false.B
        fpu.io.killm := false.B

        val fpArb = Module(new InOrderArbiter(new FPInput()(outer.p), new FPResult()(outer.p), nFPUPorts))
        val fp_rocc_ios = outer.roccs.filter(_.usesFPU).map(_.module.io)
        fpArb.io.in_req <> fp_rocc_ios.map(_.fpu_req)
        fp_rocc_ios.zip(fpArb.io.in_resp).foreach {
          case (rocc, arb) => rocc.fpu_resp <> arb
        }
        fpu.io.cp_req <> fpArb.io.out_req
        fpArb.io.out_resp <> fpu.io.cp_resp
      }
      (respArb, cmdRouter)
    }

    cmdRouter.io.in <> core.io.rocc.cmd
    outer.roccs.foreach(_.module.io.exception := core.io.rocc.exception)
    core.io.rocc.resp <> respArb.io.out
    core.io.rocc.busy <> (cmdRouter.io.busy || outer.roccs.map(_.module.io.busy).reduce(_ || _))
    core.io.rocc.interrupt := outer.roccs.map(_.module.io.interrupt).reduce(_ || _)
    val roccCSRIOs = outer.roccs.map(_.module.io.csrs)
    (core.io.rocc.csrs zip roccCSRIOs.flatten).foreach { t => t._2 := t._1 }
  }


  val dcacheArb = Module(new ShuttleDCacheArbiter(2)(outer.p))
  outer.dcache.module.io <> dcacheArb.io.mem

  core.io.ptw <> ptw.io.dpath

  dcacheArb.io.requestor <> dcachePorts
  ptw.io.requestor <> ptwPorts

  if (outer.shuttleParams.traceParams.isDefined) {
    core.io.trace_core_ingress.get <> outer.trace_encoder.get.module.io.in
    outer.trace_encoder_controller.foreach { lm =>
      outer.trace_encoder.get.module.io.control <> lm.module.io.control
    }

    val trace_sink_arbiter = Module(new TraceSinkArbiter(outer.traceSinkIds, 
      use_monitor = outer.shuttleParams.traceParams.get.useArbiterMonitor, 
      monitor_name = outer.shuttleParams.uniqueName))

    trace_sink_arbiter.io.target := outer.trace_encoder.get.module.io.control.target
    trace_sink_arbiter.io.in <> outer.trace_encoder.get.module.io.out 

    core.io.traceStall := outer.trace_encoder.get.module.io.stall

    outer.trace_sinks.zip(outer.traceSinkIds).foreach { case (sink, id) =>
      val index = outer.traceSinkIds.indexOf(id)
      sink.module.io.trace_in <> trace_sink_arbiter.io.out(index)
    }
  } else {
    core.io.traceStall := outer.traceAuxSinkNode.bundle.stall
  }
}
