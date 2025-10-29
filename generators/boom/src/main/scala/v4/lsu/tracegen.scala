package boom.v4.lsu

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy.{SimpleDevice, LazyModule, BundleBridgeSource}
import freechips.rocketchip.prci.{SynchronousCrossing, ClockCrossingType}
import freechips.rocketchip.groundtest._
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.constants.{MemoryOpConstants}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink.{TLInwardNode, TLIdentityNode, TLOutwardNode, TLTempNode}
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem._
import boom.v4.lsu.{BoomNonBlockingDCache, LSU, LSUCoreIO}
import boom.v4.common.{BoomTileParams, MicroOp, BoomCoreParams, BoomModule}
import freechips.rocketchip.prci.ClockSinkParameters

class BoomLSUShim(implicit p: Parameters) extends BoomModule()(p)
  with MemoryOpConstants {
  val io = IO(new Bundle {
    val lsu = Flipped(new LSUCoreIO)
    val tracegen = Flipped(new HellaCacheIO)
  })

  io.lsu := DontCare
  io.lsu.tsc_reg := 0.U(1.W)

  val rob_sz = numRobEntries
  val rob = Reg(Vec(rob_sz, new HellaCacheReq))
  val rob_respd = RegInit(VecInit((~(0.U(rob_sz.W))).asBools))
  val rob_uop = Reg(Vec(rob_sz, new MicroOp))
  val rob_bsy  = RegInit(VecInit(0.U(rob_sz.W).asBools))
  val rob_head = RegInit(0.U(log2Up(rob_sz).W))
  val rob_tail = RegInit(0.U(log2Up(rob_sz).W))
  val rob_wait_till_empty = RegInit(false.B)
  val ready_for_amo = rob_tail === rob_head && io.lsu.fencei_rdy
  when (ready_for_amo) {
    rob_wait_till_empty := false.B
  }

  def WrapInc(idx: UInt, max: Int): UInt = {
    Mux(idx === (max-1).U, 0.U, idx + 1.U)
  }

  io.tracegen := DontCare
  io.tracegen.req.ready := (!rob_bsy(rob_tail) &&
    !rob_wait_till_empty &&
    (ready_for_amo || !(isAMO(io.tracegen.req.bits.cmd) || io.tracegen.req.bits.cmd === M_XLR || io.tracegen.req.bits.cmd === M_XSC)) &&
    (WrapInc(rob_tail, rob_sz) =/= rob_head) &&
    !(io.lsu.ldq_full(0) && isRead(io.tracegen.req.bits.cmd)) &&
    !(io.lsu.stq_full(0) && isWrite(io.tracegen.req.bits.cmd))
  )

  val tracegen_uop = WireInit((0.U).asTypeOf(new MicroOp))
  tracegen_uop.uses_ldq     := isRead(io.tracegen.req.bits.cmd) && !isWrite(io.tracegen.req.bits.cmd)
  tracegen_uop.uses_stq     := isWrite(io.tracegen.req.bits.cmd)
  tracegen_uop.rob_idx      := rob_tail
  tracegen_uop.debug_inst   := io.tracegen.req.bits.tag
  tracegen_uop.mem_size     := io.tracegen.req.bits.size
  tracegen_uop.mem_cmd      := io.tracegen.req.bits.cmd
  tracegen_uop.mem_signed   := io.tracegen.req.bits.signed
  tracegen_uop.ldq_idx      := io.lsu.dis_ldq_idx(0)
  tracegen_uop.stq_idx      := io.lsu.dis_stq_idx(0)
  tracegen_uop.is_amo       := isAMO(io.tracegen.req.bits.cmd) || io.tracegen.req.bits.cmd === M_XSC
  tracegen_uop.uses_ldq     := isRead(io.tracegen.req.bits.cmd) && !isWrite(io.tracegen.req.bits.cmd)
  tracegen_uop.uses_stq     := isWrite(io.tracegen.req.bits.cmd)

  io.lsu.dis_uops(0).valid         := io.tracegen.req.fire
  io.lsu.dis_uops(0).bits          := tracegen_uop

  when (io.tracegen.req.fire) {
    rob_tail := WrapInc(rob_tail, rob_sz)
    rob_bsy(rob_tail)   := true.B
    rob_uop(rob_tail)   := tracegen_uop
    rob_respd(rob_tail) := false.B
    rob(rob_tail)       := io.tracegen.req.bits
    when (
      isAMO(io.tracegen.req.bits.cmd)    ||
      io.tracegen.req.bits.cmd === M_XLR ||
      io.tracegen.req.bits.cmd === M_XSC
    ) {
      rob_wait_till_empty := true.B
    }
  }

  io.lsu.dgen(1).valid := false.B
  io.lsu.dgen(1).bits  := DontCare



  io.lsu.commit.valids(0) := (!rob_bsy(rob_head) && rob_head =/= rob_tail && rob_respd(rob_head))
  io.lsu.commit.uops(0)   := rob_uop(rob_head)
  io.lsu.commit.fflags := DontCare
  when (io.lsu.commit.valids(0)) {
    rob_head := WrapInc(rob_head, rob_sz)
  }

  when (io.lsu.clr_bsy(0).valid) {
    rob_bsy(io.lsu.clr_bsy(0).bits) := false.B
  }
  when (io.lsu.clr_unsafe(0).valid && rob(io.lsu.clr_unsafe(0).bits).cmd =/= M_XLR) {
    rob_bsy(io.lsu.clr_unsafe(0).bits) := false.B
  }
  when (io.lsu.iresp(0).valid) {
    rob_bsy(io.lsu.iresp(0).bits.uop.rob_idx) := false.B
  }


  assert(!io.lsu.lxcpt.valid)


  io.lsu.agen(0).valid     := ShiftRegister(io.tracegen.req.fire, 2)
  io.lsu.agen(0).bits      := DontCare
  io.lsu.agen(0).bits.uop  := ShiftRegister(tracegen_uop, 2)
  io.lsu.agen(0).bits.data := ShiftRegister(io.tracegen.req.bits.addr, 2)

  io.lsu.dgen(0).valid     := ShiftRegister(io.tracegen.req.fire && tracegen_uop.uses_stq, 2)
  io.lsu.dgen(0).bits      := DontCare
  io.lsu.dgen(0).bits.uop  := ShiftRegister(tracegen_uop, 2)
  io.lsu.dgen(0).bits.data := ShiftRegister(io.tracegen.req.bits.data, 2)


  io.tracegen.resp.valid     := io.lsu.iresp(0).valid
  io.tracegen.resp.bits      := DontCare
  io.tracegen.resp.bits.tag  := io.lsu.iresp(0).bits.uop.debug_inst
  io.tracegen.resp.bits.size := io.lsu.iresp(0).bits.uop.mem_size
  io.tracegen.resp.bits.data := io.lsu.iresp(0).bits.data

  val store_resp_idx = PriorityEncoder((0 until rob_sz) map {i =>
    !rob_respd(i) && isWrite(rob(i).cmd)
  })
  val can_do_store_resp = ~rob_respd(store_resp_idx) && isWrite(rob(store_resp_idx).cmd) && !isRead(rob(store_resp_idx).cmd)
  when (can_do_store_resp && !io.lsu.iresp(0).valid) {
    rob_respd(store_resp_idx)     := true.B
    io.tracegen.resp.valid    := true.B
    io.tracegen.resp.bits.tag := rob(store_resp_idx).tag
  }

  when (io.lsu.iresp(0).valid) {
    rob_respd(io.lsu.iresp(0).bits.uop.rob_idx) := true.B
  }


  io.lsu.exception := false.B
  io.lsu.fence_dmem := false.B

  io.lsu.rob_pnr_idx := rob_tail
  io.lsu.commit_load_at_rob_head := false.B

  io.lsu.brupdate.b1 := (0.U).asTypeOf(new boom.v4.exu.BrUpdateMasks)
  io.lsu.brupdate.b2.uop := DontCare
  io.lsu.brupdate.b2.mispredict := false.B
  io.lsu.brupdate.b2.taken := false.B
  io.lsu.brupdate.b2.cfi_type := 0.U
  io.lsu.brupdate.b2.pc_sel := 0.U
  io.lsu.brupdate.b2.jalr_target := 0.U
  io.lsu.brupdate.b2.target_offset := 0.S(2.W)

  io.lsu.rob_head_idx := rob_head

  io.tracegen.ordered := ready_for_amo && io.lsu.fencei_rdy

  io.lsu.mcontext := 0.U(1.W)
  io.lsu.scontext := 0.U(1.W)

}

