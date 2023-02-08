package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem._

import boom.common.{BoomTile}

case class ReRoCCTileParams(
  genRoCC: Option[Parameters => LazyRoCC] = None,
  reroccId: Int = 0,
  ibufEntries: Int = 4,
  rowBits: Int = 64,
  dcacheParams: Option[DCacheParams] = Some(DCacheParams(nSets = 4, nWays = 4)),
  mergeTLNodes: Boolean = false,
  l2TLBEntries: Int = 0,
  l2TLBWays: Int = 4
) extends TileParams {
  val core = new EmptyCoreParams(l2TLBEntries, l2TLBWays)
  val icache = None
  val dcache = Some(dcacheParams.getOrElse(DCacheParams()).copy(rowBits=rowBits))
  val btb = None
  val hartId = -1
  val beuAddr = None
  val blockerCtrlAddr = None
  val name = None
  val clockSinkParams = ClockSinkParameters()

}

case object ReRoCCTileKey extends Field[Seq[ReRoCCTileParams]](Nil)

class EmptyCoreParams(val nL2TLBEntries: Int, val nL2TLBWays: Int) extends CoreParams {
  // Most fields are unused, or make no sense in the context of a ReRoCC tile
  lazy val bootFreqHz: BigInt               = ???
  lazy val useVM: Boolean                   = true
  lazy val useUser: Boolean                 = ???
  lazy val useSupervisor: Boolean           = ???
  lazy val useHypervisor: Boolean           = false
  lazy val useDebug: Boolean                = ???
  lazy val useAtomics: Boolean              = false
  lazy val useAtomicsOnlyForIO: Boolean     = false
  lazy val useCompressed: Boolean           = true
  lazy val useRVE: Boolean                  = ???
  lazy val useSCIE: Boolean                 = false
  lazy val nLocalInterrupts: Int            = ???
  lazy val useNMI: Boolean                  = false
  lazy val nBreakpoints: Int                = 0
  lazy val useBPWatch: Boolean              = ???
  lazy val mcontextWidth: Int               = ???
  lazy val scontextWidth: Int               = ???
  lazy val nPMPs: Int                       = 0
  lazy val nPerfCounters: Int               = 0
  lazy val haveBasicCounters: Boolean       = ???
  lazy val haveCFlush: Boolean              = false;
  lazy val misaWritable: Boolean            = ???
  lazy val nPTECacheEntries: Int            = 0
  lazy val mtvecInit: Option[BigInt]        = None
  lazy val mtvecWritable: Boolean           = false
  lazy val fastLoadWord: Boolean            = ???
  lazy val fastLoadByte: Boolean            = ???
  lazy val branchPredictionModeCSR: Boolean = ???
  lazy val clockGate: Boolean               = ???
  lazy val mvendorid: Int                   = ???
  lazy val mimpid: Int                      = ???
  lazy val mulDiv: Option[MulDivParams]     = None
  lazy val fpu: Option[FPUParams]           = Some(FPUParams())
  lazy val traceHasWdata: Boolean           = false
  lazy val useBitManip: Boolean             = false
  lazy val useBitManipCrypto: Boolean       = false
  lazy val useCryptoNIST: Boolean           = false
  lazy val useCryptoSM: Boolean             = false

  lazy val decodeWidth: Int                 = 0
  lazy val fetchWidth: Int                  = 0
  lazy val haveFSDirty: Boolean             = ???
  lazy val instBits: Int                    = 16
  lazy val lrscCycles: Int                  = 20
  lazy val pmpGranularity: Int              = 0
  lazy val retireWidth: Int                 = 0
}



// For local PTW
class MiniDCache(reRoCCId: Int, crossing: ClockCrossingType)(implicit p: Parameters) extends DCache(0, crossing)(p) {
  override def cacheClientParameters = Seq(TLMasterParameters.v1(
    name          = s"ReRoCC ${reRoCCId} DCache",
    sourceId      = IdRange(0, 1),
    supportsProbe = TransferSizes(cfg.blockBytes, cfg.blockBytes)))
  override def mmioClientParameters = Seq(TLMasterParameters.v1(
    name          = s"ReRoCC ${reRoCCId} DCache MMIO",
    sourceId      = IdRange(firstMMIO, firstMMIO + cfg.nMMIOs),
    requestFifo   = true))
}

