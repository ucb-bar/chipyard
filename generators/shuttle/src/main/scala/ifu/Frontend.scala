package shuttle.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._

import shuttle.common._

trait HasShuttleFrontendParameters extends HasL1ICacheParameters
{
  def fetchAlign(addr: UInt) = ~(~addr | (fetchBytes-1).U)
  def blockAlign(addr: UInt) = ~(~addr | (cacheParams.blockBytes-1).U)
  def fetchIdx(addr: UInt) = addr >> log2Ceil(fetchBytes)
  def nextFetch(addr: UInt) = fetchAlign(addr) + fetchBytes.U
  def fetchMask(addr: UInt) = {
    val idx = addr.extract(log2Ceil(fetchWidth)+log2Ceil(coreInstBytes)-1, log2Ceil(coreInstBytes))
    ((BigInt(1) << fetchWidth)-1).U << idx
  }
}

class ShuttleFetchBundle(implicit val p: Parameters) extends Bundle
  with HasShuttleFrontendParameters
  with HasCoreParameters
{
  val btbParams = tileParams.btb.get

  val pc            = Output(UInt(vaddrBitsExtended.W))
  val next_pc       = Output(Valid(UInt(vaddrBitsExtended.W)))
  val edge_inst     = Output(Bool()) // True if 1st instruction in this bundle is pc - 2
  val insts         = Output(Vec(fetchWidth, Bits(32.W)))
  val exp_insts     = Output(Vec(fetchWidth, Bits(32.W)))
  val pcs           = Output(Vec(fetchWidth, UInt(vaddrBitsExtended.W)))
  val mask          = Output(UInt(fetchWidth.W)) // mark which words are valid instructions
  val btb_resp      = Output(Valid(new BTBResp))
  val ras_head      = Output(UInt(log2Ceil(btbParams.nRAS).W))

  val br_mask       = Output(UInt(fetchWidth.W))

  val xcpt_pf_if    = Output(Bool()) // I-TLB miss (instruction fetch fault).
  val xcpt_ae_if    = Output(Bool()) // Access exception.

  val end_half      = Valid(UInt(16.W))
}



class ShuttleFrontend(val icacheParams: ICacheParams, staticIdForMetadataUseOnly: Int)(implicit p: Parameters) extends LazyModule
{
  lazy val module = new ShuttleFrontendModule(this)
  val icache = LazyModule(new ShuttleICache(icacheParams, staticIdForMetadataUseOnly))
  val masterNode = icache.masterNode
  val resetVectorSinkNode = BundleBridgeSink[UInt](Some(() =>
    UInt(masterNode.edges.out.head.bundle.addressBits.W)))
}

class RASUpdate(implicit p: Parameters) extends CoreBundle()(p) {
  val btbParams = tileParams.btb.get
  val head = UInt(log2Ceil(btbParams.nRAS).W)
  val addr = UInt(vaddrBitsExtended.W)
}

class ShuttleFrontendIO(implicit p: Parameters) extends CoreBundle()(p) {
  val btbParams = tileParams.btb.get
  val redirect_flush = Output(Bool())
  val redirect_val = Output(Bool())
  val redirect_pc = Output(UInt(vaddrBitsExtended.W))
  val redirect_ras_head = Output(UInt(log2Ceil(btbParams.nRAS).W))
  val sfence = Valid(new SFenceReq)
  val flush_icache = Output(Bool())
  val resp = Flipped(Vec(retireWidth, Decoupled(new ShuttleUOP)))
  val peek = Flipped(Vec(retireWidth, Valid(new ShuttleUOP)))

  val btb_update = Valid(new ShuttleBTBUpdate)
  val bht_update = Valid(new BHTUpdate)
  val ras_update = Valid(new RASUpdate)

}

class ShuttleFrontendBundle(val outer: ShuttleFrontend) extends CoreBundle()(outer.p)
{
  val cpu = Flipped(new ShuttleFrontendIO)
  val ptw = new TLBPTWIO()
}

