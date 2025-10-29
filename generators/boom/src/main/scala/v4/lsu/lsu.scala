//******************************************************************************
// Copyright (c) 2012 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISCV Out-of-Order Load/Store Unit
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// Load/Store Unit is made up of the Load-Address Queue, the Store-Address
// Queue, and the Store-Data queue (LAQ, SAQ, and SDQ).
//
// Stores are sent to memory at (well, after) commit, loads are executed
// optimstically ASAP.  If a misspeculation was discovered, the pipeline is
// cleared. Loads put to sleep are retried.  If a LoadAddr and StoreAddr match,
// the Load can receive its data by forwarding data out of the Store-Data
// Queue.
//
// Currently, loads are sent to memory immediately, and in parallel do an
// associative search of the SAQ, on entering the LSU. If a hit on the SAQ
// search, the memory request is killed on the next cycle, and if the SDQ entry
// is valid, the store data is forwarded to the load (delayed to match the
// load-use delay to delay with the write-port structural hazard). If the store
// data is not present, or it's only a partial match (SB->LH), the load is put
// to sleep in the LAQ.
//
// Memory ordering violations are detected by stores at their addr-gen time by
// associatively searching the LAQ for newer loads that have been issued to
// memory.
//
// The store queue contains both speculated and committed stores.
//
// Only one port to memory... loads and stores have to fight for it, West Side
// Story style.
//
// TODO:
//    - Add predicting structure for ordering failures
//    - currently won't STD forward if DMEM is busy
//    - ability to turn off things if VM is disabled
//    - reconsider port count of the wakeup, retry stuff

package boom.v4.lsu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.Str

import boom.v4.common._
import boom.v4.exu.{BrUpdateInfo, Exception, CommitSignals, MemGen, ExeUnitResp, Wakeup}
import boom.v4.util._