class ReRoCCManager(reRoCCTileParams: ReRoCCTileParams, roccOpcode: UInt)(implicit p: Parameters) extends LazyModule {
  val node = ReRoCCManagerNode(ReRoCCManagerParams(reRoCCTileParams.reroccId, reRoCCTileParams.ibufEntries))
  val ibufEntries = reRoCCTileParams.ibufEntries
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val manager_id = Input(UInt(64.W))
      val cmd = Decoupled(new RoCCCommand)
      val resp = Flipped(Decoupled(new RoCCResponse))
      val busy = Input(Bool())
      val ptw = Flipped(new DatapathPTWIO)
    })
    dontTouch(io.manager_id)
    val (rerocc, edge) = node.in(0)
    dontTouch(rerocc)

    val s_idle :: s_active :: s_rel_wait :: s_sfence :: s_unbusy :: Nil = Enum(5)

    val numClients = edge.cParams.clients.size

    val client = Reg(UInt(log2Ceil(numClients).W))
    dontTouch(client)
    val status = Reg(new MStatus)
    val ptbr = Reg(new PTBR)
    val state = RegInit(s_idle)

    io.ptw.ptbr := ptbr
    io.ptw.hgatp := 0.U.asTypeOf(new PTBR)
    io.ptw.vsatp := 0.U.asTypeOf(new PTBR)
    io.ptw.sfence.valid := state === s_sfence
    io.ptw.sfence.bits.rs1 := false.B
    io.ptw.sfence.bits.rs2 := false.B
    io.ptw.sfence.bits.addr := 0.U
    io.ptw.sfence.bits.asid := 0.U
    io.ptw.sfence.bits.hv := false.B
    io.ptw.sfence.bits.hg := false.B

    io.ptw.status := status
    io.ptw.hstatus := 0.U.asTypeOf(new HStatus)
    io.ptw.gstatus := 0.U.asTypeOf(new MStatus)
    io.ptw.pmp.foreach(_ := 0.U.asTypeOf(new PMP))

    val rr_req = Queue(rerocc.req)
    val rr_resp = rerocc.resp

    rr_req.ready := false.B

    val ibufSz = log2Ceil(ibufEntries)
    val inst_buf = Reg(Vec(ibufEntries, new RoCCCommand))
    val inst_val = RegInit(VecInit.fill(ibufEntries) { false.B })
    val inst_head = RegInit(0.U(ibufSz.W))
    val inst_q = Module(new Queue(new RoCCCommand, 2))
    io.cmd <> inst_q.io.deq
    val enq_inst = Reg(new RoCCCommand)
    val enq_inst_tag = Reg(UInt(ibufSz.W))
    val enq_inst_new_mstatus = Reg(Bool())

    val beat = RegInit(0.U(8.W))
    when (rr_req.fire()) { beat := Mux(rr_req.bits.last, 0.U, beat + 1.U) }

    // 0 -> acquire ack
    // 1 -> inst ack
    // 2 -> writeback
    // 4 -> rel
    // 5 -> unbusyack
    val resp_arb = Module(new HellaPeekingArbiter(new ReRoCCMsgBundle(edge.bundle), 5,
      (b: ReRoCCMsgBundle) => b.last,
      Some((b: ReRoCCMsgBundle) => true.B)
    ))
    rr_resp <> resp_arb.io.out
    resp_arb.io.in.foreach { i => i.valid := false.B }

    val status_new = Reg(new MStatus)
    val client_new = Reg(UInt(log2Ceil(numClients).W))

    when (rr_req.valid) {
      when (rr_req.bits.opcode === ReRoCCProtocolOpcodes.mAcquire) {
        rr_req.ready := true.B
        when (beat === 0.U) {
          status_new := rr_req.bits.data.asTypeOf(new MStatus)
          client_new := rr_req.bits.client_id
        } .elsewhen (beat === 1.U) {
          status_new := Cat(rr_req.bits.data, status_new.asUInt(63,0)).asTypeOf(new MStatus)
        }
        when (rr_req.bits.last) {
          rr_req.ready := resp_arb.io.in(0).ready
          resp_arb.io.in(0).valid := true.B
          when (state === s_idle && rr_req.fire()) {
            state := s_active
            status := status_new
            client := client_new
            ptbr := rr_req.bits.data.asTypeOf(new PTBR)
          }
        }
      } .elsewhen (rr_req.bits.opcode === ReRoCCProtocolOpcodes.mInst) {
        val tag = (rr_req.bits.data >> 33)(ibufSz-1,0)
        assert(state === s_active && (!inst_val(tag) || beat =/= 0.U))
        rr_req.ready := true.B
        val next_enq_inst = WireInit(enq_inst)
        when (beat === 0.U) {
          val update_status = rr_req.bits.data(32)
          val inst = rr_req.bits.data(31,0).asTypeOf(new RoCCInstruction)
          enq_inst_tag := tag
          enq_inst_new_mstatus := update_status
          enq_inst.inst := inst
          when (!inst.xs1        ) { enq_inst.rs1 := 0.U }
          when (!inst.xs2        ) { enq_inst.rs2 := 0.U }
          when (!update_status   ) { enq_inst.status := status }
        } .otherwise {
          val enq_inst_mstatus0 = enq_inst_new_mstatus && beat === 1.U
          val enq_inst_mstatus1 = enq_inst_new_mstatus && beat === 2.U
          val enq_inst_rs1      = enq_inst.inst.xs1 && beat === (Mux(enq_inst_new_mstatus, 2.U, 0.U) +& 1.U)
          val enq_inst_rs2      = enq_inst.inst.xs2 && beat === (Mux(enq_inst_new_mstatus, 2.U, 0.U) +& 1.U +& enq_inst.inst.xs1)
          when (enq_inst_mstatus0) {
            next_enq_inst.status := rr_req.bits.data.asTypeOf(new MStatus)
          }
          when (enq_inst_mstatus1) {
            next_enq_inst.status := Cat(rr_req.bits.data, enq_inst.status.asUInt(63,0)).asTypeOf(new MStatus)
          }
          when (enq_inst_rs1) {
            next_enq_inst.rs1 := rr_req.bits.data
          }
          when (enq_inst_rs2) {
            next_enq_inst.rs2 := rr_req.bits.data
          }
          enq_inst := next_enq_inst
        }
        when (rr_req.bits.last) {
          inst_buf(enq_inst_tag) := next_enq_inst
          inst_val(enq_inst_tag) := true.B
        }
      } .elsewhen (rr_req.bits.opcode === ReRoCCProtocolOpcodes.mRelease) {
        rr_req.ready := true.B
        state := s_rel_wait
      } .elsewhen (rr_req.bits.opcode === ReRoCCProtocolOpcodes.mUnbusy) {
        rr_req.ready := true.B
        state := s_unbusy
      } .otherwise {
        assert(false.B)
      }
    }

    // acquire->ack/nack
    resp_arb.io.in(0).bits.opcode := ReRoCCProtocolOpcodes.sAcqResp
    resp_arb.io.in(0).bits.client_id := rr_req.bits.client_id
    resp_arb.io.in(0).bits.manager_id := io.manager_id
    resp_arb.io.in(0).bits.data := Cat(ibufEntries.U, state === s_idle)
    resp_arb.io.in(0).bits.last := true.B
    resp_arb.io.in(0).bits.first := true.B

    // insts -> (inst_q, inst_ack)
    resp_arb.io.in(1).valid           := inst_val(inst_head) && inst_q.io.enq.ready
    resp_arb.io.in(1).bits.opcode     := ReRoCCProtocolOpcodes.sInstAck
    resp_arb.io.in(1).bits.client_id  := client
    resp_arb.io.in(1).bits.manager_id := io.manager_id
    resp_arb.io.in(1).bits.data       := inst_head
    resp_arb.io.in(1).bits.last       := true.B
    resp_arb.io.in(1).bits.first      := true.B
    inst_q.io.enq.valid               := inst_val(inst_head) && resp_arb.io.in(1).ready
    inst_q.io.enq.bits                := inst_buf(inst_head)
    inst_q.io.enq.bits.inst.opcode    := roccOpcode
    when (inst_q.io.enq.fire()) {
      inst_val(inst_head) := false.B
      inst_head := Mux(inst_head === (ibufEntries-1).U, 0.U, inst_head + 1.U)
    }

    // writebacks
    val resp = Queue(io.resp)
    val resp_rd = RegInit(false.B)
    resp_arb.io.in(2).valid           := resp.valid
    resp_arb.io.in(2).bits.opcode     := ReRoCCProtocolOpcodes.sWrite
    resp_arb.io.in(2).bits.client_id  := client
    resp_arb.io.in(2).bits.manager_id := io.manager_id
    resp_arb.io.in(2).bits.data       := Mux(resp_rd, resp.bits.rd, resp.bits.data)
    resp_arb.io.in(2).bits.last       := resp_rd
    resp_arb.io.in(2).bits.first      := !resp_rd
    when (resp_arb.io.in(2).fire()) { resp_rd := !resp_rd }
    resp.ready := resp_arb.io.in(2).ready && resp_rd

    // release
    resp_arb.io.in(3).valid           := state === s_rel_wait && !io.busy && inst_q.io.count === 0.U
    resp_arb.io.in(3).bits.opcode     := ReRoCCProtocolOpcodes.sRelResp
    resp_arb.io.in(3).bits.client_id  := client
    resp_arb.io.in(3).bits.manager_id := io.manager_id
    resp_arb.io.in(3).bits.data       := 0.U
    resp_arb.io.in(3).bits.last       := true.B
    resp_arb.io.in(3).bits.first      := true.B

    when (resp_arb.io.in(3).fire()) {
      state := s_sfence
      inst_val.foreach(_ := false.B)
      inst_head := 0.U
    }
    when (state === s_sfence) { state := s_idle }

    // unbusyack
    resp_arb.io.in(4).valid           := state === s_unbusy && !io.busy && inst_q.io.count === 0.U
    resp_arb.io.in(4).bits.opcode     := ReRoCCProtocolOpcodes.sUnbusyAck
    resp_arb.io.in(4).bits.client_id  := client
    resp_arb.io.in(4).bits.manager_id := io.manager_id
    resp_arb.io.in(4).bits.data       := 0.U
    resp_arb.io.in(4).bits.last       := true.B
    resp_arb.io.in(4).bits.first      := true.B

    when (resp_arb.io.in(4).fire()) { state := s_active }


  }
}

