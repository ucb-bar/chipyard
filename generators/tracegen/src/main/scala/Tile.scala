package tracegen

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{LazyModule, SynchronousCrossing}
import freechips.rocketchip.groundtest.{TraceGenerator, TraceGenParams, DummyPTW, GroundTestStatus}
import freechips.rocketchip.rocket.{DCache, NonBlockingDCache, SimpleHellaCacheIF, HellaCacheExceptions, HellaCacheReq}
import freechips.rocketchip.rocket.constants.{MemoryOpConstants}
import freechips.rocketchip.tile.{BaseTile, BaseTileModuleImp, HartsWontDeduplicate, TileKey}
import freechips.rocketchip.tilelink.{TLInwardNode, TLIdentityNode}
import freechips.rocketchip.interrupts._

import boom.lsu.{BoomNonBlockingDCache, LSU}
import boom.common.{BoomTileParams, MicroOp, BoomCoreParams}

class TraceGenTile(val id: Int, val params: TraceGenParams, q: Parameters)
    extends BaseTile(params, SynchronousCrossing(), HartsWontDeduplicate(params), q) {
  val dcache = params.dcache.map { dc => LazyModule(
    if (dc.nMSHRs == 0) new DCache(hartId, crossing)
    else new NonBlockingDCache(hartId))
  }.get

  val intInwardNode: IntInwardNode = IntIdentityNode()
  val intOutwardNode: IntOutwardNode = IntIdentityNode()
  val slaveNode: TLInwardNode = TLIdentityNode()
  val ceaseNode: IntOutwardNode = IntIdentityNode()
  val haltNode: IntOutwardNode = IntIdentityNode()
  val wfiNode: IntOutwardNode = IntIdentityNode()

  val masterNode = visibilityNode
  masterNode := dcache.node

  override lazy val module = new TraceGenTileModuleImp(this)
}

class BoomTraceGenTile(val id: Int, val params: TraceGenParams, q: Parameters)
  extends BaseTile(params, SynchronousCrossing(), HartsWontDeduplicate(params), q) {
  val boom_params = p.alterMap(Map(TileKey -> BoomTileParams(
    dcache=params.dcache,
    core=BoomCoreParams(nPMPs=0, numLdqEntries=32, numStqEntries=32, useVM=false))))
  val dcache = params.dcache.map {
    dc => LazyModule(new BoomNonBlockingDCache(hartId)(boom_params))
  }.get

  val intInwardNode: IntInwardNode = IntIdentityNode()
  val intOutwardNode: IntOutwardNode = IntIdentityNode()
  val slaveNode: TLInwardNode = TLIdentityNode()
  val ceaseNode: IntOutwardNode = IntIdentityNode()
  val haltNode: IntOutwardNode = IntIdentityNode()
  val wfiNode: IntOutwardNode = IntIdentityNode()

  val masterNode = visibilityNode
  masterNode := dcache.node

  override lazy val module = new BoomTraceGenTileModuleImp(this)
}