case class BoomTraceGenTileAttachParams(
  tileParams: BoomTraceGenParams,
  crossingParams: HierarchicalElementCrossingParamsLike
) extends CanAttachTile {
  type TileType = BoomTraceGenTile
  val lookup: LookupByHartIdImpl = HartsWontDeduplicate(tileParams)
}


case class BoomTraceGenParams(
    wordBits: Int,
    addrBits: Int,
    addrBag: List[BigInt],
    maxRequests: Int,
    memStart: BigInt,
    numGens: Int,
    dcache: Option[DCacheParams] = Some(DCacheParams()),
    tileId: Int = 0
) extends InstantiableTileParams[BoomTraceGenTile]
{
  def instantiate(crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): BoomTraceGenTile = {
    new BoomTraceGenTile(this, crossing, lookup)
  }
  val core = RocketCoreParams(nPMPs = 0) //TODO remove this
  val btb = None
  val icache = Some(ICacheParams())
  val beuAddr = None
  val blockerCtrlAddr = None
  val name = None
  val traceParams = TraceGenParams(wordBits, addrBits, addrBag, maxRequests, memStart, numGens, dcache, tileId)
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  val baseName = "boom_l1_tracegen"
  val uniqueName = s"${baseName}_$tileId"
}

class BoomTraceGenTile private(
  val params: BoomTraceGenParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters) extends BaseTile(params, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{
  def this(params: BoomTraceGenParams, crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  val cpuDevice: SimpleDevice = new SimpleDevice("groundtest", Nil)
  val intOutwardNode = None
  val slaveNode: TLInwardNode = TLIdentityNode()
  val statusNode = BundleBridgeSource(() => new GroundTestStatus)

  val boom_params = p.alterMap(Map(TileKey -> BoomTileParams(
    dcache=params.dcache,
    core=BoomCoreParams(nPMPs=0, numLdqEntries=16, numStqEntries=16, useVM=false))))
  val dcache = LazyModule(new BoomNonBlockingDCache(tileId)(boom_params))


  val masterNode: TLOutwardNode = TLIdentityNode() := visibilityNode := dcache.node

  override lazy val module = new BoomTraceGenTileModuleImp(this)
}

class BoomTraceGenTileModuleImp(outer: BoomTraceGenTile)
  extends BaseTileModuleImp(outer){

  val status = outer.statusNode.bundle
  val halt_and_catch_fire = None

  val tracegen = Module(new TraceGenerator(outer.params.traceParams))
  tracegen.io.hartid := outer.hartIdSinkNode.bundle

  val ptw = Module(new DummyPTW(1))
  val lsu = Module(new LSU()(outer.boom_params, outer.dcache.module.edge))
  val boom_shim = Module(new BoomLSUShim()(outer.boom_params))
  ptw.io.requestors.head <> lsu.io.ptw
  outer.dcache.module.io.lsu <> lsu.io.dmem
  boom_shim.io.tracegen <> tracegen.io.mem
  tracegen.io.fence_rdy := boom_shim.io.tracegen.ordered
  boom_shim.io.lsu <> lsu.io.core

  // Normally the PTW would use this port
  lsu.io.hellacache           := DontCare
  lsu.io.hellacache.req.valid := false.B

  outer.reportCease(Some(tracegen.io.finished))
  outer.reportHalt(Some(tracegen.io.timeout))
  outer.reportWFI(None)

  status.timeout.valid := tracegen.io.timeout
  status.timeout.bits := 0.U
  status.error.valid := false.B

  assert(!tracegen.io.timeout, s"TraceGen tile ${outer.tileParams.tileId}: request timed out")

}