class ReRoCCManagerTile()(implicit p: Parameters) extends LazyModule {
  val reRoCCParams = p(TileKey).asInstanceOf[ReRoCCTileParams]
  val reRoCCId = reRoCCParams.reroccId
  def this(tileParams: ReRoCCTileParams, p: Parameters) = {
    this()(p.alterMap(Map(
      TileKey -> tileParams,
      TileVisibilityNodeKey -> TLEphemeralNode()(ValName("rerocc_manager"))
    )))
  }
  val reroccManagerIdSinkNode = BundleBridgeSink[UInt]()

  val rocc = reRoCCParams.genRoCC.get(p)
  require(rocc.opcodes.opcodes.size == 1)
  val rerocc_manager = LazyModule(new ReRoCCManager(reRoCCParams, rocc.opcodes.opcodes.head))
  val reRoCCNode = ReRoCCIdentityNode()
  rerocc_manager.node := ReRoCCBuffer() := reRoCCNode
  val tlNode = p(TileVisibilityNodeKey)
  val tlXbar = TLXbar()


  tlXbar :=* rocc.atlNode
  if (reRoCCParams.mergeTLNodes) {
    tlXbar :=* rocc.tlNode
  } else {
    tlNode :=* rocc.tlNode
  }
  tlNode :=* tlXbar