class ShuttleFrontendModule(outer: ShuttleFrontend) extends LazyModuleImp(outer)
  with HasShuttleFrontendParameters
  with HasCoreParameters
  with RISCVConstants
{
  val io = IO(new ShuttleFrontendBundle(outer))
  val io_reset_vector = outer.resetVectorSinkNode.bundle
  implicit val edge = outer.masterNode.edges.out(0)
  require(fetchWidth*coreInstBytes == outer.icacheParams.fetchBytes)

  val btbParams = tileParams.btb.get
  val icache = outer.icache.module
  icache.io.invalidate := io.cpu.flush_icache

  val tlb = Module(new TLB(true, log2Ceil(fetchBytes), TLBConfig(nTLBSets, nTLBWays)))
  io.ptw <> tlb.io.ptw
  val btb = Module(new ShuttleBTB)
  val ras = Reg(Vec(btbParams.nRAS, UInt(vaddrBitsExtended.W)))
  // TODO add RAS
  btb.io.flush := false.B
  btb.io.btb_update := io.cpu.btb_update
  btb.io.bht_update := io.cpu.bht_update

  // --------------------------------------------------------
  // **** NextPC Select (F0) ****
  //      Send request to ICache
  // -----------------------------

  val s0_vpc = WireInit(0.U(vaddrBitsExtended.W))
  val s0_valid = WireInit(false.B)
  val s0_ras_head = WireInit(0.U(log2Ceil(btbParams.nRAS).W))
  val s0_is_replay = WireInit(false.B)
  val s0_replay_resp = Wire(new TLBResp)
  val s0_replay_ppc = Wire(UInt(paddrBits.W))

  icache.io.req.valid := s0_valid
  icache.io.req.bits := s0_vpc

  // --------------------------------------------------------
  // **** ICache Access (F1) ****
  //      Translate VPC
  // --------------------------------------------------------
  val s1_vpc       = RegNext(s0_vpc)
  val s1_ras_head  = WireInit(RegNext(s0_ras_head))
  val s1_valid     = RegNext(s0_valid, false.B)
  val s1_is_replay = RegNext(s0_is_replay)
  val f1_clear     = WireInit(false.B)

  tlb.io.req.valid      := (s1_valid && !s1_is_replay && !f1_clear && !io.cpu.sfence.valid)
  tlb.io.req.bits.cmd   := DontCare
  tlb.io.req.bits.vaddr := Mux(io.cpu.sfence.valid, io.cpu.sfence.bits.addr, s1_vpc)
  tlb.io.req.bits.passthrough := false.B
  tlb.io.req.bits.size  := log2Ceil(coreInstBytes * fetchWidth).U
  tlb.io.req.bits.v     := io.ptw.status.v
  tlb.io.req.bits.prv   := io.ptw.status.prv

  tlb.io.sfence         := io.cpu.sfence
  tlb.io.kill           := false.B

  btb.io.req.valid := s1_valid && !io.cpu.sfence.valid
  btb.io.req.bits.addr := fetchAlign(s1_vpc)


  val s1_tlb_miss = !s1_is_replay && (tlb.io.resp.miss || io.cpu.sfence.valid)
  val s1_tlb_resp = Mux(s1_is_replay, RegNext(s0_replay_resp), tlb.io.resp)
  val s1_ppc  = Mux(s1_is_replay, RegNext(s0_replay_ppc), tlb.io.resp.paddr)

  icache.io.s1_paddr := s1_ppc
  icache.io.s1_kill  := tlb.io.resp.miss || f1_clear

  val f1_mask = fetchMask(s1_vpc)

  val f1_next_fetch = nextFetch(s1_vpc)
  val f1_do_redirect = btb.io.resp.valid && btb.io.resp.bits.taken
  val f1_predicted_target = Mux(f1_do_redirect,
    btb.io.resp.bits.target.sextTo(vaddrBitsExtended),
    f1_next_fetch)

  when (s1_valid) {
    // Stop fetching on fault
    s0_valid     := true.B
    s0_vpc       := f1_predicted_target
    s0_is_replay := false.B
    s0_ras_head  := s1_ras_head
  }

  btb.io.bht_advance.valid := s1_valid && btb.io.resp.valid
  btb.io.bht_advance.bits := btb.io.resp.bits

  // --------------------------------------------------------
  // **** ICache Response (F2) ****
  // --------------------------------------------------------

  val s2_valid = RegNext(s1_valid && !f1_clear, false.B)
  val s2_vpc   = RegNext(s1_vpc)
  val s2_ppc  = RegNext(s1_ppc)
  val s2_ras_head = RegNext(s1_ras_head)
  val f2_clear = WireInit(false.B)
  val s2_tlb_resp = RegNext(s1_tlb_resp)
  val s2_tlb_miss = RegNext(s1_tlb_miss)
  val s2_is_replay = RegNext(s1_is_replay) && s2_valid
  val s2_xcpt = s2_valid && (s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst) && !s2_is_replay
  val s2_btb_resp = RegNext(btb.io.resp)
  val f3_ready = Wire(Bool())

  icache.io.s2_kill := s2_xcpt

  val f2_fetch_mask = fetchMask(s2_vpc)
  val f2_next_fetch = RegNext(f1_next_fetch)


  val f2_aligned_pc = fetchAlign(s2_vpc)
  val f2_inst_mask  = Wire(Vec(fetchWidth, Bool()))
  val f2_call_mask  = Wire(Vec(fetchWidth, Bool()))
  val f2_ret_mask   = Wire(Vec(fetchWidth, Bool()))
  val f2_do_call = (f2_call_mask.asUInt & f2_inst_mask.asUInt) =/= 0.U
  val f2_do_ret  = (f2_ret_mask.asUInt & f2_inst_mask.asUInt) =/= 0.U
  val f2_npc_plus4_mask = Wire(Vec(fetchWidth, Bool()))

  val f2_do_redirect = WireInit(false.B)
  val f2_redirect_bridx = WireInit(0.U(log2Ceil(fetchWidth).W))
  val f2_predicted_target = Mux(f2_do_redirect,
    Mux(f2_do_ret, ras(s2_ras_head), RegNext(f1_predicted_target)),
    RegNext(f1_next_fetch))


  val ras_write_val = WireInit(false.B)
  val ras_write_idx = WireInit(0.U(log2Ceil(btbParams.nRAS).W))
  val ras_write_addr = WireInit(0.U(vaddrBitsExtended.W))
  val ras_next_head = Mux(f2_do_call, Mux(s2_ras_head === (btbParams.nRAS-1).U, 0.U, s2_ras_head + 1.U),
    Mux(f2_do_ret, Mux(s2_ras_head === 0.U, (btbParams.nRAS-1).U, s2_ras_head - 1.U), s2_ras_head))
  when ((f2_do_call || f2_do_ret) && s2_valid && f3_ready && icache.io.resp.valid) {
    s0_ras_head := ras_next_head
    s1_ras_head := ras_next_head
    when (f2_do_call) {
      ras_write_val := true.B
      ras_write_idx := ras_next_head
      ras_write_addr := f2_aligned_pc + (f2_redirect_bridx << 1) + Mux(f2_npc_plus4_mask(s2_btb_resp.bits.bridx), 4.U, 2.U)
    }
  }

  when (io.cpu.ras_update.valid || (RegNext(ras_write_val && !io.cpu.redirect_val) && !io.cpu.redirect_val) ) {
    val idx = Mux(io.cpu.ras_update.valid, io.cpu.ras_update.bits.head, RegNext(ras_write_idx))
    val addr = Mux(io.cpu.ras_update.valid, io.cpu.ras_update.bits.addr, RegNext(ras_write_addr))
    ras(idx) := addr
  }

  // Tracks trailing 16b of previous fetch packet
  val f2_prev_half = Reg(UInt(16.W))
  // Tracks if last fetchpacket contained a half-inst
  val f2_prev_is_half = RegInit(false.B)

  val f2_fetch_bundle = Wire(new ShuttleFetchBundle)
  f2_fetch_bundle            := DontCare
  f2_fetch_bundle.pc         := s2_vpc
  f2_fetch_bundle.next_pc.valid := f2_do_redirect
  f2_fetch_bundle.next_pc.bits := f2_predicted_target
  f2_fetch_bundle.xcpt_pf_if := s2_tlb_resp.pf.inst
  f2_fetch_bundle.xcpt_ae_if := s2_tlb_resp.ae.inst
  f2_fetch_bundle.mask       := f2_inst_mask.asUInt
  f2_fetch_bundle.btb_resp   := s2_btb_resp
  f2_fetch_bundle.ras_head   := s2_ras_head

  def isRVC(inst: UInt) = (inst(1,0) =/= 3.U)

  def isJALR(exp_inst: UInt) = exp_inst(6,0) === Instructions.JALR.value.asUInt(6,0)
  def isJump(exp_inst: UInt) = exp_inst(6,0) === Instructions.JAL.value.asUInt(6,0)
  def isCall(exp_inst: UInt) = (isJALR(exp_inst) || isJump(exp_inst)) && exp_inst(7)
  def isRet(exp_inst: UInt)  = isJALR(exp_inst) && !exp_inst(7) && BitPat("b00?01") === exp_inst(19,15)
  def isBr(exp_inst: UInt)  = exp_inst(6,0) === Instructions.BEQ.value.asUInt(6,0)

  val icache_data  = icache.io.resp.bits
  var redir_found = false.B
  for (i <- 0 until fetchWidth) {
    val valid = Wire(Bool())
    f2_inst_mask(i) := s2_valid && f2_fetch_mask(i) && valid && !redir_found
    f2_fetch_bundle.pcs(i) := f2_aligned_pc + (i << 1).U - ((f2_fetch_bundle.edge_inst && (i == 0).B) << 1)
    when (!valid && s2_btb_resp.valid && s2_btb_resp.bits.bridx === i.U) {
      btb.io.flush := true.B
    }
    f2_call_mask(i) := isCall(f2_fetch_bundle.exp_insts(i))
    f2_ret_mask(i)  := isRet(f2_fetch_bundle.exp_insts(i))
    f2_npc_plus4_mask(i) := !isRVC(f2_fetch_bundle.insts(i))
    if (i == 0)
      f2_npc_plus4_mask(i) := !isRVC(f2_fetch_bundle.insts(i)) && !f2_fetch_bundle.edge_inst
    val redir_br = (isBr(f2_fetch_bundle.exp_insts(i)) &&
      ((s2_btb_resp.valid && s2_btb_resp.bits.bridx === i.U && s2_btb_resp.bits.taken && s2_btb_resp.bits.bht.taken)))
    val redir = f2_inst_mask(i) && (isJALR(f2_fetch_bundle.exp_insts(i)) || isJump(f2_fetch_bundle.exp_insts(i)) || redir_br)
    when (redir) {
      f2_do_redirect := true.B
      f2_redirect_bridx := i.U
    }
    redir_found = redir_found || redir
    if (i == 0) {
      valid := true.B
      when (f2_prev_is_half) {
        val expanded = ExpandRVC(Cat(icache_data(15,0), f2_prev_half))
        f2_fetch_bundle.insts(i)     := Cat(icache_data(15,0), f2_prev_half)
        f2_fetch_bundle.exp_insts(i) := expanded
        f2_fetch_bundle.edge_inst    := true.B
      } .otherwise {
        val expanded = ExpandRVC(icache_data(31,0))
        f2_fetch_bundle.insts(i)     := icache_data(31,0)
        f2_fetch_bundle.exp_insts(i) := expanded
        f2_fetch_bundle.edge_inst    := false.B
      }
    } else if (i == 1) {
      // Need special case since 0th instruction may carry over the wrap around
      val inst = icache_data(47,16)
      val expanded = ExpandRVC(inst)
      f2_fetch_bundle.insts(i)     := inst
      f2_fetch_bundle.exp_insts(i) := expanded
      valid := f2_prev_is_half || !(f2_inst_mask(i-1) && !isRVC(f2_fetch_bundle.insts(i-1)))
    } else if (i == fetchWidth - 1) {
      val inst = Cat(0.U(16.W), icache_data(fetchWidth*16-1,(fetchWidth-1)*16))
      val expanded = ExpandRVC(inst)
      f2_fetch_bundle.insts(i)     := inst
      f2_fetch_bundle.exp_insts(i) := expanded
      valid := !((f2_inst_mask(i-1) && !isRVC(f2_fetch_bundle.insts(i-1))) || !isRVC(inst))
    } else {
      val inst = icache_data(i*16+32-1,i*16)
      val expanded = ExpandRVC(inst)
      f2_fetch_bundle.insts(i)     := inst
      f2_fetch_bundle.exp_insts(i) := expanded
      valid := !(f2_inst_mask(i-1) && !isRVC(f2_fetch_bundle.insts(i-1)))
    }
  }
  val last_inst = f2_fetch_bundle.insts(fetchWidth-1)(15,0)
  f2_fetch_bundle.end_half.valid := (!(f2_inst_mask(fetchWidth-2) && !isRVC(f2_fetch_bundle.insts(fetchWidth-2))) && !isRVC(last_inst))
  f2_fetch_bundle.end_half.bits := last_inst


  when ((s2_valid && !icache.io.resp.valid) ||
        (s2_valid && icache.io.resp.valid && !f3_ready)) {
    s0_valid := (!s2_tlb_resp.ae.inst && !s2_tlb_resp.pf.inst) || s2_is_replay || s2_tlb_miss || !f3_ready
    s0_vpc   := s2_vpc
    s0_ras_head := s2_ras_head
    s0_is_replay := s2_valid && icache.io.resp.valid
    f1_clear := true.B
  } .elsewhen (s2_valid && f3_ready) {
    f2_prev_is_half := f2_fetch_bundle.end_half.valid && !f2_do_redirect
    f2_prev_half    := f2_fetch_bundle.end_half.bits
    when ((s1_valid && (s1_vpc =/= f2_predicted_target)) || !s1_valid) {
      f1_clear := true.B

      s0_valid     := !((s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst) && !s2_is_replay)
      s0_vpc       := f2_predicted_target
      s0_ras_head  := ras_next_head
      s0_is_replay := false.B
    }
  }
  s0_replay_resp := s2_tlb_resp
  s0_replay_ppc  := s2_ppc

  val fb = Module(new ShuttleFetchBuffer)
  fb.io.enq.valid := (s2_valid && !f2_clear &&
    (icache.io.resp.valid || ((s2_tlb_resp.ae.inst || s2_tlb_resp.pf.inst) && !s2_tlb_miss))
  )
  fb.io.enq.bits := f2_fetch_bundle
  f3_ready := fb.io.enq.ready
  io.cpu.resp <> fb.io.deq
  io.cpu.peek := fb.io.peek
  fb.io.clear := false.B

  when (io.cpu.redirect_flush) {
    fb.io.clear := true.B
    f2_clear    := true.B
    f2_prev_is_half := false.B
    f1_clear    := true.B

    s0_valid     := io.cpu.redirect_val
    s0_vpc       := io.cpu.redirect_pc
    s0_ras_head  := io.cpu.redirect_ras_head
    s0_is_replay := false.B
  }

  val jump_to_reset = RegInit(true.B)

  when (jump_to_reset) {
    s0_valid := true.B
    s0_vpc   := io_reset_vector
    s0_ras_head := (btbParams.nRAS-1).U
    fb.io.clear := true.B
    f2_clear    := true.B
    f2_prev_is_half := false.B
    f1_clear    := true.B
    jump_to_reset := false.B
  }

  //dontTouch(io)
}