class BoomTraceGenTileModuleImp(outer: BoomTraceGenTile)
  extends BaseTileModuleImp(outer) with MemoryOpConstants {

  val status = IO(new GroundTestStatus)

  val tracegen = Module(new TraceGenerator(outer.params))
  tracegen.io.mem := DontCare
  tracegen.io.hartid := constants.hartid

  val ptw = Module(new DummyPTW(1))

  val lsu = Module(new LSU()(outer.boom_params, outer.dcache.module.edge))
  lsu.io.core.tsc_reg := 0.U(1.W)
  ptw.io.requestors.head <> lsu.io.ptw
  outer.dcache.module.io.lsu <> lsu.io.dmem


  val rob_sz = outer.boom_params(TileKey).core.asInstanceOf[BoomCoreParams].numRobEntries
  val rob = Reg(Vec(rob_sz, new HellaCacheReq))
  val rob_respd = RegInit(VecInit((~(0.U(rob_sz.W))).asBools))
  val rob_uop = Reg(Vec(rob_sz, new MicroOp()(outer.boom_params)))
  val rob_bsy  = RegInit(VecInit(0.U(rob_sz.W).asBools))
  val rob_head = RegInit(0.U(log2Up(rob_sz).W))
  val rob_tail = RegInit(0.U(log2Up(rob_sz).W))
  val rob_wait_till_empty = RegInit(false.B)
  val ready_for_amo = rob_tail === rob_head && lsu.io.core.fencei_rdy
  when (ready_for_amo) {
    rob_wait_till_empty := false.B
  }

  def WrapInc(idx: UInt, max: Int): UInt = {
    Mux(idx === (max-1).U, 0.U, idx + 1.U)
  }


  tracegen.io.mem.req.ready := (!rob_bsy(rob_tail) &&
    !rob_wait_till_empty &&
    (ready_for_amo || !(isAMO(tracegen.io.mem.req.bits.cmd) || tracegen.io.mem.req.bits.cmd === M_XLR || tracegen.io.mem.req.bits.cmd === M_XSC)) &&
    (WrapInc(rob_tail, rob_sz) =/= rob_head) &&
    !(lsu.io.core.ldq_full(0) && isRead(tracegen.io.mem.req.bits.cmd)) &&
    !(lsu.io.core.stq_full(0) && isWrite(tracegen.io.mem.req.bits.cmd))
  )

  val tracegen_uop = WireInit((0.U).asTypeOf(new MicroOp()(outer.boom_params)))
  tracegen_uop.uses_ldq     := isRead(tracegen.io.mem.req.bits.cmd) && !isWrite(tracegen.io.mem.req.bits.cmd)
  tracegen_uop.uses_stq     := isWrite(tracegen.io.mem.req.bits.cmd)
  tracegen_uop.rob_idx      := rob_tail
  tracegen_uop.uopc         := tracegen.io.mem.req.bits.tag
  tracegen_uop.mem_size     := tracegen.io.mem.req.bits.size
  tracegen_uop.mem_cmd      := tracegen.io.mem.req.bits.cmd
  tracegen_uop.mem_signed   := tracegen.io.mem.req.bits.signed
  tracegen_uop.ldq_idx      := lsu.io.core.dis_ldq_idx(0)
  tracegen_uop.stq_idx      := lsu.io.core.dis_stq_idx(0)
  tracegen_uop.is_amo       := isAMO(tracegen.io.mem.req.bits.cmd) || tracegen.io.mem.req.bits.cmd === M_XSC
  tracegen_uop.ctrl.is_load := isRead(tracegen.io.mem.req.bits.cmd) && !isWrite(tracegen.io.mem.req.bits.cmd)
  tracegen_uop.ctrl.is_sta  := isWrite(tracegen.io.mem.req.bits.cmd)
  tracegen_uop.ctrl.is_std  := isWrite(tracegen.io.mem.req.bits.cmd)

  lsu.io.core.dis_uops(0).valid         := tracegen.io.mem.req.fire()
  lsu.io.core.dis_uops(0).bits          := tracegen_uop
  
  when (tracegen.io.mem.req.fire()) {
    rob_tail := WrapInc(rob_tail, rob_sz)
    rob_bsy(rob_tail)   := true.B
    rob_uop(rob_tail)   := tracegen_uop
    rob_respd(rob_tail) := false.B
    rob(rob_tail)       := tracegen.io.mem.req.bits
    when (
      isAMO(tracegen.io.mem.req.bits.cmd)    ||
      tracegen.io.mem.req.bits.cmd === M_XLR ||
      tracegen.io.mem.req.bits.cmd === M_XSC
    ) {
      rob_wait_till_empty := true.B
    }
  }

  lsu.io.core.fp_stdata.valid := false.B
  lsu.io.core.fp_stdata.bits  := DontCare



  lsu.io.core.commit.valids(0) := (!rob_bsy(rob_head) && rob_head =/= rob_tail && rob_respd(rob_head))
  lsu.io.core.commit.uops(0)   := rob_uop(rob_head)
  lsu.io.core.commit.rbk_valids(0) := false.B
  lsu.io.core.commit.rollback := false.B
  lsu.io.core.commit.fflags := DontCare
  when (lsu.io.core.commit.valids(0)) {
    rob_head := WrapInc(rob_head, rob_sz)
  }

  when (lsu.io.core.clr_bsy(0).valid) {
    rob_bsy(lsu.io.core.clr_bsy(0).bits) := false.B
  }
  when (lsu.io.core.clr_unsafe(0).valid && rob(lsu.io.core.clr_unsafe(0).bits).cmd =/= M_XLR) {
    rob_bsy(lsu.io.core.clr_unsafe(0).bits) := false.B
  }
  when (lsu.io.core.exe(0).iresp.valid) {
    rob_bsy(lsu.io.core.exe(0).iresp.bits.uop.rob_idx) := false.B
  }


  assert(!lsu.io.core.lxcpt.valid)

  lsu.io.core.exe(0).req.valid     := RegNext(tracegen.io.mem.req.fire())
  lsu.io.core.exe(0).req.bits      := DontCare
  lsu.io.core.exe(0).req.bits.uop  := RegNext(tracegen_uop)
  lsu.io.core.exe(0).req.bits.addr := RegNext(tracegen.io.mem.req.bits.addr)
  lsu.io.core.exe(0).req.bits.data := RegNext(tracegen.io.mem.req.bits.data)

  tracegen.io.mem.resp.valid     := lsu.io.core.exe(0).iresp.valid
  tracegen.io.mem.resp.bits      := DontCare
  tracegen.io.mem.resp.bits.tag  := lsu.io.core.exe(0).iresp.bits.uop.uopc
  tracegen.io.mem.resp.bits.size := lsu.io.core.exe(0).iresp.bits.uop.mem_size
  tracegen.io.mem.resp.bits.data := lsu.io.core.exe(0).iresp.bits.data

  val store_resp_idx = PriorityEncoder((0 until rob_sz) map {i =>
    !rob_respd(i) && isWrite(rob(i).cmd)
  })
  val can_do_store_resp = ~rob_respd(store_resp_idx) && isWrite(rob(store_resp_idx).cmd) && !isRead(rob(store_resp_idx).cmd)
  when (can_do_store_resp && !lsu.io.core.exe(0).iresp.valid) {
    rob_respd(store_resp_idx)     := true.B
    tracegen.io.mem.resp.valid    := true.B
    tracegen.io.mem.resp.bits.tag := rob(store_resp_idx).tag
  }

  when (lsu.io.core.exe(0).iresp.valid) {
    rob_respd(lsu.io.core.exe(0).iresp.bits.uop.rob_idx) := true.B
  }

  lsu.io.core.exe(0).fresp.ready := true.B
  lsu.io.core.exe(0).iresp.ready := true.B


  lsu.io.core.exception := false.B
  lsu.io.core.fence_dmem := false.B

  lsu.io.hellacache           := DontCare
  lsu.io.hellacache.req.valid := false.B

  lsu.io.core.rob_pnr_idx := rob_tail
  lsu.io.core.commit_load_at_rob_head := false.B

  lsu.io.core.brinfo := DontCare
  lsu.io.core.brinfo.valid := false.B
  lsu.io.core.rob_head_idx := rob_head

  status.finished := tracegen.io.finished
  status.timeout.valid := tracegen.io.timeout
  status.timeout.bits := 0.U
  status.error.valid := false.B
}

class TraceGenTileModuleImp(outer: TraceGenTile)
    extends BaseTileModuleImp(outer) {
  val status = IO(new GroundTestStatus)
  val halt_and_catch_fire = None

  val ptw = Module(new DummyPTW(1))
  ptw.io.requestors.head <> outer.dcache.module.io.ptw

  val tracegen = Module(new TraceGenerator(outer.params))
  tracegen.io.hartid := constants.hartid

  val dcacheIF = Module(new SimpleHellaCacheIF())
  dcacheIF.io.requestor <> tracegen.io.mem
  outer.dcache.module.io.cpu <> dcacheIF.io.cache

  status.finished := tracegen.io.finished
  status.timeout.valid := tracegen.io.timeout
  status.timeout.bits := 0.U
  status.error.valid := false.B

  assert(!tracegen.io.timeout, s"TraceGen tile ${outer.id}: request timed out")
}