  // minicache
  val dcache = reRoCCParams.dcacheParams.map(_ => LazyModule(new MiniDCache(reRoCCId, SynchronousCrossing())(p)))
  dcache.map(d => tlXbar := d.node)

  val hellammio: Option[HellaMMIO] = if (!dcache.isDefined) {
    val h = LazyModule(new HellaMMIO(s"ReRoCC $reRoCCId MMIO"))
    tlXbar := h.node
    Some(h)
  } else { None }

  override lazy val module = new LazyModuleImp(this) {
    val dcacheArb = Module(new HellaCacheArbiter(2)(p))
    dcache.map(_.module.io.cpu).getOrElse(hellammio.get.module.io) <> dcacheArb.io.mem

    val edge = dcache.map(_.node.edges.out(0)).getOrElse(hellammio.get.node.edges.out(0))

    val ptw = Module(new PTW(1 + rocc.nPTWPorts)(edge, p))
    if (dcache.isDefined) {
      ptw.io.requestor(0) <> dcache.get.module.io.ptw
    } else {
      ptw.io.requestor(0) := DontCare
      ptw.io.requestor(0).req.valid := false.B
    }
    dcacheArb.io.requestor(0) <> ptw.io.mem

    val dcIF = Module(new SimpleHellaCacheIF)
    dcIF.io.requestor <> rocc.module.io.mem
    dcacheArb.io.requestor(1) <> dcIF.io.cache

    for (i <- 0 until rocc.nPTWPorts) {
      ptw.io.requestor(1+i) <> rocc.module.io.ptw(i)
    }

    rocc.module.io.cmd <> rerocc_manager.module.io.cmd
    rerocc_manager.module.io.resp <> rocc.module.io.resp
    rerocc_manager.module.io.busy := rocc.module.io.busy

    ptw.io.dpath <> rerocc_manager.module.io.ptw
  }
}