class BoomDCacheReq(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomUOP
{
  val addr  = UInt(coreMaxAddrBits.W)
  val data  = Bits(coreDataBits.W)
  val is_hella = Bool() // Is this the hellacache req? If so this is not tracked in LDQ or STQ
}

class BoomDCacheResp(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomUOP
{
  val data = Bits(coreDataBits.W)
  val is_hella = Bool()
}

class LSUDMemIO(implicit p: Parameters, edge: TLEdgeOut) extends BoomBundle()(p)
{
  // In LSU's dmem stage, send the request
  val req         = new DecoupledIO(Vec(lsuWidth, Valid(new BoomDCacheReq)))
  // In LSU's LCAM search stage, kill if order fail (or forwarding possible)
  val s1_kill     = Output(Vec(lsuWidth, Bool()))
  // The s1 inflight request will likely get nacked next cycle
  val s1_nack_advisory = Input(Vec(lsuWidth, Bool()))
  // Get a request any cycle
  val resp        = Flipped(Vec(lsuWidth, new ValidIO(new BoomDCacheResp)))
  // The cache irrevocably accepted our store
  val store_ack   = Flipped(Vec(lsuWidth, new ValidIO(new BoomDCacheReq)))
  // In our response stage, if we get a nack, we need to reexecute
  val nack        = Flipped(Vec(lsuWidth, new ValidIO(new BoomDCacheReq)))

  val ll_resp     = Flipped(new DecoupledIO(new BoomDCacheResp))

  val brupdate       = Output(new BrUpdateInfo)
  val exception    = Output(Bool())
  val rob_pnr_idx  = Output(UInt(robAddrSz.W))
  val rob_head_idx = Output(UInt(robAddrSz.W))

  val release = Flipped(new DecoupledIO(new TLBundleC(edge.bundle)))

  // Clears prefetching MSHRs
  val force_order  = Output(Bool())
  val ordered     = Input(Bool())

  val perf = Input(new Bundle {
    val acquire = Bool()
    val release = Bool()
  })

}

class LSUCoreIO(implicit p: Parameters) extends BoomBundle()(p)
{

  val agen        = Flipped(Vec(lsuWidth, Valid(new MemGen)))
  val dgen        = Flipped(Vec(memWidth + 1, Valid(new MemGen)))

  val iwakeups    = Vec(lsuWidth, Valid(new Wakeup))

  val iresp       = Vec(lsuWidth, Valid(new ExeUnitResp(xLen)))
  val fresp       = Vec(lsuWidth, Valid(new ExeUnitResp(xLen)))

  val sfence      = Flipped(Valid(new rocket.SFenceReq))

  val dis_uops    = Flipped(Vec(coreWidth, Valid(new MicroOp)))
  val dis_ldq_idx = Output(Vec(coreWidth, UInt((1+ldqAddrSz).W)))
  val dis_stq_idx = Output(Vec(coreWidth, UInt((1+stqAddrSz).W)))

  val ldq_full    = Output(Vec(coreWidth, Bool()))
  val stq_full    = Output(Vec(coreWidth, Bool()))

  val commit      = Input(new CommitSignals)
  val commit_load_at_rob_head = Input(Bool())

  // Stores clear busy bit when stdata is received
  // memWidth for incoming addr/datagen, +1 for fp dgen, +1 for clr_head pointer
  val clr_bsy         = Output(Vec(coreWidth, Valid(UInt(robAddrSz.W))))

  // Speculatively safe load (barring memory ordering failure)
  val clr_unsafe      = Output(Vec(lsuWidth, Valid(UInt(robAddrSz.W))))

  // Tell the DCache to clear prefetches/speculating misses
  val fence_dmem   = Input(Bool())


  val brupdate     = Input(new BrUpdateInfo)
  val rob_pnr_idx  = Input(UInt(robAddrSz.W))
  val rob_head_idx = Input(UInt(robAddrSz.W))
  val exception    = Input(Bool())

  val fencei_rdy  = Output(Bool())

  val lxcpt       = Output(Valid(new Exception))

  val tsc_reg     = Input(UInt())

  val status = Input(new rocket.MStatus)
  val bp     = Input(Vec(nBreakpoints, new rocket.BP))
  val mcontext = Input(UInt())
  val scontext = Input(UInt())

  val perf        = Output(new Bundle {
    val acquire = Bool()
    val release = Bool()
    val tlbMiss = Bool()
  })
}

class LSUIO(implicit p: Parameters, edge: TLEdgeOut) extends BoomBundle()(p)
{
  val ptw   = new rocket.TLBPTWIO
  val core  = new LSUCoreIO
  val dmem  = new LSUDMemIO

  val hellacache = Flipped(new freechips.rocketchip.rocket.HellaCacheIO)
}

class LDQEntry(implicit p: Parameters) extends BoomBundle()(p)
    with HasBoomUOP
{
  val addr                = Valid(UInt(coreMaxAddrBits.W))
  val addr_is_virtual     = Bool() // Virtual address, we got a TLB miss
  val addr_is_uncacheable = Bool() // Uncacheable, wait until head of ROB to execute

  val executed            = Bool() // load sent to memory, reset by NACKs
  val succeeded           = Bool()
  val order_fail          = Bool()
  val observed            = Bool()

  val next_stq_idx        = UInt((1+stqAddrSz).W)

  val ld_byte_mask        = UInt(8.W)

  val forward_std_val     = Bool()
  val forward_stq_idx     = UInt((1+stqAddrSz).W) // Which store did we get the store-load forward from?

  val debug_wb_data       = UInt(xLen.W)
}

class STQEntry(implicit p: Parameters) extends BoomBundle()(p)
   with HasBoomUOP
{
  val addr                = Valid(UInt(coreMaxAddrBits.W))
  val addr_is_virtual     = Bool() // Virtual address, we got a TLB miss
  val data                = Valid(UInt(xLen.W))

  val committed           = Bool() // committed by ROB
  val succeeded           = Bool() // D$ has ack'd this, we don't need to maintain this anymore
  val can_execute         = Bool()
  val cleared             = Bool() // we've cleared this entry in the ROB
  val next_ldq_idx        = UInt((1+ldqAddrSz).W)

  val debug_wb_data       = UInt(xLen.W)
}


class LSU(implicit p: Parameters, edge: TLEdgeOut) extends BoomModule()(p)
  with rocket.HasL1HellaCacheParameters
{
  val io = IO(new LSUIO)


  // invariant: saq cannot dequeue when there are older loads uncommitted
  //val ldq                 = Reg(Vec(numLdqEntries, Valid(new LDQEntry)))
  val ldq_valid               = Reg(Vec(numLdqEntries, Bool()))
  val ldq_uop                 = Reg(Vec(numLdqEntries, new MicroOp))
  val ldq_addr                = Reg(Vec(numLdqEntries, Valid(UInt(coreMaxAddrBits.W))))
  val ldq_span_page           = Reg(Vec(numLdqEntries, Bool()))
  val ldq_addr_is_virtual     = Reg(Vec(numLdqEntries, Bool()))
  val ldq_addr_is_uncacheable = Reg(Vec(numLdqEntries, Bool()))
  val ldq_executed            = Reg(Vec(numLdqEntries, Bool()))
  val ldq_succeeded           = Reg(Vec(numLdqEntries, Bool()))
  val ldq_order_fail          = Reg(Vec(numLdqEntries, Bool()))
  val ldq_observed            = Reg(Vec(numLdqEntries, Bool()))
  val ldq_next_stq_idx        = Reg(Vec(numLdqEntries, UInt((stqAddrSz+1).W))) // the one bit is for overflow calculation
  val ldq_ld_byte_mask        = Reg(Vec(numLdqEntries, UInt(8.W)))
  val ldq_forward_std_val     = Reg(Vec(numLdqEntries, Bool()))
  val ldq_forward_stq_idx     = Reg(Vec(numLdqEntries, UInt((1+stqAddrSz).W)))
  val ldq_debug_wb_data       = Reg(Vec(numLdqEntries, UInt(xLen.W)))
  def ldq_read(idx: UInt): Valid[LDQEntry] = {
    val e = Wire(Valid(new LDQEntry))
    e.valid                    := ldq_valid              (idx)
    e.bits.uop                 := ldq_uop                (idx)
    e.bits.addr                := ldq_addr               (idx)
    e.bits.addr_is_virtual     := ldq_addr_is_virtual    (idx)
    e.bits.addr_is_uncacheable := ldq_addr_is_uncacheable(idx)
    e.bits.executed            := ldq_executed           (idx)
    e.bits.succeeded           := ldq_succeeded          (idx)
    e.bits.order_fail          := ldq_order_fail         (idx)
    e.bits.observed            := ldq_observed           (idx)
    e.bits.next_stq_idx        := ldq_next_stq_idx       (idx)    
    e.bits.ld_byte_mask        := ldq_ld_byte_mask       (idx)
    e.bits.forward_std_val     := ldq_forward_std_val    (idx)
    e.bits.forward_stq_idx     := ldq_forward_stq_idx    (idx)
    e.bits.debug_wb_data       := ldq_debug_wb_data      (idx)
    e
  }
  //val stq = Reg(Vec(numStqEntries, Valid(new STQEntry)))
  val stq_valid           = Reg(Vec(numStqEntries, Bool()))
  val stq_uop             = Reg(Vec(numStqEntries, new MicroOp))
  val stq_addr            = Reg(Vec(numStqEntries, Valid(UInt(coreMaxAddrBits.W))))
  val stq_addr_is_virtual = Reg(Vec(numStqEntries, Bool()))
  val stq_data            = Reg(Vec(numStqEntries, Valid(UInt(xLen.W))))
  val stq_committed       = Reg(Vec(numStqEntries, Bool()))
  val stq_succeeded       = Reg(Vec(numStqEntries, Bool()))
  val stq_can_execute     = Reg(Vec(numStqEntries, Bool()))
  val stq_cleared         = Reg(Vec(numStqEntries, Bool()))
  val stq_debug_wb_data   = Reg(Vec(numStqEntries, UInt(xLen.W)))
  val stq_next_ldq_idx    = Reg(Vec(numStqEntries, UInt((1+ldqAddrSz).W)))
  def stq_read(idx: UInt): Valid[STQEntry] = {
    val e = Wire(Valid(new STQEntry))
    e.valid                    := stq_valid              (idx)
    e.bits.uop                 := stq_uop                (idx)
    e.bits.addr                := stq_addr               (idx)
    e.bits.addr_is_virtual     := stq_addr_is_virtual    (idx)
    e.bits.data                := stq_data               (idx)
    e.bits.committed           := stq_committed          (idx)
    e.bits.succeeded           := stq_succeeded          (idx)
    e.bits.can_execute         := stq_can_execute        (idx)
    e.bits.cleared             := stq_cleared            (idx)
    e.bits.debug_wb_data       := stq_debug_wb_data      (idx)
    e.bits.next_ldq_idx        := stq_next_ldq_idx       (idx)
    e
  }


  val ldq_head         = Reg(UInt((ldqAddrSz+1).W))
  val ldq_tail         = Reg(UInt((ldqAddrSz+1).W))
  val stq_head         = Reg(UInt((stqAddrSz+1).W)) // point to next store to clear from STQ (i.e., send to memory)
  val stq_tail         = Reg(UInt((stqAddrSz+1).W))
  val stq_commit_head  = Reg(UInt((stqAddrSz+1).W)) // point to next store to commit
  val stq_execute_head = Reg(UInt((stqAddrSz+1).W)) // point to next store to execute





  val h_ready :: h_s1 :: h_s2 :: h_s2_nack :: h_wait :: h_replay :: h_dead :: Nil = Enum(7)
  // s1 : do TLB, if success and not killed, fire request go to h_s2
  //      store s1_data to register
  //      if tlb miss, go to s2_nack
  //      if don't get TLB, go to s2_nack
  //      store tlb xcpt
  // s2 : If kill, go to dead
  //      If tlb xcpt, send tlb xcpt, go to dead
  // s2_nack : send nack, go to dead
  // wait : wait for response, if nack, go to replay
  // replay : refire request, use already translated address
  // dead : wait for response, ignore it
  val hella_state           = RegInit(h_ready)
  val hella_req             = Reg(new rocket.HellaCacheReq)
  val hella_data            = Reg(new rocket.HellaCacheWriteData)
  val hella_paddr           = Reg(UInt(paddrBits.W))
  val hella_xcpt            = Reg(new rocket.HellaCacheExceptions)


  val dtlb = Module(new NBDTLB(
    instruction = false, lgMaxSize = log2Ceil(coreDataBytes), rocket.TLBConfig(dcacheParams.nTLBSets, dcacheParams.nTLBWays)))

  io.ptw <> dtlb.io.ptw
  io.core.perf.tlbMiss := io.ptw.req.fire
  io.core.perf.acquire := io.dmem.perf.acquire
  io.core.perf.release := io.dmem.perf.release



  val clear_store     = WireInit(false.B)


  def widthMap[T <: Data](f: Int => T) = VecInit((0 until lsuWidth).map(f))

  // default connection from flip flop to next state

  val ldq_will_succeed        = WireDefault(ldq_succeeded)
  ldq_succeeded := ldq_will_succeed


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // Enqueue new entries
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // Decode stage
  var ld_enq_idx = ldq_tail
  var st_enq_idx = stq_tail
  var actual_ld_enq_idx = (ld_enq_idx)
  var actual_st_enq_idx = (st_enq_idx)

  val dis_ldq_oh = Reg(Vec(numLdqEntries, Bool()))
  val dis_stq_oh = Reg(Vec(numStqEntries, Bool()))
  dis_ldq_oh := 0.U(numLdqEntries.W).asBools
  dis_stq_oh := 0.U(numStqEntries.W).asBools
  val dis_uops   = Reg(Vec(coreWidth, Valid(new MicroOp)))

  val live_store_mask = stq_valid.asUInt | dis_stq_oh.asUInt
  var next_live_store_mask = Mux(clear_store, live_store_mask & ~(1.U << stq_head),
                                              live_store_mask)


  var ldq_tail_oh = UIntToOH(GetRealLSQIdx(ldq_tail))(numLdqEntries-1,0)
  val ldq_valids  = ldq_valid.asUInt | dis_ldq_oh.asUInt
  var stq_tail_oh = UIntToOH(GetRealLSQIdx(stq_tail))(numStqEntries-1,0)
  val stq_valids  = stq_valid.asUInt | dis_stq_oh.asUInt

  val stq_nonempty = stq_valid.reduce(_||_)

  for (w <- 0 until coreWidth)
  {

    val ldq_full = (ldq_tail_oh & ldq_valids).orR
    io.core.ldq_full(w)    := ldq_full
    io.core.dis_ldq_idx(w) := actual_ld_enq_idx

    val stq_full = (stq_tail_oh & stq_valids).orR
    io.core.stq_full(w)    := stq_full
    io.core.dis_stq_idx(w) := actual_st_enq_idx

    val dis_ld_val = io.core.dis_uops(w).valid && io.core.dis_uops(w).bits.uses_ldq && !io.core.dis_uops(w).bits.exception
    val dis_st_val = io.core.dis_uops(w).valid && io.core.dis_uops(w).bits.uses_stq && !io.core.dis_uops(w).bits.exception

    dis_uops(w).valid := dis_ld_val || dis_st_val
    dis_uops(w).bits  := io.core.dis_uops(w).bits

    when (dis_ld_val) {
      dis_ldq_oh(actual_ld_enq_idx) := true.B
      ldq_next_stq_idx           (actual_ld_enq_idx)  := st_enq_idx
      assert (actual_ld_enq_idx === io.core.dis_uops(w).bits.ldq_idx, "[lsu] mismatch enq load tag.")
      assert (!ldq_valid(actual_ld_enq_idx), "[lsu] Enqueuing uop is overwriting ldq entries")
    }
    when (dis_st_val) {
      dis_stq_oh(actual_st_enq_idx) := true.B
      stq_next_ldq_idx (actual_st_enq_idx) := ld_enq_idx
      assert (actual_st_enq_idx === io.core.dis_uops(w).bits.stq_idx, "[lsu] mismatch enq store tag.")
      assert (!stq_valid(actual_st_enq_idx), "[lsu] Enqueuing uop is overwriting stq entries")
    }
    assert(!(dis_ld_val && dis_st_val), "[lsu] cannot enqueue load and store at the same time into a single pipe!")
    when (dis_uops(w).valid && dis_uops(w).bits.uses_ldq) {
      val ldq_idx = dis_uops(w).bits.ldq_idx
      ldq_valid          (ldq_idx)       := !IsKilledByBranch(io.core.brupdate, io.core.exception, dis_uops(w).bits)
      ldq_uop            (ldq_idx)       := UpdateBrMask(io.core.brupdate, dis_uops(w).bits)
      ldq_addr           (ldq_idx).valid := false.B
      ldq_executed       (ldq_idx)       := false.B
      ldq_will_succeed   (ldq_idx)       := false.B
      ldq_order_fail     (ldq_idx)       := false.B
      ldq_observed       (ldq_idx)       := false.B
      ldq_forward_std_val(ldq_idx)       := false.B
    }
    when (dis_uops(w).valid && dis_uops(w).bits.uses_stq) {
      val stq_idx = dis_uops(w).bits.stq_idx
      stq_valid          (stq_idx)        := !IsKilledByBranch(io.core.brupdate, io.core.exception, dis_uops(w).bits)
      stq_uop            (stq_idx)        := UpdateBrMask(io.core.brupdate, dis_uops(w).bits)
      stq_addr           (stq_idx).valid  := false.B
      stq_data           (stq_idx).valid  := false.B
      stq_committed      (stq_idx)        := false.B
      stq_can_execute    (stq_idx)        := false.B
      stq_succeeded      (stq_idx)        := false.B
      stq_cleared        (stq_idx)        := false.B
    }

    ld_enq_idx = Mux(dis_ld_val, WrapIncWCarry(ld_enq_idx, numLdqEntries),
                                 ld_enq_idx)
    actual_ld_enq_idx = (ld_enq_idx)

    next_live_store_mask = Mux(dis_st_val, next_live_store_mask | (1.U << st_enq_idx),
                                           next_live_store_mask)
    st_enq_idx = Mux(dis_st_val, WrapIncWCarry(st_enq_idx, numStqEntries),
                                 st_enq_idx)
    actual_st_enq_idx = (st_enq_idx)

    assert(!(dis_ld_val && dis_st_val), "A UOP is trying to go into both the LDQ and the STQ")

    ldq_tail_oh = Mux((io.core.dis_uops(w).bits.uses_ldq && !io.core.dis_uops(w).bits.exception) || !enableCompactingLSUDuringDispatch.B,
      RotateL1(ldq_tail_oh), ldq_tail_oh)
    stq_tail_oh = Mux((io.core.dis_uops(w).bits.uses_stq && !io.core.dis_uops(w).bits.exception) || !enableCompactingLSUDuringDispatch.B,
      RotateL1(stq_tail_oh), stq_tail_oh)
  }

  ldq_tail := ld_enq_idx
  stq_tail := st_enq_idx

  io.dmem.force_order   := io.core.fence_dmem
  io.core.fencei_rdy    := !stq_nonempty && io.dmem.ordered


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // Execute stage (access TLB, send requests to Memory)
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // We can only report 1 exception per cycle.
  // Just be sure to report the youngest one
  val mem_xcpt_valid  = Wire(Bool())
  val mem_xcpt_cause  = Wire(UInt())
  val mem_xcpt_uop    = Wire(new MicroOp)
  val mem_xcpt_vaddr  = Wire(UInt())


  //---------------------------------------
  // Can-fire logic and wakeup/retry select
  //
  // First we determine what operations are waiting to execute.
  // These are the "can_fire"/"will_fire" signals

  val will_fire_load_agen_exec  = Wire(Vec(lsuWidth, Bool()))
  val will_fire_load_agen         = Wire(Vec(lsuWidth, Bool()))
  val will_fire_store_agen        = Wire(Vec(lsuWidth, Bool()))
  val will_fire_sfence            = Wire(Vec(lsuWidth, Bool()))
  val will_fire_hella_incoming    = Wire(Vec(lsuWidth, Bool()))
  val will_fire_hella_wakeup      = Wire(Vec(lsuWidth, Bool()))
  val will_fire_release           = Wire(Vec(lsuWidth, Bool()))
  val will_fire_load_retry        = Wire(Vec(lsuWidth, Bool()))
  val will_fire_store_retry       = Wire(Vec(lsuWidth, Bool()))
  val will_fire_store_commit_fast = Wire(Vec(lsuWidth, Bool()))
  val will_fire_store_commit_slow = Wire(Vec(lsuWidth, Bool()))
  val will_fire_load_wakeup       = Wire(Vec(lsuWidth, Bool()))

  val agen = io.core.agen
  // -------------------------------
  // Assorted signals for scheduling

  // Don't wakeup a load if we just sent it last cycle or two cycles ago
  // The block_load_mask may be wrong, but the executing_load mask must be accurate
  val block_load_mask    = WireInit(VecInit((0 until numLdqEntries).map(x=>false.B)))
  val p1_block_load_mask = RegNext(block_load_mask)
  val p2_block_load_mask = RegNext(p1_block_load_mask)

  // Prioritize emptying the store queue when it is almost full
  val stq_tail_plus   = WrapAddWCarryWidth(stq_tail, (2*coreWidth).U, 2*numStqEntries, (1+stqAddrSz))
  val stq_almost_full = SafeRegNext(IsOlderLSU(stq_head, stq_tail_plus, stq_tail))

  // The store at the commit head needs the DCache to appear ordered
  // Delay firing load wakeups and retries now
  val store_needs_order = WireInit(false.B)

  val ldq_incoming_idx = widthMap(i => agen(i).bits.uop.ldq_idx)
  val ldq_incoming_e   = widthMap(i => WireInit(ldq_read(ldq_incoming_idx(i))))

  val stq_incoming_idx = widthMap(i => agen(i).bits.uop.stq_idx)
  val stq_incoming_e   = widthMap(i => WireInit(stq_read(stq_incoming_idx(i))))

  val ldq_wakeup_idx = SafeRegNext(LSUAgePriorityEncoder((0 until numLdqEntries).map(i=> {
    val block = block_load_mask(i) || p1_block_load_mask(i)
    ldq_addr(i).valid && !ldq_executed(i) && !ldq_succeeded(i) && !ldq_addr_is_virtual(i) && !block
  }), ldq_head))
  val ldq_wakeup_e   = WireInit(ldq_read(ldq_wakeup_idx))


  // Enqueue into the retry queue
  val ldq_enq_retry_idx = Reg(UInt((1+ldqAddrSz).W))
  ldq_enq_retry_idx := LSUAgePriorityEncoder((0 until numLdqEntries).map(i => {
    ldq_addr(i).valid && ldq_addr_is_virtual(i) && (i.U =/= GetRealLSQIdx(ldq_enq_retry_idx))
  }), ldq_head)
  val ldq_enq_retry_e = WireInit(ldq_read(ldq_enq_retry_idx))

  val stq_enq_retry_idx = Reg(UInt((1+stqAddrSz).W))
  stq_enq_retry_idx := LSUAgePriorityEncoder((0 until numStqEntries).map(i => {
    stq_addr(i).valid && stq_addr_is_virtual(i) && (i.U =/= GetRealLSQIdx(stq_enq_retry_idx))
  }), stq_commit_head)
  val stq_enq_retry_e   = WireInit(stq_read(stq_enq_retry_idx))

  val can_enq_load_retry    = (ldq_enq_retry_e.valid                            &&
                               ldq_enq_retry_e.bits.addr.valid                  &&
                               ldq_enq_retry_e.bits.addr_is_virtual)
  val can_enq_store_retry   = (stq_enq_retry_e.valid                            &&
                               stq_enq_retry_e.bits.addr.valid                  &&
                               stq_enq_retry_e.bits.addr_is_virtual)

  val retry_queue = Module(new BranchKillableQueue(new MemGen, 8))
  retry_queue.io.brupdate := io.core.brupdate
  retry_queue.io.flush    := io.core.exception

  retry_queue.io.enq.valid     := can_enq_store_retry || can_enq_load_retry
  retry_queue.io.enq.bits      := DontCare
  retry_queue.io.enq.bits.data := Mux(can_enq_store_retry, stq_enq_retry_e.bits.addr.bits, ldq_enq_retry_e.bits.addr.bits)
  retry_queue.io.enq.bits.uop  := Mux(can_enq_store_retry, stq_enq_retry_e.bits.uop      , ldq_enq_retry_e.bits.uop)
  retry_queue.io.enq.bits.uop.uses_ldq := can_enq_load_retry && !can_enq_store_retry
  retry_queue.io.enq.bits.uop.ldq_idx  := ldq_enq_retry_idx
  retry_queue.io.enq.bits.uop.uses_stq := can_enq_store_retry
  retry_queue.io.enq.bits.uop.stq_idx  := stq_enq_retry_idx

  when (can_enq_store_retry && retry_queue.io.enq.fire) {
    stq_addr(stq_enq_retry_idx).valid := false.B
  } .elsewhen (can_enq_load_retry && retry_queue.io.enq.fire) {
    ldq_addr(ldq_enq_retry_idx).valid := false.B
  }

  val ldq_retry_idx = retry_queue.io.deq.bits.uop.ldq_idx
  val stq_retry_idx = retry_queue.io.deq.bits.uop.stq_idx
  retry_queue.io.deq.ready := will_fire_load_retry.reduce(_||_) || will_fire_store_retry.reduce(_||_)

  val stq_execute_queue_flush = WireInit(false.B)
  val stq_execute_queue = withReset(reset.asBool || stq_execute_queue_flush) {
    Module(new Queue(new STQEntry, 4))
  }
  val stq_commit_e = stq_execute_queue.io.deq
  
  val stq_enq_e = WireInit(stq_read(stq_execute_head))

  stq_execute_queue.io.enq.bits := stq_enq_e.bits
  stq_execute_queue.io.enq.bits.uop.stq_idx := stq_execute_head
  stq_execute_queue.io.deq.ready := will_fire_store_commit_fast.reduce(_||_) || will_fire_store_commit_slow.reduce(_||_)

  val can_enq_store_execute = (
    stq_enq_e.valid &&
    stq_enq_e.bits.addr.valid &&
    stq_enq_e.bits.data.valid &&
   !stq_enq_e.bits.addr_is_virtual &&
   !stq_enq_e.bits.uop.exception &&
   !stq_enq_e.bits.uop.is_fence &&
   (stq_enq_e.bits.committed || stq_enq_e.bits.uop.is_amo)
  )
  stq_execute_queue.io.enq.valid := can_enq_store_execute

  when (can_enq_store_execute && stq_execute_queue.io.enq.fire) {
    stq_execute_head := WrapIncWCarry(stq_execute_head, numStqEntries)
  }

  // -----------------------
  // Determine what can fire

  // Can we fire a incoming load
  val can_fire_load_agen_exec = widthMap(w => agen(w).valid && agen(w).bits.uop.uses_ldq)
  val can_fire_load_agen      = can_fire_load_agen_exec

  // Can we fire an incoming store addrgen + store datagen
  val can_fire_store_agen     = widthMap(w => agen(w).valid && agen(w).bits.uop.uses_stq)


  // Can we fire an incoming sfence
  val can_fire_sfence        = widthMap(w => io.core.sfence.valid)

  // Can we fire a request from dcache to release a line
  // This needs to go through LDQ search to mark loads as dangerous
  val can_fire_release       = widthMap(w => (w == lsuWidth-1).B && io.dmem.release.valid)
  io.dmem.release.ready     := will_fire_release.reduce(_||_)

  // Can we retry a load that missed in the TLB
  val can_fire_load_retry    = widthMap(w =>
                               ( retry_queue.io.deq.valid                     &&
                                 retry_queue.io.deq.bits.uop.uses_ldq         &&
                                !RegNext(store_needs_order)                   &&
                                (w == lsuWidth-1).B))

  // Can we retry a store addrgen that missed in the TLB
  val can_fire_store_retry   = widthMap(w =>
                               ( retry_queue.io.deq.valid                     &&
                                 retry_queue.io.deq.bits.uop.uses_stq         &&
                                 (w == lsuWidth-1).B))

  // Can we commit a store
  val can_fire_store_commit_slow  = widthMap(w =>
                                    ( stq_commit_e.valid                           &&
                                     !mem_xcpt_valid                               &&
                                      (w == 0).B))

  val can_fire_store_commit_fast = widthMap(w => can_fire_store_commit_slow(w) && stq_almost_full)

  // Can we wakeup a load that was nack'd
  val block_load_wakeup = WireInit(false.B)
  val can_fire_load_wakeup = widthMap(w =>
                             ( ldq_wakeup_e.valid                                      &&
                               ldq_wakeup_e.bits.addr.valid                            &&
                              !ldq_wakeup_e.bits.succeeded                             &&
                              !ldq_wakeup_e.bits.addr_is_virtual                       &&
                              !ldq_wakeup_e.bits.executed                              &&
                              !ldq_wakeup_e.bits.order_fail                            &&
                              !p1_block_load_mask(ldq_wakeup_idx)                      &&
                              !p2_block_load_mask(ldq_wakeup_idx)                      &&
                              !RegNext(store_needs_order)                              &&
                              !block_load_wakeup                                       &&
                              (w == lsuWidth-1).B                                      &&
                              (!ldq_wakeup_e.bits.addr_is_uncacheable || (io.core.commit_load_at_rob_head &&
                                                                          ldq_head === ldq_wakeup_idx &&
                                                                          IdxAgeYe(stq_head, ldq_wakeup_e.bits.next_stq_idx)))))


  // Can we fire an incoming hellacache request
  val can_fire_hella_incoming  = WireInit(widthMap(w => false.B)) // This is assigned to in the hellashim ocntroller

  // Can we fire a hellacache request that the dcache nack'd
  val can_fire_hella_wakeup    = WireInit(widthMap(w => false.B)) // This is assigned to in the hellashim controller

  //---------------------------------------------------------
  // Controller logic. Arbitrate which request actually fires

  val exe_tlb_valid = Wire(Vec(lsuWidth, Bool()))
  for (w <- 0 until lsuWidth) {
    var tlb_avail  = true.B
    var dc_avail   = true.B
    var lcam_avail = true.B

    def lsu_sched(can_fire: Bool, uses_tlb:Boolean, uses_dc:Boolean, uses_lcam: Boolean): Bool = {
      val will_fire = can_fire && !(uses_tlb.B && !tlb_avail) &&
                                  !(uses_lcam.B && !lcam_avail) &&
                                  !(uses_dc.B && !dc_avail)
      tlb_avail  = tlb_avail  && !(will_fire && uses_tlb.B)
      lcam_avail = lcam_avail && !(will_fire && uses_lcam.B)
      dc_avail   = dc_avail   && !(will_fire && uses_dc.B)
      dontTouch(will_fire) // dontTouch these so we can inspect the will_fire signals
      will_fire
    }

    // The order of these statements is the priority
    // Some restrictions
    //  - Incoming ops must get precedence, can't backpresure memaddrgen
    //  - Incoming hellacache ops must get precedence over retrying ops (PTW must get precedence over retrying translation)
    // Notes on performance
    //  - Prioritize releases, this speeds up cache line writebacks and refills
    //  - Store commits are lowest priority, since they don't "block" younger instructions unless stq fills up
    will_fire_sfence           (w) := lsu_sched(can_fire_sfence           (w) , true , false, false) // TLB ,    ,
    will_fire_store_commit_fast(w) := lsu_sched(can_fire_store_commit_fast(w) , false, true , false) //     , DC          If store queue is filling up, prioritize draining it
    will_fire_load_agen_exec   (w) := lsu_sched(can_fire_load_agen_exec   (w) , true , true , true ) // TLB , DC , LCAM   Normally fire loads as soon as translation completes
    will_fire_load_agen        (w) := lsu_sched(can_fire_load_agen        (w) , true , false, true ) // TLB ,    , LCAM   If we are draining stores, still translate the loads
    will_fire_store_agen       (w) := lsu_sched(can_fire_store_agen       (w) , true , false, true ) // TLB ,    , LCAM
    will_fire_release          (w) := lsu_sched(can_fire_release          (w) , false, false, true ) //            LCAM
    will_fire_hella_incoming   (w) := lsu_sched(can_fire_hella_incoming   (w) , true , true , false) // TLB , DC
    will_fire_hella_wakeup     (w) := lsu_sched(can_fire_hella_wakeup     (w) , false, true , false) //     , DC
    will_fire_store_retry      (w) := lsu_sched(can_fire_store_retry      (w) , true , false, true ) // TLB ,    , LCAM
    will_fire_load_retry       (w) := lsu_sched(can_fire_load_retry       (w) , true , true , true ) // TLB , DC , LCAM
    will_fire_load_wakeup      (w) := lsu_sched(can_fire_load_wakeup      (w) , false, true , true ) //     , DC , LCAM
    will_fire_store_commit_slow(w) := lsu_sched(can_fire_store_commit_slow(w) , false, true , false) //     , DC


    assert(!(agen(w).valid && !(will_fire_load_agen_exec(w) || will_fire_load_agen(w) || will_fire_store_agen(w))))

    when (will_fire_load_wakeup(w)) {
      block_load_mask(ldq_wakeup_idx)           := true.B
    } .elsewhen (will_fire_load_agen(w) || will_fire_load_agen_exec(w)) {
      block_load_mask(agen(w).bits.uop.ldq_idx) := true.B
    } .elsewhen (will_fire_load_retry(w)) {
      block_load_mask(ldq_retry_idx)            := true.B
    }
    exe_tlb_valid(w) := !tlb_avail
  }
  assert((lsuWidth == 1).B ||
    (!will_fire_hella_incoming.reduce(_&&_)    &&
     !will_fire_hella_wakeup.reduce(_&&_)      &&
     !will_fire_load_retry.reduce(_&&_)        &&
     !will_fire_store_retry.reduce(_&&_)       &&
     !will_fire_store_commit_fast.reduce(_&&_) &&
     !will_fire_store_commit_slow.reduce(_&&_) &&
     !will_fire_load_wakeup.reduce(_&&_)),
    "Some operations is proceeding down multiple pipes")

  require(lsuWidth <= 2)

  //--------------------------------------------
  // TLB Access

  assert(!(hella_state =/= h_ready && hella_req.cmd === rocket.M_SFENCE),
    "SFENCE through hella interface not supported")

  val exe_tlb_uop = widthMap(w =>
                    Mux(will_fire_load_agen_exec(w) ||
                        will_fire_load_agen     (w)  , ldq_incoming_e(w).bits.uop,
                    Mux(will_fire_store_agen    (w)  , stq_incoming_e(w).bits.uop,
                    Mux(will_fire_load_retry    (w) ||
                        will_fire_store_retry   (w)  , retry_queue.io.deq.bits.uop,
                    Mux(will_fire_hella_incoming(w)  , 0.U.asTypeOf(new MicroOp),
                                                       0.U.asTypeOf(new MicroOp))))))

  val exe_tlb_vaddr = widthMap(w =>
                    Mux(will_fire_load_agen_exec(w) ||
                        will_fire_load_agen     (w) ||
                        will_fire_store_agen    (w)  , agen(w).bits.data,
                    Mux(will_fire_sfence        (w)  , io.core.sfence.bits.addr,
                    Mux(will_fire_load_retry    (w) ||
                        will_fire_store_retry   (w)  , retry_queue.io.deq.bits.data,
                    Mux(will_fire_hella_incoming(w)  , hella_req.addr,
                                                       0.U)))))

  val exe_sfence = io.core.sfence

  val exe_size   = widthMap(w =>
                   Mux(will_fire_load_agen_exec(w) ||
                       will_fire_load_agen     (w) ||
                       will_fire_store_agen    (w) ||
                       will_fire_load_retry    (w) ||
                       will_fire_store_retry   (w)  , exe_tlb_uop(w).mem_size,
                   Mux(will_fire_hella_incoming(w)  , hella_req.size,
                                                      0.U)))
  val exe_cmd    = widthMap(w =>
                   Mux(will_fire_load_agen_exec(w) ||
                       will_fire_load_agen     (w) ||
                       will_fire_store_agen    (w) ||
                       will_fire_load_retry    (w) ||
                       will_fire_store_retry   (w)  , exe_tlb_uop(w).mem_cmd,
                   Mux(will_fire_hella_incoming(w)  , hella_req.cmd,
                   Mux(will_fire_sfence        (w)  , rocket.M_SFENCE,
                                                      0.U))))

  val exe_passthr= widthMap(w =>
                   Mux(will_fire_hella_incoming(w)  , hella_req.phys,
                                                      false.B))
  val exe_kill   = widthMap(w =>
                   Mux(will_fire_hella_incoming(w)  , io.hellacache.s1_kill,
                                                      false.B))
  val bkptu = Seq.fill(lsuWidth) { Module(new rocket.BreakpointUnit(nBreakpoints)) }
  for (w <- 0 until lsuWidth) {
    dtlb.io.req(w).valid            := exe_tlb_valid(w)
    dtlb.io.req(w).bits.vaddr       := exe_tlb_vaddr(w)
    dtlb.io.req(w).bits.size        := exe_size(w)
    dtlb.io.req(w).bits.cmd         := exe_cmd(w)
    dtlb.io.req(w).bits.passthrough := exe_passthr(w)
    dtlb.io.req(w).bits.prv         := io.ptw.status.prv
    dtlb.io.req(w).bits.v           := io.ptw.status.v

    bkptu(w).io.status := io.core.status
    bkptu(w).io.bp     := io.core.bp
    bkptu(w).io.pc     := DontCare
    bkptu(w).io.ea     := exe_tlb_vaddr(w)
    bkptu(w).io.mcontext := io.core.mcontext
    bkptu(w).io.scontext := io.core.scontext
  }
  dtlb.io.kill                      := exe_kill.reduce(_||_)
  dtlb.io.sfence                    := exe_sfence

  // exceptions
  val ma_ld  = widthMap(w => dtlb.io.resp(w).ma.ld && exe_tlb_uop(w).uses_ldq)
  val ma_st  = widthMap(w => dtlb.io.resp(w).ma.st && exe_tlb_uop(w).uses_stq && !exe_tlb_uop(w).is_fence)
  val pf_ld  = widthMap(w => dtlb.io.resp(w).pf.ld && exe_tlb_uop(w).uses_ldq)
  val pf_st  = widthMap(w => dtlb.io.resp(w).pf.st && exe_tlb_uop(w).uses_stq)
  val ae_ld  = widthMap(w => dtlb.io.resp(w).ae.ld && exe_tlb_uop(w).uses_ldq)
  val ae_st  = widthMap(w => dtlb.io.resp(w).ae.st && exe_tlb_uop(w).uses_stq)
  val dbg_bp = widthMap(w => bkptu(w).io.debug_st && ((exe_tlb_uop(w).uses_ldq && bkptu(w).io.debug_ld) ||
                                                      (exe_tlb_uop(w).uses_stq && bkptu(w).io.debug_st && !exe_tlb_uop(w).is_fence)))
  val bp     = widthMap(w => bkptu(w).io.debug_st && ((exe_tlb_uop(w).uses_ldq && bkptu(w).io.xcpt_ld) ||
                                                      (exe_tlb_uop(w).uses_stq && bkptu(w).io.xcpt_st && !exe_tlb_uop(w).is_fence)))


  // TODO check for xcpt_if and verify that never happens on non-speculative instructions.
  val mem_xcpt_valids = RegNext(widthMap(w =>
                     exe_tlb_valid(w) &&
                     (pf_ld(w) || pf_st(w) || ae_ld(w) || ae_st(w) || ma_ld(w) || ma_st(w) || dbg_bp(w) || bp(w)) &&
                     !IsKilledByBranch(io.core.brupdate, io.core.exception, exe_tlb_uop(w))))
  val mem_xcpt_uops   = RegNext(widthMap(w => UpdateBrMask(io.core.brupdate, exe_tlb_uop(w))))
  val mem_xcpt_causes = RegNext(widthMap(w =>
    Mux(dbg_bp(w), rocket.CSR.debugTriggerCause.U,
    Mux(bp    (w), rocket.Causes.breakpoint.U,
    Mux(ma_st (w), rocket.Causes.misaligned_store.U,
    Mux(ma_ld (w), rocket.Causes.misaligned_load.U,
    Mux(pf_st (w), rocket.Causes.store_page_fault.U,
    Mux(pf_ld (w), rocket.Causes.load_page_fault.U,
    Mux(ae_st (w), rocket.Causes.store_access.U,
    Mux(ae_ld (w), rocket.Causes.load_access.U, 0.U))))))))))

  val mem_xcpt_vaddrs = RegNext(exe_tlb_vaddr)

  for (w <- 0 until lsuWidth) {
    assert (!(dtlb.io.req(w).valid && exe_tlb_uop(w).is_fence), "Fence is pretending to talk to the TLB")
  }

  mem_xcpt_valid := mem_xcpt_valids.reduce(_||_)
  mem_xcpt_cause := mem_xcpt_causes(0)
  mem_xcpt_uop   := mem_xcpt_uops(0)
  mem_xcpt_vaddr := mem_xcpt_vaddrs(0)
  var xcpt_found = mem_xcpt_valids(0)
  var oldest_xcpt_rob_idx = mem_xcpt_uops(0).rob_idx
  for (w <- 1 until lsuWidth) {
    val is_older = WireInit(false.B)
    when (mem_xcpt_valids(w) &&
      (IsOlder(mem_xcpt_uops(w).rob_idx, oldest_xcpt_rob_idx, io.core.rob_head_idx) || !xcpt_found)) {
      is_older := true.B
      mem_xcpt_cause := mem_xcpt_causes(w)
      mem_xcpt_uop   := mem_xcpt_uops(w)
      mem_xcpt_vaddr := mem_xcpt_vaddrs(w)
    }
    xcpt_found = xcpt_found || mem_xcpt_valids(w)
    oldest_xcpt_rob_idx = Mux(is_older, mem_xcpt_uops(w).rob_idx, oldest_xcpt_rob_idx)
  }

  val exe_tlb_miss  = widthMap(w => dtlb.io.req(w).valid && (dtlb.io.resp(w).miss || !dtlb.io.req(w).ready))
  val exe_tlb_paddr = widthMap(w => Cat(dtlb.io.resp(w).paddr(paddrBits-1,corePgIdxBits),
                                        exe_tlb_vaddr(w)(corePgIdxBits-1,0)))
  val exe_tlb_uncacheable = widthMap(w => !(dtlb.io.resp(w).cacheable))

  for (w <- 0 until lsuWidth) {
    assert (exe_tlb_paddr(w) === dtlb.io.resp(w).paddr, "[lsu] paddrs should match.")

    when (mem_xcpt_valids(w))
    {
      assert(RegNext(will_fire_load_agen_exec(w) || will_fire_load_agen(w) || will_fire_store_agen(w) ||
        will_fire_load_retry(w) || will_fire_store_retry(w)))
      // Technically only faulting AMOs need this
      assert(mem_xcpt_uops(w).uses_ldq ^ mem_xcpt_uops(w).uses_stq)
      when (mem_xcpt_uops(w).uses_ldq)
      {
        ldq_uop(mem_xcpt_uops(w).ldq_idx).exception := true.B
      }
        .otherwise
      {
        stq_uop(mem_xcpt_uops(w).stq_idx).exception := true.B
      }
    }
  }

  val exe_agen_killed = widthMap(w => IsKilledByBranch(io.core.brupdate, io.core.exception, agen(w).bits.uop))


  //------------------------------
  // Issue Someting to Memory
  //
  // A memory op can come from many different places
  // The address either was freshly translated, or we are
  // reading a physical address from the LDQ,STQ, or the HellaCache adapter


  // defaults
  io.dmem.brupdate       := io.core.brupdate
  io.dmem.exception      := io.core.exception
  io.dmem.rob_head_idx   := io.core.rob_head_idx
  io.dmem.rob_pnr_idx    := io.core.rob_pnr_idx

  val dmem_req = Wire(Vec(lsuWidth, Valid(new BoomDCacheReq)))
  io.dmem.req.valid := dmem_req.map(_.valid).reduce(_||_)
  io.dmem.req.bits  := dmem_req
  val dmem_req_fire = widthMap(w => dmem_req(w).valid && io.dmem.req.fire)

  val s0_executing_loads = WireInit(VecInit((0 until numLdqEntries).map(x=>false.B)))
  val s0_kills = Wire(Vec(lsuWidth, Bool()))

  for (w <- 0 until lsuWidth) {
    dmem_req(w).valid := false.B
    dmem_req(w).bits.uop   := NullMicroOp
    dmem_req(w).bits.addr  := 0.U
    dmem_req(w).bits.data  := 0.U
    dmem_req(w).bits.is_hella := false.B

    s0_kills(w) := false.B
    io.dmem.s1_kill(w) := RegNext(s0_kills(w) && dmem_req_fire(w))

    when (will_fire_load_agen_exec(w)) {
      dmem_req(w).valid      := true.B
      dmem_req(w).bits.addr  := exe_tlb_paddr(w)
      dmem_req(w).bits.uop   := exe_tlb_uop(w)

      s0_kills(w)            := exe_tlb_miss(w) || exe_tlb_uncacheable(w) || ma_ld(w) || ae_ld(w) || pf_ld(w)
      s0_executing_loads(ldq_incoming_idx(w)) := dmem_req_fire(w) && !s0_kills(w)

      assert(!ldq_incoming_e(w).bits.executed)
    } .elsewhen (will_fire_load_retry(w)) {
      dmem_req(w).valid      := true.B
      dmem_req(w).bits.addr  := exe_tlb_paddr(w)
      dmem_req(w).bits.uop   := exe_tlb_uop(w)

      s0_kills(w) := exe_tlb_miss(w) || exe_tlb_uncacheable(w) || ma_ld(w) || ae_ld(w) || pf_ld(w)
      s0_executing_loads(ldq_retry_idx) := dmem_req_fire(w) && !s0_kills(w)
    } .elsewhen (will_fire_store_commit_slow(w) || will_fire_store_commit_fast(w)) {
      dmem_req(w).valid         := true.B
      dmem_req(w).bits.addr     := stq_commit_e.bits.addr.bits
      dmem_req(w).bits.data     := (new freechips.rocketchip.rocket.StoreGen(
                                    stq_commit_e.bits.uop.mem_size, 0.U,
                                    stq_commit_e.bits.data.bits,
                                    coreDataBytes)).data
      dmem_req(w).bits.uop      := stq_commit_e.bits.uop

      when (!dmem_req_fire(w)) {
        stq_execute_queue_flush := true.B
        stq_execute_head := stq_commit_e.bits.uop.stq_idx
      }

      stq_succeeded(stq_commit_e.bits.uop.stq_idx) := false.B
    } .elsewhen (will_fire_load_wakeup(w)) {
      dmem_req(w).valid      := true.B
      dmem_req(w).bits.addr  := ldq_wakeup_e.bits.addr.bits
      dmem_req(w).bits.uop   := ldq_wakeup_e.bits.uop

      s0_executing_loads(ldq_wakeup_idx) := dmem_req_fire(w)

      assert(!ldq_wakeup_e.bits.executed && !ldq_wakeup_e.bits.addr_is_virtual)
    } .elsewhen (will_fire_hella_incoming(w)) {
      assert(hella_state === h_s1)

      dmem_req(w).valid               := !io.hellacache.s1_kill && (!exe_tlb_miss(w) || hella_req.phys)
      dmem_req(w).bits.addr           := exe_tlb_paddr(w)
      dmem_req(w).bits.data           := (new freechips.rocketchip.rocket.StoreGen(
        hella_req.size, 0.U,
        io.hellacache.s1_data.data,
        coreDataBytes)).data
      dmem_req(w).bits.uop.mem_cmd    := hella_req.cmd
      dmem_req(w).bits.uop.mem_size   := hella_req.size
      dmem_req(w).bits.uop.mem_signed := hella_req.signed
      dmem_req(w).bits.is_hella       := true.B

      hella_paddr := exe_tlb_paddr(w)
    }
      .elsewhen (will_fire_hella_wakeup(w))
    {
      assert(hella_state === h_replay)
      dmem_req(w).valid               := true.B
      dmem_req(w).bits.addr           := hella_paddr
      dmem_req(w).bits.data           := (new freechips.rocketchip.rocket.StoreGen(
        hella_req.size, 0.U,
        hella_data.data,
        coreDataBytes)).data
      dmem_req(w).bits.uop.mem_cmd    := hella_req.cmd
      dmem_req(w).bits.uop.mem_size   := hella_req.size
      dmem_req(w).bits.uop.mem_signed := hella_req.signed
      dmem_req(w).bits.is_hella       := true.B
    }

    //-------------------------------------------------------------
    // Write Addr into the LAQ/SAQ
    when (will_fire_load_agen(w) || will_fire_load_agen_exec(w) || will_fire_load_retry(w))
    {
      val ldq_idx = Mux(will_fire_load_agen(w) || will_fire_load_agen_exec(w), ldq_incoming_idx(w), ldq_retry_idx)
      ldq_addr               (ldq_idx).valid  := !exe_agen_killed(w) || will_fire_load_retry(w)
      ldq_addr               (ldq_idx).bits   := Mux(exe_tlb_miss(w), exe_tlb_vaddr(w), exe_tlb_paddr(w))
      ldq_ld_byte_mask       (ldq_idx)        := GenByteMask(exe_tlb_vaddr(w), exe_tlb_uop(w).mem_size)
      ldq_uop                (ldq_idx).pdst   := exe_tlb_uop(w).pdst
      ldq_addr_is_virtual    (ldq_idx)        := exe_tlb_miss(w)
      ldq_addr_is_uncacheable(ldq_idx)        := exe_tlb_uncacheable(w) && !exe_tlb_miss(w)

      assert(!ldq_addr(ldq_idx).valid,
        "[lsu] Translating load is overwriting a valid address")
    }

    when (will_fire_store_agen(w) || will_fire_store_retry(w))
    {
      val stq_idx = Mux(will_fire_store_agen(w),
        stq_incoming_idx(w), stq_retry_idx)

      stq_addr           (stq_idx).valid := (!exe_agen_killed(w) || will_fire_store_retry(w)) && !pf_st(w) // Prevent AMOs from executing!
      stq_addr           (stq_idx).bits  := Mux(exe_tlb_miss(w), exe_tlb_vaddr(w), exe_tlb_paddr(w))
      stq_uop            (stq_idx).pdst  := exe_tlb_uop(w).pdst // Needed for AMOs
      stq_addr_is_virtual(stq_idx)       := exe_tlb_miss(w)

      assert(!stq_addr(stq_idx).valid,
        "[lsu] Translating store is overwriting a valid address")

    }

  }

  //-------------------------------------------------------------
  // Write data into the STQ
  for (dgen <- io.core.dgen) {
    when (dgen.valid) {
      val sidx = dgen.bits.uop.stq_idx
      //val stq_e = WireInit(stq(sidx))
      stq_data(sidx).valid := true.B
      stq_data(sidx).bits  := dgen.bits.data
      assert(!stq_data(sidx).valid || (stq_data(sidx).bits === dgen.bits.data))
    }
  }

  require (xLen >= fLen) // for correct SDQ size



  //-------------------------------------------------------------
  // Speculative wakeup if loadUseDelay == 4
  val wakeupArbs = Seq.fill(lsuWidth) { Module(new Arbiter(new Wakeup, 2)) }
  for (w <- 0 until lsuWidth) {
    wakeupArbs(w).io.out.ready := true.B
    wakeupArbs(w).io.in := DontCare
    io.core.iwakeups(w) := wakeupArbs(w).io.out

    if (enableFastLoadUse) {
      wakeupArbs(w).io.in(1).valid := (will_fire_load_agen_exec(w)                     &&
                                       dmem_req_fire(w)                                &&
                                      !agen(w).bits.uop.fp_val)
      wakeupArbs(w).io.in(1).bits.uop := agen(w).bits.uop
      wakeupArbs(w).io.in(1).bits.bypassable := true.B
      wakeupArbs(w).io.in(1).bits.speculative_mask := 0.U
      wakeupArbs(w).io.in(1).bits.rebusy := false.B
    }
  }


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // Cache Access Cycle (Mem)
  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // Note the DCache may not have accepted our request

  val fired_load_agen_exec = widthMap(w => RegNext(will_fire_load_agen_exec(w) && !exe_agen_killed(w)))
  val fired_load_agen      = widthMap(w => RegNext(will_fire_load_agen     (w) && !exe_agen_killed(w)))
  val fired_store_agen     = widthMap(w => RegNext(will_fire_store_agen    (w) && !exe_agen_killed(w)))
  val fired_sfence         = RegNext(will_fire_sfence)
  val fired_release        = RegNext(will_fire_release)
  val fired_load_retry     = widthMap(w => RegNext(will_fire_load_retry   (w) && !IsKilledByBranch(io.core.brupdate, io.core.exception, retry_queue.io.deq.bits.uop)))
  val fired_store_retry    = widthMap(w => RegNext(will_fire_store_retry  (w) && !IsKilledByBranch(io.core.brupdate, io.core.exception, retry_queue.io.deq.bits.uop)))
  val fired_store_commit   = widthMap(w => RegNext(will_fire_store_commit_slow(w) || will_fire_store_commit_fast(w)))
  val fired_load_wakeup    = widthMap(w => RegNext(will_fire_load_wakeup  (w) && !IsKilledByBranch(io.core.brupdate, io.core.exception, ldq_wakeup_e.bits.uop)))
  val fired_hella_incoming = RegNext(will_fire_hella_incoming)
  val fired_hella_wakeup   = RegNext(will_fire_hella_wakeup)

  val mem_incoming_uop     = SafeRegNext(widthMap(w => UpdateBrMask(io.core.brupdate, agen(w).bits.uop)))
  val mem_ldq_incoming_e   = RegNext(widthMap(w => UpdateBrMask(io.core.brupdate, io.core.exception, ldq_incoming_e(w))))
  val mem_stq_incoming_e   = RegNext(widthMap(w => UpdateBrMask(io.core.brupdate, io.core.exception, stq_incoming_e(w))))
  val mem_ldq_wakeup_e     = RegNext(UpdateBrMask(io.core.brupdate, io.core.exception, ldq_wakeup_e))
  val mem_ldq_retry_e      = RegNext(UpdateBrMask(io.core.brupdate, io.core.exception, ldq_read(ldq_retry_idx)))
  val mem_stq_retry_e      = RegNext(UpdateBrMask(io.core.brupdate, io.core.exception, stq_read(stq_retry_idx)))
  val mem_ldq_e            = widthMap(w =>
                             Mux(fired_load_agen     (w) ||
                                 fired_load_agen_exec(w), mem_ldq_incoming_e(w),
                             Mux(fired_load_retry    (w), mem_ldq_retry_e,
                             Mux(fired_load_wakeup   (w), mem_ldq_wakeup_e,
                                                          (0.U).asTypeOf(Valid(new LDQEntry))))))
  val mem_stq_e            = widthMap(w =>
                             Mux(fired_store_agen (w), mem_stq_incoming_e(w),
                             Mux(fired_store_retry(w), mem_stq_retry_e, (0.U).asTypeOf(Valid(new STQEntry)))))


  val mem_tlb_miss             = RegNext(exe_tlb_miss)
  val mem_tlb_uncacheable      = RegNext(exe_tlb_uncacheable)
  val mem_paddr                = RegNext(widthMap(w => dmem_req(w).bits.addr))

  // Task 1: Clr ROB busy bit

  val stq_clr_head_idx = SafeRegNext(LSUAgePriorityEncoder((0 until numStqEntries).map(i => {
    stq_valid(i) && !stq_cleared(i)
  }), stq_commit_head))

  var clr_idx = stq_clr_head_idx
  for (i <- 0 until coreWidth) {
    // Delay store clearing 1 extra cycle, as the store clear can't occur
    // too quickly before an order fail caused by this store propagates to ROB
    val clr_valid = Reg(Bool())
    clr_valid    := false.B
    val clr_uop   = Reg(new MicroOp)

    val clr_valid_1 = RegNext(clr_valid && !IsKilledByBranch(io.core.brupdate, io.core.exception, clr_uop))
    val clr_uop_1   = RegNext(UpdateBrMask(io.core.brupdate, clr_uop))


    //val stq_e   = WireInit(stq(clr_idx))
    val s_uop = WireInit(stq_uop(clr_idx))
    when ( stq_valid           (clr_idx)        &&
           stq_addr            (clr_idx).valid  &&
           stq_data            (clr_idx).valid  &&
          !stq_addr_is_virtual (clr_idx)        &&
          !s_uop.is_amo                         &&
          !stq_cleared         (clr_idx)        &&
          !IsKilledByBranch(io.core.brupdate, io.core.exception, s_uop)) {
      clr_valid := true.B
      clr_uop   := UpdateBrMask(io.core.brupdate, s_uop)

      stq_cleared(clr_idx) := true.B
    }

    io.core.clr_bsy(i).valid := clr_valid_1 && !IsKilledByBranch(io.core.brupdate, io.core.exception, clr_uop_1)
    io.core.clr_bsy(i).bits  := clr_uop_1.rob_idx

    clr_idx = WrapIncWCarry(clr_idx, numStqEntries)
  }





  // Task 2: Do LD-LD. ST-LD searches for ordering failures
  //         Do LD-ST search for forwarding opportunities
  // We have the opportunity to kill a request we sent last cycle. Use it wisely!

  // We translated a store last cycle
  val do_st_search = widthMap(w => (fired_store_agen(w) || fired_store_retry(w)) && !mem_tlb_miss(w))
  // We translated a load last cycle
  val do_ld_search = widthMap(w => ((fired_load_agen(w) || fired_load_agen_exec(w) || fired_load_retry(w)) && !mem_tlb_miss(w)) ||
                     fired_load_wakeup(w))
  // We are making a local line visible to other harts
  val do_release_search = widthMap(w => fired_release(w))

  // Store addrs don't go to memory yet, get it from the TLB response
  // Load wakeups don't go through TLB, get it through memory
  // Load incoming and load retries go through both

  val lcam_addr  = widthMap(w => Mux(fired_store_agen(w) || fired_store_retry(w) || fired_load_agen(w) || fired_load_agen_exec(w),
                                     RegNext(exe_tlb_paddr(w)),
                                     Mux(fired_release(w), RegNext(io.dmem.release.bits.address),
                                         mem_paddr(w))))
  val lcam_uop   = widthMap(w => Mux(do_st_search(w), mem_stq_e(w).bits.uop,
                                 Mux(do_ld_search(w), mem_ldq_e(w).bits.uop, NullMicroOp)))

  val lcam_mask  = widthMap(w => GenByteMask(lcam_addr(w), lcam_uop(w).mem_size))
  val lcam_next_stq_idx = widthMap(w => mem_ldq_e(w).bits.next_stq_idx)
  val lcam_is_release = widthMap(w => fired_release(w))
  val lcam_ldq_idx  = widthMap(w =>
                      Mux(fired_load_agen     (w) ||
                          fired_load_agen_exec(w)  , mem_incoming_uop(w).ldq_idx,
                      Mux(fired_load_wakeup  (w), SafeRegNext(ldq_wakeup_idx),
                      Mux(fired_load_retry   (w), SafeRegNext(ldq_retry_idx), 0.U((1+ldqAddrSz).W)))))
  val lcam_stq_idx  = widthMap(w =>
                      Mux(fired_store_agen (w), mem_incoming_uop(w).stq_idx,
                      Mux(fired_store_retry(w), SafeRegNext(stq_retry_idx), 0.U((1+stqAddrSz).W))))

  // is_younger_mask(i) == 1 means i is younger than ldq_idx (ldq_idx is older than i)
  val lcam_younger_load_mask = Wire(Vec(lsuWidth, Vec(numLdqEntries, Bool())))

  for (w <- 0 until lsuWidth) {
    for (i <- 0 until numLdqEntries) {
      lcam_younger_load_mask(w)(i) := EntryValidFromAge(lcam_ldq_idx(w), ldq_tail, i.U((ldqAddrSz+1).W)) & (i.U =/= GetRealLSQIdx(lcam_ldq_idx(w)))
    }      
  }


  val can_forward = widthMap(w => Mux(fired_load_agen(w) || fired_load_agen_exec(w) || fired_load_retry(w),
    !mem_tlb_uncacheable(w),
    !mem_ldq_wakeup_e.bits.addr_is_uncacheable
  ))

  val kill_forward = WireInit(widthMap(w => false.B))

  // Mask of stores which we conflict on address with
  val ldst_addr_matches    = Wire(Vec(lsuWidth, UInt(numStqEntries.W)))
  // Mask of stores which we can forward from
  val ldst_forward_matches = Wire(Vec(lsuWidth, UInt(numStqEntries.W)))
  // Mask of stores we can forward to
  val stld_prs2_matches    = Wire(Vec(lsuWidth, UInt(numStqEntries.W)))

  val s1_executing_loads = RegNext(s0_executing_loads)
  val s1_set_execute     = WireInit(s1_executing_loads)

  val wb_ldst_forward_matches  = RegNext(ldst_forward_matches)
  val wb_ldst_forward_valid    = Wire(Vec(lsuWidth, Bool()))
  val wb_ldst_forward_e        = widthMap(w => RegNext(UpdateBrMask(io.core.brupdate, ldq_read(lcam_ldq_idx(w)).bits)))
  val wb_ldst_forward_ldq_idx  = SafeRegNext(lcam_ldq_idx)
  val wb_ldst_forward_ld_addr  = RegNext(lcam_addr)
  val wb_ldst_forward_stq_idx  = Wire(Vec(lsuWidth, UInt((stqAddrSz+1).W)))

  val failed_load = WireInit(false.B)

  for (i <- 0 until numLdqEntries) {
    // val l_bits  = ldq(i).bits
    val l_valid           = ldq_valid(i)
    val l_addr            = ldq_addr(i)
    val l_addr_is_virtual = ldq_addr_is_virtual(i)
    val l_executed        = ldq_executed(i)
    val l_succeeded       = ldq_succeeded(i)
    val l_will_succeed    = ldq_will_succeed(i)
    val l_observed        = ldq_observed(i)
    val l_mask            = ldq_ld_byte_mask(i)
    val l_next_stq_idx    = ldq_next_stq_idx(i)
    val l_uop             = WireInit(ldq_uop(i))
    val l_forward_std_val = ldq_forward_std_val(i)
    val l_forward_stq_idx = ldq_forward_stq_idx(i)

    val block_addr_matches = widthMap(w => lcam_addr(w) >> blockOffBits === l_addr.bits >> blockOffBits)
    val dword_addr_matches = widthMap(w => block_addr_matches(w) && lcam_addr(w)(blockOffBits-1,3) === l_addr.bits(blockOffBits-1,3))
    val mask_match   = widthMap(w => (l_mask & lcam_mask(w)) === l_mask)
    val mask_overlap = widthMap(w => (l_mask & lcam_mask(w)).orR)


    // Searcher is a store
    for (w <- 0 until lsuWidth) {

      when ( do_release_search(w)   &&
             l_valid                &&
             l_addr.valid           &&
            !l_addr_is_virtual      &&
             block_addr_matches(w)) {
        // This load has been observed, so if a younger load to the same address has not
        // executed yet, this load must be squashed
        ldq_observed(i) := true.B
      }
      when (       do_st_search(w)                                     &&
                   l_valid                                             &&
                   l_addr.valid                                        &&
                   (l_executed || l_succeeded)                         &&
                   !l_addr_is_virtual                                  &&
                   IdxAgeOt(lcam_stq_idx(w), l_next_stq_idx)           &&
                   dword_addr_matches(w)                               &&
                   mask_overlap(w)) {

        // searcher is older than the load
        // the lcam entry depends on the stq_entry -> stq entry pointed to by lcam_stq_idx(w) is older
        assert(l_next_stq_idx === l_uop.stq_idx)

        // the stq entry forwarded out of is even older than this store
        val forwarded_is_older = IsOlderLSU(l_forward_stq_idx, lcam_stq_idx(w), l_uop.stq_idx)
        // We are older than this load, which overlapped us.
        when (!l_forward_std_val || // If the load wasn't forwarded, it definitely failed
          ((l_forward_stq_idx =/= lcam_stq_idx(w)) && forwarded_is_older)) { // If the load forwarded from us, we might be ok
          ldq_order_fail(i) := true.B
          failed_load := true.B
        }
      }
      when (       do_ld_search(w)       &&
                   l_valid               &&
                   l_addr.valid          &&
                   !l_addr_is_virtual    &&
                   dword_addr_matches(w) &&
                   mask_overlap(w)) {

        val searcher_is_older = lcam_younger_load_mask(w)(i)

        when (searcher_is_older) {
          when ((l_executed || l_succeeded) &&
                !s1_executing_loads(i) && // If the load is proceeding in parallel we don't need to kill it
                l_observed) {        // Its only a ordering failure if the cache line was observed between the younger load and us
            //ldq_order_fail(i) := true.B
            // failed_load := true.B

	    // this case is no longer possible where an older search gets replayed and found that a younger search has already been released. Note that this is stronger than rvwmo.
            assert(false.B)
          }
        } .elsewhen (GetRealLSQIdx(lcam_ldq_idx(w)) =/= i.U) {
          // The load is older, and it wasn't executed
          // we need to kill ourselves, and prevent forwarding
	      // can also forward from the next cycle
          when (!(l_executed && (l_succeeded || l_will_succeed))) {
            s1_set_execute(lcam_ldq_idx(w))    := false.B
            when (RegNext(dmem_req_fire(w) && !s0_kills(w)) && !fired_load_agen(w)) {
              io.dmem.s1_kill(w)               := true.B
            }
            kill_forward(w)                    := true.B
          }
        }
      }
    }
  }


  for (wi <- 0 until lsuWidth) {
    for (w <- 0 until lsuWidth) {
      // A younger load might find a older nacking load
      // this is not the full case, need also to account for the situation where the older load already went to sleep above
      val nack_dword_addr_matches = (lcam_addr(w) >> 3) === (io.dmem.nack(wi).bits.addr >> 3)
      val nack_mask = GenByteMask(io.dmem.nack(wi).bits.addr, io.dmem.nack(wi).bits.uop.mem_size)
      val nack_mask_overlap = (nack_mask & lcam_mask(w)) =/= 0.U
      when (do_ld_search(w)                    &&
            io.dmem.nack(wi).valid             &&
            io.dmem.nack(wi).bits.uop.uses_ldq &&
            nack_dword_addr_matches            &&
            nack_mask_overlap                  &&
            IsOlderLSU(io.dmem.nack(wi).bits.uop.ldq_idx, lcam_ldq_idx(w), ldq_head)) {
        s1_set_execute(lcam_ldq_idx(w)) := false.B
        when (RegNext(dmem_req_fire(w) && !s0_kills(w)) && !fired_load_agen(w)) {
          io.dmem.s1_kill(w) := true.B
        }
        kill_forward(w) := true.B
      }



      // A older load might find a younger forwarding load
      val forward_dword_addr_matches = (lcam_addr(w) >> 3 === wb_ldst_forward_ld_addr(wi) >> 3)
      val forward_mask = GenByteMask(wb_ldst_forward_ld_addr(wi), wb_ldst_forward_e(wi).uop.mem_size)
      val forward_mask_overlap = (forward_mask & lcam_mask(w)) =/= 0.U
      when (do_ld_search(w)                   &&
            wb_ldst_forward_valid(wi)         &&
            forward_dword_addr_matches        &&
            forward_mask_overlap              &&
            wb_ldst_forward_e(wi).observed    &&
            IsOlderLSU(lcam_ldq_idx(w), wb_ldst_forward_ldq_idx(wi), ldq_head)) {
        ldq_order_fail(wb_ldst_forward_ldq_idx(wi)) := true.B
        failed_load := true.B
      }

      // A older store might find a younger load which is forwarding from the wrong place
      val forwarded_is_older = IsOlderLSU(wb_ldst_forward_stq_idx(wi), lcam_stq_idx(w), wb_ldst_forward_e(wi).uop.stq_idx)

      when (do_st_search(w)                                               &&
            wb_ldst_forward_valid(wi)                                     &&
            forward_dword_addr_matches                                    &&
            forward_mask_overlap                                          &&
            IdxAgeOt(lcam_stq_idx(w), wb_ldst_forward_e(wi).next_stq_idx) &&
            forwarded_is_older) {
        ldq_order_fail(wb_ldst_forward_ldq_idx(wi)) := true.B
        failed_load := true.B
      }
    }
  }

  val addr_matches    = Wire(Vec(lsuWidth, Vec(numStqEntries, Bool())))
  val forward_matches = Wire(Vec(lsuWidth, Vec(numStqEntries, Bool())))
  val prs2_matches    = Wire(Vec(lsuWidth, Vec(numStqEntries, Bool())))
  // age_matches(w)(i) |-> stq(i) is older than the searcher
  val age_matches     = Wire(Vec(lsuWidth, Vec(numStqEntries, Bool())))


  for (i <- 0 until numStqEntries) {
    //val s_e    = WireInit(stq(i))
    val s_addr            = stq_addr(i)
    val s_data            = stq_data(i)
    val s_addr_is_virtual = stq_addr_is_virtual(i)
    val s_uop             = WireInit(stq_uop(i))
    val s_valid           = stq_valids(i)

    val write_mask = GenByteMask(s_addr.bits, s_uop.mem_size)
    for (w <- 0 until lsuWidth) {
      val dword_addr_matches = ( s_addr.valid      &&
                                !s_uop.is_amo      &&
                                !s_addr_is_virtual &&
                                (s_addr.bits(corePAddrBits-1,3) === lcam_addr(w)(corePAddrBits-1,3)))
      val mask_union = lcam_mask(w) & write_mask

      addr_matches(w)(i)    := (mask_union =/= 0.U) && dword_addr_matches
      forward_matches(w)(i) := addr_matches(w)(i) && s_data.valid && (mask_union === lcam_mask(w))

      prs2_matches(w)(i) := (s_uop.prs2 === lcam_uop(w).pdst &&
                             s_uop.lrs2_rtype === RT_FIX &&
                             enableLoadToStoreForwarding.B)

      // for the load address depend on this entry in laq, the entry must be younger than the head of the store queue (as the stq head always points to an invalid entry), but older than the next stq entry (means not younger) (in the perspective of the current load )at the point of issue
      assert(reset.asBool | io.core.brupdate.b1.mispredict_mask.orR | io.core.brupdate.b2.mispredict | s_valid === EntryValidFromAge(stq_head, stq_tail, i.U((stqAddrSz+1).W)), s"Entry $i has failed")

      age_matches(w)(i)     := EntryValidFromAge(stq_head,lcam_next_stq_idx(w),i.U((stqAddrSz+1).W))
    }
  }


  val fast_stq_valids = stq_valid.asUInt
  for (w <- 0 until lsuWidth) {
    // address matches in store queue /\ is older than the current entry /\ valid
    ldst_addr_matches(w)    := (addr_matches(w).asUInt & age_matches(w).asUInt) & fast_stq_valids
    ldst_forward_matches(w) := (forward_matches(w).asUInt & age_matches(w).asUInt) & fast_stq_valids
    stld_prs2_matches(w)    := (prs2_matches(w).asUInt & ~age_matches(w).asUInt) & fast_stq_valids
  }

  val stq_amos = VecInit(stq_uop.map(u => u.is_fence || u.is_amo))
  for (w <- 0 until lsuWidth) {
    val has_older_amo = (stq_amos.asUInt & age_matches(w).asUInt) =/= 0.U
    //tianrui: this is the place of the bug
    when (do_ld_search(w) && (has_older_amo || (ldst_addr_matches(w) =/= 0.U))) {
      when (RegNext(dmem_req_fire(w) && !s0_kills(w)) && !fired_load_agen(w)) {
        io.dmem.s1_kill(w)               := true.B
      }
      s1_set_execute(lcam_ldq_idx(w)) := false.B
      when (has_older_amo) {
        kill_forward(w) := true.B
      }
    }
  }

  // Set execute bit in LDQ
  for (i <- 0 until numLdqEntries) {
    when (s1_set_execute(i)) { ldq_executed(i) := true.B }
  }



  // Find the youngest store which the load is dependent on
  for (w <- 0 until lsuWidth) {
    val (youngest_matching, match_found) = ForwardingAgeLogic(numStqEntries, ldst_addr_matches(w), GetRealLSQIdx(lcam_uop(w).stq_idx))
    val (youngest_forwarder, forwarder_found) = ForwardingAgeLogic(numStqEntries, ldst_forward_matches(w), GetRealLSQIdx(lcam_uop(w).stq_idx))

    wb_ldst_forward_stq_idx(w) := youngest_forwarder
    // Forward if st-ld forwarding is possible from the writemask and loadmask
    wb_ldst_forward_valid(w)    := (youngest_matching === youngest_forwarder                       &&
                                    match_found                                                    &&
                                    forwarder_found                                                &&
                                    RegNext(can_forward(w) && !kill_forward(w) && do_ld_search(w)) &&
                                    !RegNext(IsKilledByBranch(io.core.brupdate, io.core.exception, lcam_uop(w))))

  }



  // Avoid deadlock with a 1-w LSU prioritizing load wakeups > store commits
  // On a 2W machine, load wakeups and store commits occupy separate pipelines,
  // so only add this logic for 1-w LSU
  if (lsuWidth == 1) {
    // Wakeups may repeatedly find a st->ld addr conflict and fail to forward,
    // repeated wakeups may block the store from ever committing
    // Disallow load wakeups 1 cycle after this happens to allow the stores to drain
    when (RegNext(ldst_addr_matches(0) =/= 0.U) && !wb_ldst_forward_valid(0)) {
      block_load_wakeup := true.B
    }

    // If stores remain blocked for 15 cycles, block load wakeups to get a store through
    val store_blocked_counter = Reg(UInt(4.W))
    when (will_fire_store_commit_fast(0) || will_fire_store_commit_slow(0) || !can_fire_store_commit_slow(0)) {
      store_blocked_counter := 0.U
    } .elsewhen (can_fire_store_commit_slow(0) && !(will_fire_store_commit_slow(0) || will_fire_store_commit_fast(0))) {
      store_blocked_counter := Mux(store_blocked_counter === 15.U, 15.U, store_blocked_counter + 1.U)
    }
    when (store_blocked_counter === 15.U) {
      block_load_wakeup := true.B
    }
  }


  // Forward this load to the youngest matching store
  val mem_stld_forward_stq_idx = widthMap(w => LSUAgePriorityEncoder(stld_prs2_matches(w).asBools, lcam_uop(w).stq_idx))
  val mem_stld_forward_valid   = widthMap(w => do_ld_search(w) && stld_prs2_matches(w)(mem_stld_forward_stq_idx(w)))


  // Task 3: Clr unsafe bit in ROB for succesful t_ranslations
  //         Delay this a cycle to avoid going ahead of the exception broadcast
  //         The unsafe bit is cleared on the first translation, so no need to fire for load wakeups
  for (w <- 0 until lsuWidth) {
    io.core.clr_unsafe(w).valid := (
      RegNext(do_st_search(w)) ||
      (!io.dmem.nack(w).valid && RegNext(do_ld_search(w) && !fired_load_agen(w) && !io.dmem.s1_kill(w) && RegNext(dmem_req_fire(w))))
    ) && !RegNext(failed_load)
    io.core.clr_unsafe(w).bits  := RegNext(lcam_uop(w).rob_idx)
  }



  // one exception port, but multiple causes!
  // - 1) the incoming store-address finds a faulting load (it is by definition younger)
  // - 2) the incoming load or store address is excepting. It must be older and thus takes precedent.
  val r_xcpt_valid = RegInit(false.B)
  val r_xcpt       = Reg(new Exception)

  val ld_xcpt_valid = (ldq_order_fail.asUInt & ldq_valid.asUInt) =/= 0.U

  // detect which loads get marked as failures, but broadcast to the ROB the oldest failing load
  val l_idx = LSUAgePriorityEncoder((0 until numLdqEntries).map { i => ldq_valid(i) && ldq_order_fail(i) }, ldq_head)
  val ld_xcpt_uop   = WireInit(ldq_uop(l_idx))

  val use_mem_xcpt = (mem_xcpt_valid && IsOlder(mem_xcpt_uop.rob_idx, ld_xcpt_uop.rob_idx, io.core.rob_head_idx)) || !ld_xcpt_valid

  val xcpt_uop = Mux(use_mem_xcpt, mem_xcpt_uop, ld_xcpt_uop)

  r_xcpt_valid := (ld_xcpt_valid || mem_xcpt_valid) && !IsKilledByBranch(io.core.brupdate, io.core.exception, xcpt_uop)
  r_xcpt.uop         := xcpt_uop
  r_xcpt.uop.br_mask := GetNewBrMask(io.core.brupdate, xcpt_uop)
  r_xcpt.cause       := Mux(use_mem_xcpt, mem_xcpt_cause, MINI_EXCEPTION_MEM_ORDERING)
  r_xcpt.badvaddr    := mem_xcpt_vaddr // TODO is there another register we can use instead?

  io.core.lxcpt.valid := r_xcpt_valid && !IsKilledByBranch(io.core.brupdate, io.core.exception, r_xcpt.uop)
  io.core.lxcpt.bits  := r_xcpt

  // Task 4: Speculatively wakeup loads 1 cycle before they come back
  // if loadUseDelay == 5
  for (w <- 0 until lsuWidth) {
    if (!enableFastLoadUse) {
      wakeupArbs(w).io.in(1).valid := (fired_load_agen_exec(w)      &&
                                       RegNext(dmem_req_fire(w))    &&
                                       !io.dmem.s1_nack_advisory(w) &&
                                       !mem_incoming_uop(w).fp_val)
      wakeupArbs(w).io.in(1).bits.uop := mem_incoming_uop(w)
      wakeupArbs(w).io.in(1).bits.bypassable := true.B
      wakeupArbs(w).io.in(1).bits.speculative_mask := 0.U
      wakeupArbs(w).io.in(1).bits.rebusy := false.B
    }
  }


  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // Writeback Cycle (St->Ld Forwarding Path)
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // Handle Memory Responses and nacks
  //----------------------------------
  val iresp = Wire(Vec(lsuWidth, Valid(new ExeUnitResp(xLen))))
  val fresp = Wire(Vec(lsuWidth, Valid(new ExeUnitResp(xLen))))

  for (w <- 0 until lsuWidth) {
    iresp(w).valid := false.B
    iresp(w).bits  := DontCare
    fresp(w).valid := false.B
    fresp(w).bits  := DontCare
    io.core.iresp(w) := (if (enableFastLoadUse) iresp(w) else RegNext(
      UpdateBrMask(io.core.brupdate, io.core.exception, iresp(w))))
    io.core.fresp(w) := fresp(w)
  }

  // Initially assume the speculative load wakeup failed
  val wb_spec_wakeups = Wire(Vec(lsuWidth, Valid(new Wakeup)))
  val spec_wakeups = Wire(Vec(lsuWidth, Valid(new Wakeup)))

  val wb_slow_wakeups = Wire(Vec(lsuWidth, Valid(new Wakeup)))
  val slow_wakeups = Wire(Vec(lsuWidth, Valid(new Wakeup)))

  val dmem_resp_fired = WireInit(widthMap(w => false.B))
  for (w <- 0 until lsuWidth) {
    val w1 = Reg(Valid(new Wakeup))
    w1.valid := wakeupArbs(w).io.in(1).fire && !IsKilledByBranch(io.core.brupdate, io.core.exception, wakeupArbs(w).io.in(1).bits)
    w1.bits  := UpdateBrMask(io.core.brupdate, wakeupArbs(w).io.in(1).bits)

    val w2 = Reg(Valid(new Wakeup))
    w2.valid := w1.valid && !IsKilledByBranch(io.core.brupdate, io.core.exception, w1.bits)
    w2.bits  := UpdateBrMask(io.core.brupdate, w1.bits)


    spec_wakeups(w).valid := w2.valid
    spec_wakeups(w).bits  := w2.bits

    wb_spec_wakeups(w) := (if (enableFastLoadUse) w2 else w1)

  }
  for (w <- 0 until lsuWidth) {
    wb_slow_wakeups(w).valid := false.B
    wb_slow_wakeups(w).bits  := DontCare

    // Handle nacks
    when (io.dmem.nack(w).valid) {
      when (io.dmem.nack(w).bits.is_hella) {
        assert(hella_state === h_wait || hella_state === h_dead)
      } .elsewhen (io.dmem.nack(w).bits.uop.uses_ldq) {
        assert(ldq_executed(io.dmem.nack(w).bits.uop.ldq_idx))
        ldq_executed(io.dmem.nack(w).bits.uop.ldq_idx) := false.B
      } .otherwise {
        assert(io.dmem.nack(w).bits.uop.uses_stq)
        when (IsOlderLSU(io.dmem.nack(w).bits.uop.stq_idx, stq_execute_head, stq_head)) {
          stq_execute_queue_flush := true.B
          stq_execute_head := io.dmem.nack(w).bits.uop.stq_idx
        }
      }
    }
    // Handle the response
    val resp = Mux(!io.dmem.resp(w).valid && (w == lsuWidth-1).B,
      io.dmem.ll_resp.bits, io.dmem.resp(w).bits)
    if (w == lsuWidth-1) {
      io.dmem.ll_resp.ready := !io.dmem.resp(w).valid && !wb_spec_wakeups(w).valid
    }
    when (io.dmem.resp(w).valid || ((w == lsuWidth-1).B && io.dmem.ll_resp.fire)) {
      when (resp.uop.uses_ldq) {
        assert(!resp.is_hella)
        val ldq_idx = resp.uop.ldq_idx
        val uop = WireInit(ldq_uop(ldq_idx))
        val send_iresp = uop.dst_rtype === RT_FIX
        val send_fresp = uop.dst_rtype === RT_FLT

        iresp(w).bits.uop  := uop
        fresp(w).bits.uop  := uop
        iresp(w).valid     := send_iresp



        iresp(w).bits.data := resp.data
        fresp(w).valid     := send_fresp
        fresp(w).bits.data := resp.data

        assert(send_iresp ^ send_fresp)
        dmem_resp_fired(w) := true.B

        ldq_will_succeed (ldq_idx) := iresp(w).valid || fresp(w).valid
        ldq_debug_wb_data(ldq_idx) := resp.data

        wb_slow_wakeups(w).valid    := send_iresp
        wb_slow_wakeups(w).bits.uop := uop
        wb_slow_wakeups(w).bits.speculative_mask := 0.U
        wb_slow_wakeups(w).bits.rebusy := false.B
        wb_slow_wakeups(w).bits.bypassable := false.B
      }

      when (resp.uop.uses_stq)
      {
        val uop = stq_uop(resp.uop.stq_idx)
        assert(!resp.is_hella && resp.uop.is_amo)
        dmem_resp_fired(w) := true.B
        iresp(w).valid     := true.B
        iresp(w).bits.uop  := uop
        iresp(w).bits.data := resp.data

        wb_slow_wakeups(w).valid    := true.B
        wb_slow_wakeups(w).bits.uop := uop
        wb_slow_wakeups(w).bits.speculative_mask := 0.U
        wb_slow_wakeups(w).bits.rebusy := false.B
        wb_slow_wakeups(w).bits.bypassable := false.B

        stq_debug_wb_data(resp.uop.stq_idx) := resp.data
      }
    }
    // Handle store acks
    when (io.dmem.store_ack(w).valid) {
      stq_succeeded(io.dmem.store_ack(w).bits.uop.stq_idx) := true.B
    }


    when (dmem_resp_fired(w) && wb_ldst_forward_valid(w))
    {
      // Twiddle thumbs. Can't forward because dcache response takes precedence
    }
      .elsewhen (!dmem_resp_fired(w) && wb_ldst_forward_valid(w))
    {
      val f_idx       = wb_ldst_forward_ldq_idx(w).suggestName("f_idx")
      val forward_uop = wb_ldst_forward_e(w).uop.suggestName("forward_uop")
      //val stq_e       = WireInit(stq(wb_ldst_forward_stq_idx(w)))
      val s_idx       = wb_ldst_forward_stq_idx(w).suggestName("s_idx")
      val storegen = new freechips.rocketchip.rocket.StoreGen(
                                stq_uop(s_idx).mem_size, stq_addr(s_idx).bits,
                                stq_data(s_idx).bits, coreDataBytes)
      val trace_storegen_data = WireDefault(storegen.data).suggestName("storegen_data")
      val loadgen  = new freechips.rocketchip.rocket.LoadGen(
                                forward_uop.mem_size, forward_uop.mem_signed,
                                wb_ldst_forward_ld_addr(w),
                                storegen.data, false.B, coreDataBytes)

      wb_slow_wakeups(w).valid    := forward_uop.dst_rtype === RT_FIX
      wb_slow_wakeups(w).bits.uop := forward_uop
      wb_slow_wakeups(w).bits.speculative_mask := 0.U
      wb_slow_wakeups(w).bits.rebusy := false.B
      wb_slow_wakeups(w).bits.bypassable := false.B

      iresp(w).valid := (forward_uop.dst_rtype === RT_FIX)
      fresp(w).valid := (forward_uop.dst_rtype === RT_FLT)
      iresp(w).bits.uop  := forward_uop
      fresp(w).bits.uop  := forward_uop
      iresp(w).bits.data := loadgen.data
      fresp(w).bits.data := loadgen.data

      ldq_will_succeed   (f_idx) := true.B
      ldq_forward_std_val(f_idx) := true.B
      ldq_forward_stq_idx(f_idx) := wb_ldst_forward_stq_idx(w)

      ldq_debug_wb_data  (f_idx) := loadgen.data
    }

    // Forward loads to store-data
    if (enableStLdForwarding) {
      when (iresp(w).valid &&
            iresp(w).bits.uop.uses_ldq &&
            iresp(w).bits.uop.ldq_idx === RegNext(lcam_ldq_idx(w)) &&
            RegNext(mem_stld_forward_valid(w))) {
        assert(!stq_data(RegNext(mem_stld_forward_stq_idx(w))).valid)
        stq_data(RegNext(mem_stld_forward_stq_idx(w))).valid := true.B
        stq_data(RegNext(mem_stld_forward_stq_idx(w))).bits  := iresp(w).bits.data
      }
    }

    // Do wakeups
    wakeupArbs(w).io.in(0).valid := false.B
    wakeupArbs(w).io.in(0).bits  := DontCare
    slow_wakeups(w) := (if (enableFastLoadUse) wb_slow_wakeups(w) else RegNext(UpdateBrMask(
      io.core.brupdate, io.core.exception, wb_slow_wakeups(w))))
    when (slow_wakeups(w).valid) {
      when (spec_wakeups(w).valid) { // Do nothing, since the slow wakeup matches a earlier speculative wakeup
        assert(slow_wakeups(w).bits.uop.ldq_idx === spec_wakeups(w).bits.uop.ldq_idx)
      } .otherwise { // This wasn't speculatively woken up, so end the late slow wakeup
        wakeupArbs(w).io.in(0).valid := slow_wakeups(w).valid
        wakeupArbs(w).io.in(0).bits := slow_wakeups(w).bits
      }
    } .otherwise {
      when (spec_wakeups(w).valid) { // This sent a speculative wakeup, now we need to undo it
        wakeupArbs(w).io.in(0).valid := spec_wakeups(w).valid
        wakeupArbs(w).io.in(0).bits  := spec_wakeups(w).bits
        wakeupArbs(w).io.in(0).bits.rebusy := true.B
      }
    }
  }



  //-------------------------------------------------------------
  // Kill speculated entries on branch mispredict
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  // Kill stores
  for (i <- 0 until numStqEntries)
  {

    when (stq_valid(i))
    {
      val uop = WireInit(stq_uop(i))
      stq_uop(i).br_mask := GetNewBrMask(io.core.brupdate, uop.br_mask)

      when (IsKilledByBranch(io.core.brupdate, false.B, uop))
      {
        stq_valid(i)       := false.B
        stq_addr (i).valid := false.B
        stq_data (i).valid := false.B
      }
    }

    assert (!(IsKilledByBranch(io.core.brupdate, false.B, stq_uop(i)) && stq_valid(i) && stq_committed(i)),
      "Branch is trying to clear a committed store.")
  }

  // Kill loads
  for (i <- 0 until numLdqEntries)
  {
    when (ldq_valid(i)) {
      val uop = WireInit(ldq_uop(i))
      ldq_uop(i).br_mask := GetNewBrMask(io.core.brupdate, uop.br_mask)
      when (IsKilledByBranch(io.core.brupdate, io.core.exception, uop))
      {
        ldq_valid(i)       := false.B
        ldq_addr (i).valid := false.B
      }
    }
  }

  //-------------------------------------------------------------
  when (io.core.brupdate.b2.mispredict && !io.core.exception)
  {
    stq_tail := io.core.brupdate.b2.uop.stq_idx
    ldq_tail := io.core.brupdate.b2.uop.ldq_idx
  }

  //-------------------------------------------------------------
  //-------------------------------------------------------------
  // dequeue old entries on commit
  //-------------------------------------------------------------
  //-------------------------------------------------------------

  var temp_stq_commit_head = stq_commit_head
  var temp_ldq_head        = ldq_head
  for (w <- 0 until coreWidth)
  {
    val commit_store = io.core.commit.valids(w) && io.core.commit.uops(w).uses_stq
    val commit_load  = io.core.commit.valids(w) && io.core.commit.uops(w).uses_ldq
    // val stq_e = WireInit(stq(temp_stq_commit_head))
    // val ldq_e = WireInit(ldq(temp_ldq_head))
    val l_uop = WireInit(ldq_uop(temp_ldq_head))
    val s_uop = WireInit(stq_uop(temp_stq_commit_head))
    when (commit_store)
    {
      stq_committed  (temp_stq_commit_head) := true.B
      stq_can_execute(temp_stq_commit_head) := true.B

    } .elsewhen (commit_load) {
      assert (ldq_valid(temp_ldq_head), "[lsu] trying to commit an un-allocated load entry.")
      assert ((ldq_executed(temp_ldq_head) || ldq_forward_std_val(temp_ldq_head)) && ldq_succeeded(temp_ldq_head),
        "[lsu] trying to commit an un-executed load entry.")

      ldq_valid(temp_ldq_head)                 := false.B
    }

    if (MEMTRACE_PRINTF) {
      when (commit_store || commit_load) {
        val uop    = Mux(commit_store, s_uop, l_uop)
        val addr   = Mux(commit_store, stq_addr(temp_stq_commit_head).bits    , ldq_addr(temp_ldq_head).bits)
        val stdata = Mux(commit_store, stq_data(temp_stq_commit_head).bits    , 0.U)
        val wbdata = Mux(commit_store, stq_debug_wb_data(temp_stq_commit_head), ldq_debug_wb_data(temp_ldq_head))
        printf("MT %x %x %x %x %x %x %x\n",
          io.core.tsc_reg, 0.U, uop.mem_cmd, uop.mem_size, addr, stdata, wbdata)
      }
    }

    temp_stq_commit_head = Mux(commit_store,
                               WrapIncWCarry(temp_stq_commit_head, numStqEntries),
                               temp_stq_commit_head)

    temp_ldq_head        = Mux(commit_load,
                               WrapIncWCarry(temp_ldq_head, numLdqEntries),
                               temp_ldq_head)
  }
  stq_commit_head := temp_stq_commit_head
  ldq_head        := temp_ldq_head

  // store has been committed AND successfully sent data to memory
  val stq_head_is_fence = stq_uop(stq_head).is_fence
  when (stq_valid(stq_head) && stq_committed(stq_head))
  {

    when (stq_head_is_fence && !io.dmem.ordered) {
      io.dmem.force_order := true.B
      store_needs_order   := true.B
    }
    clear_store := Mux(stq_head_is_fence, io.dmem.ordered, stq_succeeded(stq_head))
  }

  when (clear_store)
  {
    stq_valid(stq_head)           := false.B

    stq_head := WrapIncWCarry(stq_head, numStqEntries)
    when (stq_head_is_fence)
    {
      stq_execute_head := WrapIncWCarry(stq_execute_head, numStqEntries)
    }
  }


  // -----------------------
  // Hellacache interface
  // We need to time things like a HellaCache would
  io.hellacache.req.ready := false.B
  io.hellacache.s2_nack   := false.B
  io.hellacache.s2_nack_cause_raw := false.B
  io.hellacache.s2_uncached := DontCare
  io.hellacache.s2_paddr  := DontCare
  io.hellacache.s2_xcpt   := (0.U).asTypeOf(new rocket.HellaCacheExceptions)
  io.hellacache.replay_next   := false.B
  io.hellacache.s2_gpa        := DontCare
  io.hellacache.s2_gpa_is_pte := DontCare
  io.hellacache.ordered       := DontCare
  io.hellacache.perf          := DontCare
  io.hellacache.clock_enabled := true.B
  io.hellacache.store_pending := stq_valid.reduce(_||_)
  io.hellacache.resp.valid := false.B
  io.hellacache.resp.bits.addr   := hella_req.addr
  io.hellacache.resp.bits.tag    := hella_req.tag
  io.hellacache.resp.bits.cmd    := hella_req.cmd
  io.hellacache.resp.bits.signed := hella_req.signed
  io.hellacache.resp.bits.size   := hella_req.size
  io.hellacache.resp.bits.mask   := hella_req.mask
  io.hellacache.resp.bits.replay := false.B
  io.hellacache.resp.bits.has_data := true.B
  io.hellacache.resp.bits.data_word_bypass := io.dmem.ll_resp.bits.data
  io.hellacache.resp.bits.data_raw := io.dmem.ll_resp.bits.data
  io.hellacache.resp.bits.store_data := hella_req.data
  io.hellacache.resp.bits.dprv     := io.ptw.status.prv
  io.hellacache.resp.bits.dv       := io.ptw.status.v
  io.hellacache.resp.bits.data     := io.dmem.ll_resp.bits.data

  when (hella_state === h_ready) {
    io.hellacache.req.ready := true.B
    when (io.hellacache.req.fire) {
      hella_req   := io.hellacache.req.bits
      hella_state := h_s1
    }
  } .elsewhen (hella_state === h_s1) {
    can_fire_hella_incoming(0) := true.B

    hella_data := io.hellacache.s1_data
    hella_xcpt := dtlb.io.resp(0)

    when (io.hellacache.s1_kill) {
      when (will_fire_hella_incoming(0) && dmem_req_fire(0)) {
        hella_state := h_dead
      } .otherwise {
        hella_state := h_ready
      }
    } .elsewhen (will_fire_hella_incoming(0) && dmem_req_fire(0)) {
      hella_state := h_s2
    } .otherwise {
      hella_state := h_s2_nack
    }
  } .elsewhen (hella_state === h_s2_nack) {
    io.hellacache.s2_nack := true.B
    hella_state := h_ready
  } .elsewhen (hella_state === h_s2) {
    io.hellacache.s2_xcpt := hella_xcpt
    when (io.hellacache.s2_kill || hella_xcpt.asUInt =/= 0.U) {
      hella_state := h_dead
    } .otherwise {
      hella_state := h_wait
    }
  } .elsewhen (hella_state === h_wait) {
    when (io.dmem.ll_resp.fire && io.dmem.ll_resp.bits.is_hella) {
      hella_state := h_ready

      io.hellacache.resp.valid       := true.B
      io.hellacache.resp.bits.addr   := hella_req.addr
      io.hellacache.resp.bits.tag    := hella_req.tag
      io.hellacache.resp.bits.cmd    := hella_req.cmd
      io.hellacache.resp.bits.signed := hella_req.signed
      io.hellacache.resp.bits.size   := hella_req.size
      io.hellacache.resp.bits.data   := io.dmem.ll_resp.bits.data
    }
    for (w <- 0 until lsuWidth) {
      when ((io.dmem.resp(w).valid && io.dmem.resp(w).bits.is_hella) ||
            (io.dmem.store_ack(w).valid && io.dmem.store_ack(w).bits.is_hella)) {
        hella_state := h_ready

        io.hellacache.resp.valid       := true.B
        io.hellacache.resp.bits.addr   := hella_req.addr
        io.hellacache.resp.bits.tag    := hella_req.tag
        io.hellacache.resp.bits.cmd    := hella_req.cmd
        io.hellacache.resp.bits.signed := hella_req.signed
        io.hellacache.resp.bits.size   := hella_req.size
        io.hellacache.resp.bits.data   := io.dmem.resp(w).bits.data
      }
      when (io.dmem.nack(w).valid && io.dmem.nack(w).bits.is_hella) {
        hella_state := h_replay
      }
    }
  } .elsewhen (hella_state === h_replay) {
    can_fire_hella_wakeup(0) := true.B

    when (will_fire_hella_wakeup(0) && dmem_req_fire(0)) {
      hella_state := h_wait
    }
  } .elsewhen (hella_state === h_dead) {
    when (io.dmem.ll_resp.fire && io.dmem.ll_resp.bits.is_hella) {
      hella_state := h_ready
    }
    for (w <- 0 until lsuWidth) {
      when (io.dmem.resp(w).valid && io.dmem.resp(w).bits.is_hella) {
        hella_state := h_ready
      }
    }
  }

  //-------------------------------------------------------------
  // Exception / Reset

  when (reset.asBool || io.core.exception)
  {
    ldq_head := 0.U
    ldq_tail := 0.U

    when (reset.asBool)
    {
      stq_head := 0.U
      stq_tail := 0.U
      stq_commit_head  := 0.U
      stq_execute_head := 0.U

      for (i <- 0 until numStqEntries)
      {
        stq_valid(i)           := false.B
      }
    }
      .otherwise // exception
    {
      stq_tail := stq_commit_head

      for (i <- 0 until numStqEntries)
      {
        when (!stq_committed(i) && !stq_succeeded(i))
        {
          stq_valid(i)          := false.B
        }
      }
    }

    for (i <- 0 until numLdqEntries)
    {
      ldq_valid(i)           := false.B
    }
  }



}

/**
 * Object to take an address and generate an 8-bit mask of which bytes within a
 * double-word.
 */
object GenByteMask
{
   def apply(addr: UInt, size: UInt): UInt =
   {
      val mask = Wire(UInt(8.W))
      mask := MuxCase(255.U(8.W), Array(
                   (size === 0.U) -> (1.U(8.W) << addr(2,0)),
                   (size === 1.U) -> (3.U(8.W) << (addr(2,1) << 1.U)),
                   (size === 2.U) -> Mux(addr(2), 240.U(8.W), 15.U(8.W)),
                   (size === 3.U) -> 255.U(8.W)))
      mask
   }
}

/**
 * Object to return the lowest bit position after the head.
 */
object LSUAgePriorityEncoder
{
  def _private_helper(idx: UInt): UInt = {
    idx(idx.getWidth-2, 0)
  }
  def apply(in: Seq[Bool], head: UInt): UInt = {
    val head_base = _private_helper(head)
    val head_overflow = head(head.getWidth - 1)
    val base = AgePriorityEncoder(in, head_base)
    val overflow = Mux(base >= head_base, head_overflow, ~head_overflow)
    Cat(overflow, base)
  }
}


object ForwardingAgeLogic
{
  def apply(n: Int, matches: UInt, youngest: UInt): (UInt, Bool) = {
    val logic = Module(new ForwardingAgeLogic(n))
    logic.io.matches := matches
    logic.io.youngest := youngest
    (logic.io.found_idx, logic.io.found)
  }
}

class ForwardingAgeLogic(n: Int) extends Module()
{
  val io = IO(new Bundle
    {
      val matches    = Input(UInt(n.W)) // bit vector of addresses that match
                                        // between the load and the SAQ
      val youngest = Input(UInt(log2Ceil(n).W)) // needed to get "age"

      val found  = Output(Bool())
      val found_idx  = Output(UInt(log2Ceil(n).W))
    })

  // generating mask that zeroes out anything younger than tail
  val age_mask = Wire(Vec(n, Bool()))
  for (i <- 0 until n)
  {
    age_mask(i) := true.B
    when (i.U >= io.youngest) // currently the tail points PAST last store, so use >=
    {
      age_mask(i) := false.B
    }
  }

  // Priority encoder with moving tail: double length
  val matches = Wire(UInt((2*n).W))
  matches := Cat(io.matches & age_mask.asUInt,
                 io.matches)

  val found_match = Reg(Bool())
  val found_idx = Reg(UInt(log2Ceil(n).W))
  found_match       := false.B
  found_idx         := 0.U

  io.found_idx := found_idx
  io.found := found_match

   // look for youngest, approach from the oldest side, let the last one found stick
   for (i <- 0 until (2*n))
   {
      when (matches(i))
      {
         found_match := true.B
         found_idx := (i % n).U
      }
   }
}

object GetRealLSQIdx{
  def apply (idx: UInt): UInt = {
    idx(idx.getWidth-2, 0)
  }
}

object GetLSQIdxCarry{
  def apply(idx: UInt): UInt = {
    idx(idx.getWidth-1)
  }
}
/**
  when age1 is younger (later) than age2, return true,
  when age1 is older (earlier than age2, return false

  IdxAgeYoungerThan(entry1, entry2) = entry1.dep_mask(entry2)
  first entry must be real idx
  second entry must be idx pointer
  */
object IdxAgeYoungerThan{
  def apply(age1: UInt, age2: UInt): Bool = {
    assert(age1.widthKnown)
    assert(age2.widthKnown)
    val age1_width : Int = age1.getWidth
    val age2_width : Int = age2.getWidth
    assert(age1_width == age2_width)
    val age1_overflow : UInt = GetLSQIdxCarry(age1)
    val age2_overflow : UInt = GetLSQIdxCarry(age2)

    val age1_age      : UInt = GetRealLSQIdx(age1)
    val age2_age      : UInt = GetRealLSQIdx(age2)

    Mux1H(Seq(
      (age1_overflow === age2_overflow) -> (age1_age > age2_age),
      (age1_overflow =/= age2_overflow) -> (age1_age < age2_age)
    ))
  }
}

object IdxAgeEq{
  def apply(age1: UInt, age2: UInt): Bool = {
    assert(age1.getWidth == age2.getWidth)
    val width = age1.getWidth
    val age1_overflow : UInt = GetLSQIdxCarry(age1)
    val age2_overflow : UInt = GetLSQIdxCarry(age2)
    val age1_age      : UInt = GetRealLSQIdx(age1)
    val age2_age      : UInt = GetRealLSQIdx(age2)
    age1_age === age2_age
  }
}

object IdxAgeYt{
  def apply(age1: UInt, age2: UInt) : Bool = {
    IdxAgeYoungerThan(age1, age2)
  }
}

object IdxAgeYe{
  def apply(age1: UInt, age2: UInt) : Bool = {
    IdxAgeYoungerThan(age1, age2) | IdxAgeEq(age1, age2)
  }
}

object IdxAgeOt{
  def apply(age1: UInt, age2: UInt) : Bool = {
    IdxAgeYoungerThan(age2, age1)
  }
}

object IdxAgeOe{
  def apply(age1: UInt, age2: UInt) : Bool = {
    IdxAgeOt(age1, age2) | IdxAgeEq(age1, age2)
  }
}

object IsOlderLSU {
  def apply(i0: UInt, i1: UInt, head: UInt): Bool = {
    IdxAgeOt(i0, i1) ^ IdxAgeOt(i0, head) ^ IdxAgeOt(i1, head)
  }
}

object SafeRegNext{
  def apply[T <: Data](x: T): T = {
    val reg = Reg(chiselTypeOf(x))
    reg := x
    reg
  }
}

object EntryValidFromAge {
  def apply(head: UInt, tail: UInt, ptr: UInt) : Bool = {
    assert(head.widthKnown)
    assert(tail.widthKnown)
    assert(head.getWidth == tail.getWidth)
    assert(head.getWidth == ptr.getWidth)

    val real_head_idx = GetRealLSQIdx(head)
    val real_tail_idx = GetRealLSQIdx(tail)
    // the pointer doesn't have the extra carry bit
    val real_ptr_idx = GetRealLSQIdx(ptr)

    val head_carry = GetLSQIdxCarry(head)
    val tail_carry = GetLSQIdxCarry(tail)

    Mux(head_carry === tail_carry,
      (real_ptr_idx >= real_head_idx) & (real_ptr_idx < real_tail_idx),
      (real_ptr_idx >= real_head_idx) | (real_ptr_idx < real_tail_idx)
    )
  }
}

object WrapAddWCarryWidth
{
  def apply(value: UInt, amt: UInt, n: Int, w: Int): UInt = {
    if (isPow2(n)) {
      (value + amt)(w-1, 0)
    } else {
      val sum = Cat(0.U(1.W), value) + Cat(0.U(1.W), amt)
      Mux(sum >= n.U, sum - n.U, sum)(w-1, 0)
    }
  }
}