case object ReRoCCNoCKey extends Field[Option[ReRoCCNoCParams]](None)

trait CanHaveReRoCCTiles { this: HasTiles with constellation.soc.CanHaveGlobalNoC =>

  // WARNING: Not multi-clock safe
  val reRoCCClients = tiles.map { t => t match {
    case r: RocketTile => r.roccs collect { case r: ReRoCCClient => (t, r) }
    case b: BoomTile => b.roccs collect { case r: ReRoCCClient => (t, r) }
    case _ => Nil
  }}.flatten

  val reRoCCManagerIds = (0 until p(ReRoCCTileKey).size)
  val reRoCCManagerIdNexusNode = LazyModule(new BundleBridgeNexus[UInt](
    inputFn = BundleBridgeNexus.orReduction[UInt](false) _,
    outputFn = (prefix: UInt, n: Int) =>  Seq.tabulate(n) { i => {
      dontTouch(prefix | reRoCCManagerIds(i).U(7.W)) // dontTouch to keep constant prop from breaking tile dedup
    }},
    default = Some(() => 0.U(7.W)),
    inputRequiresOutput = true, // guard against this being driven but then ignored in tileHartIdIONodes below
    shouldBeInlined = false // can't inline something whose output we are are dontTouching
  )).node
  val reRoCCManagers = p(ReRoCCTileKey).zipWithIndex.map { case (g,i) =>
    val rerocc_tile = LazyModule(new ReRoCCManagerTile(g.copy(rowBits = p(SystemBusKey).beatBits, reroccId = i), p))
    println(s"ReRoCC Manager id $i is a ${rerocc_tile.rocc}")
    locateTLBusWrapper(SBUS).coupleFrom(s"port_named_rerocc_$i") {
      (_ :=* TLBuffer() :=* rerocc_tile.tlNode)
    }
    rerocc_tile.reroccManagerIdSinkNode := reRoCCManagerIdNexusNode
    rerocc_tile
  }
  require(!(reRoCCManagers.isEmpty ^ reRoCCClients.isEmpty))

  if (!reRoCCClients.isEmpty) {
    val rerocc_bus = p(ReRoCCNoCKey).map { k =>
      if (k.useGlobalNoC) {
        globalNoCDomain { LazyModule(new ReRoCCGlobalNoC(k)) }
      } else {
        LazyModule(new ReRoCCNoC(k))
      }
    }.getOrElse(LazyModule(new ReRoCCXbar()))
    reRoCCClients.foreach { case (t, c) => rerocc_bus.node := t { ReRoCCBuffer() := c.reRoCCNode } }
    reRoCCManagers.foreach { m => m.reRoCCNode := rerocc_bus.node }
  }
}
