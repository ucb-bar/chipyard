//******************************************************************************
// Copyright (c) 2017 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// ICache
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v4.ifu

import chisel3._
import chisel3.util._
import chisel3.util.random._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import freechips.rocketchip.rocket.{HasL1ICacheParameters, ICacheParams, ICacheErrors, ICacheReq}




import boom.v4.common._
import boom.v4.util.{BoomCoreStringPrefix}

/**
 * ICache module
 *
 * @param icacheParams parameters for the icache
 * @param hartId the id of the hardware thread in the cache
 * @param enableBlackBox use a blackbox icache
 */
class ICache(
  val icacheParams: ICacheParams,
  val tileId: Int)(implicit p: Parameters)
  extends LazyModule
{
  lazy val module = new ICacheModule(this)
  val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    sourceId = IdRange(0, 1 + icacheParams.prefetch.toInt), // 0=refill, 1=hint
    name = s"Core ${tileId} ICache")))))

  val size = icacheParams.nSets * icacheParams.nWays * icacheParams.blockBytes
  private val wordBytes = icacheParams.fetchBytes
}

/**
 * IO Signals leaving the ICache
 *
 * @param outer top level ICache class
 */
class ICacheResp(val outer: ICache) extends Bundle
{
  val data = UInt((outer.icacheParams.fetchBytes*8).W)
  val replay = Bool()
  val ae = Bool()
}

/**
 * IO Signals for interacting with the ICache
 *
 * @param outer top level ICache class
 */
class ICacheBundle(val outer: ICache) extends BoomBundle()(outer.p)
  with HasBoomFrontendParameters
{
  val req = Flipped(Decoupled(new ICacheReq))
  val s1_paddr = Input(UInt(paddrBits.W)) // delayed one cycle w.r.t. req

  val s1_kill = Input(Bool()) // delayed one cycle w.r.t. req
  val s2_kill = Input(Bool()) // delayed two cycles; prevents I$ miss emission
  val s2_prefetch = Input(Bool()) // should I$ prefetch next line on a miss?

  val resp = Valid(new ICacheResp(outer))
  val invalidate = Input(Bool())

  val perf = Output(new Bundle {
    val acquire = Bool()
  })
}

/**
 * Main ICache module
 *
 * @param outer top level ICache class
 */
class ICacheModule(outer: ICache) extends LazyModuleImp(outer)
  with HasBoomFrontendParameters
{
  override def tlBundleParams = outer.masterNode.out.head._2.bundle

  val enableICacheDelay = tileParams.core.asInstanceOf[BoomCoreParams].enableICacheDelay
  val icacheSinglePorted = tileParams.core.asInstanceOf[BoomCoreParams].icacheSinglePorted
  val io = IO(new ICacheBundle(outer))

  val (tl_out, edge_out) = outer.masterNode.out(0)

  require(isPow2(nSets) && isPow2(nWays))
  require(usingVM)
  require(pgIdxBits >= untagBits)

  // How many bits do we intend to fetch at most every cycle?
  val wordBits = outer.icacheParams.fetchBytes*8
  // Each of these cases require some special-case handling.
  require (tl_out.d.bits.data.getWidth == wordBits)

  val s0_valid = io.req.fire
  val s0_vaddr = io.req.bits.addr

  val s1_valid = RegNext(s0_valid, false.B)
  val s1_tag_hit = Wire(Vec(nWays, Bool()))
  val s1_hit = s1_tag_hit.reduce(_||_)
  val s2_valid = RegNext(s1_valid && !io.s1_kill, false.B)
  val s2_hit = RegNext(s1_hit)


  val invalidated = Reg(Bool())
  val refill_valid = RegInit(false.B)
  val refill_fire = tl_out.a.fire
  val s2_miss = s2_valid && !s2_hit && !RegNext(refill_valid)
  val refill_paddr = RegEnable(io.s1_paddr, s1_valid && !(refill_valid || s2_miss))
  val refill_tag = refill_paddr(tagBits+untagBits-1,untagBits)
  val refill_idx = refill_paddr(untagBits-1,blockOffBits)
  val refill_one_beat = tl_out.d.fire && edge_out.hasData(tl_out.d.bits)

  io.req.ready := !refill_one_beat

  val (_, _, d_done, refill_cnt) = edge_out.count(tl_out.d)
  val refill_done = refill_one_beat && d_done
  tl_out.d.ready := true.B
  require (edge_out.manager.minLatency > 0)

  val repl_way = if (isDM) 0.U else LFSR(16, refill_fire)(log2Ceil(nWays)-1,0)

  val tag_array = SyncReadMem(nSets, Vec(nWays, UInt(tagBits.W)))
  val tag_rdata = if (icacheSinglePorted) {
    tag_array.read(s0_vaddr(untagBits-1, blockOffBits), !refill_done && s0_valid)
  } else {
    tag_array.read(s0_vaddr(untagBits-1, blockOffBits), io.req.valid)
  }

  when (refill_done) {
    tag_array.write(refill_idx, VecInit(Seq.fill(nWays)(refill_tag)), Seq.tabulate(nWays)(repl_way === _.U))
  }

  val vb_array = RegInit(0.U((nSets*nWays).W))
  when (refill_one_beat) {
    vb_array := vb_array.bitSet(Cat(repl_way, refill_idx), refill_done && !invalidated)
  }

  when (io.invalidate) {
    vb_array := 0.U
    invalidated := true.B
  }

  val s2_dout   = Wire(Vec(nWays, UInt(wordBits.W)))
  val s1_bankid = Wire(Bool())

  for (i <- 0 until nWays) {
    val s1_idx = io.s1_paddr(untagBits-1,blockOffBits)
    val s1_tag = io.s1_paddr(tagBits+untagBits-1,untagBits)
    val s1_vb = vb_array(Cat(i.U, s1_idx))
    val tag = tag_rdata(i)
    s1_tag_hit(i) := s1_vb && tag === s1_tag
  }
  assert(PopCount(s1_tag_hit) <= 1.U || !s1_valid)

  val ramDepth = nSets * refillCycles

  val dataArrays = Seq.tabulate(nBanks) { b =>
    DescribedSRAM(
      name = s"dataArrayB${b}",
      desc = "ICache Data Array",
      size = ramDepth,
      data = Vec(nWays, UInt((wordBits/nBanks).W))
    )
  }
  if (nBanks == 1) {
    // Use unbanked icache for narrow accesses.
    s1_bankid := 0.U
    val array = dataArrays(0)
    def row(addr: UInt) = addr(untagBits-1, blockOffBits-log2Ceil(refillCycles))
    val s0_ren = s0_valid
    val wen = refill_one_beat && !invalidated

    val mem_idx = Mux(refill_one_beat, (refill_idx << log2Ceil(refillCycles)) | refill_cnt,
                      row(s0_vaddr))
    val wmask = UIntToOH(repl_way)(nWays-1,0).asBools
    when (wen) {
      array.write(mem_idx, VecInit(Seq.fill(nWays) { tl_out.d.bits.data }), wmask)
    }
    if (enableICacheDelay) {
      if (icacheSinglePorted)
        s2_dout := array.read(RegNext(mem_idx), RegNext(!wen && s0_ren))
      else
        s2_dout := array.read(RegNext(mem_idx), RegNext(s0_ren))
    } else {
      if (icacheSinglePorted)
        s2_dout := RegNext(array.read(mem_idx, !wen && s0_ren))
      else
        s2_dout := RegNext(array.read(mem_idx, io.req.valid))
    }
  } else {
    // Use two banks, interleaved.
    val array_0 = dataArrays(0)
    val array_1 = dataArrays(1)
    require (nBanks == 2)

    // Bank0 row's id wraps around if Bank1 is the starting bank.
    def b0Row(addr: UInt) =
      addr(untagBits-1, blockOffBits-log2Ceil(refillCycles)) + bank(addr)
    // Bank1 row's id stays the same regardless of which Bank has the fetch address.
    def b1Row(addr: UInt) =
      addr(untagBits-1, blockOffBits-log2Ceil(refillCycles))

    s1_bankid := RegNext(bank(s0_vaddr))

    val s0_ren = s0_valid
    val wen = (refill_one_beat && !invalidated)
    val wmask = UIntToOH(repl_way)(nWays-1,0).asBools


    var mem_idx0: UInt = null
    var mem_idx1: UInt = null

    // write a refill beat across both banks.
    mem_idx0 =
      Mux(refill_one_beat, (refill_idx << log2Ceil(refillCycles)) | refill_cnt,
        b0Row(s0_vaddr))
    mem_idx1 =
      Mux(refill_one_beat, (refill_idx << log2Ceil(refillCycles)) | refill_cnt,
        b1Row(s0_vaddr))

    when (wen) {
      val data = tl_out.d.bits.data
      val wdata_0 = VecInit(Seq.fill(nWays) { data(wordBits/2-1, 0) })
      val wdata_1 = VecInit(Seq.fill(nWays) { data(wordBits-1, wordBits/2) })
      array_0.write(mem_idx0, wdata_0, wmask)
      array_1.write(mem_idx1, wdata_1, wmask)
    }
    val rdata_0 = Wire(Vec(nWays, UInt((wordBits/nBanks).W)))
    val rdata_1 = Wire(Vec(nWays, UInt((wordBits/nBanks).W)))

    if (enableICacheDelay) {
      if (icacheSinglePorted) {
        rdata_0 := array_0.read(RegNext(mem_idx0), RegNext(!wen && s0_ren))
        rdata_1 := array_1.read(RegNext(mem_idx1), RegNext(!wen && s0_ren))
      } else {
        rdata_0 := array_0.read(RegNext(mem_idx0), RegNext(s0_ren))
        rdata_1 := array_1.read(RegNext(mem_idx1), RegNext(s0_ren))
      }
    } else {
      if (icacheSinglePorted) {
        rdata_0 := RegNext(array_0.read(mem_idx0, !wen && s0_ren))
        rdata_1 := RegNext(array_1.read(mem_idx1, !wen && s0_ren))
      } else {
        rdata_0 := RegNext(array_0.read(mem_idx0, io.req.valid))
        rdata_1 := RegNext(array_1.read(mem_idx1, io.req.valid))
      }
    }

    for (w <- 0 until nWays) {
      s2_dout(w) := Cat(rdata_1(w), rdata_0(w))
    }
  }
  val s2_tag_hit = RegNext(s1_tag_hit)
  val s2_hit_way = OHToUInt(s2_tag_hit)
  val s2_bankid = RegNext(s1_bankid)
  val s2_way_mux = Mux1H(s2_tag_hit, s2_dout)

  val s2_unbanked_data = s2_way_mux
  val sz = s2_way_mux.getWidth
  val s2_bank0_data = s2_way_mux(sz/2-1,0)
  val s2_bank1_data = s2_way_mux(sz-1,sz/2)

  val s2_data =
    if (nBanks == 2) {
      Mux(s2_bankid,
        Cat(s2_bank0_data, s2_bank1_data),
        Cat(s2_bank1_data, s2_bank0_data))
    } else {
      s2_unbanked_data
    }

  io.resp.bits.data := s2_data
  io.resp.bits.ae := DontCare
  io.resp.bits.replay := DontCare
  io.resp.valid := s2_valid && s2_hit

  tl_out.a.valid := s2_miss && !refill_valid && !io.s2_kill
  tl_out.a.bits := edge_out.Get(
    fromSource = 0.U,
    toAddress = (refill_paddr >> blockOffBits) << blockOffBits,
    lgSize = lgCacheBlockBytes.U)._2
  tl_out.b.ready := true.B
  tl_out.c.valid := false.B
  tl_out.e.valid := false.B

  io.perf.acquire := tl_out.a.fire

  when (!refill_valid) { invalidated := false.B }
  when (refill_fire) { refill_valid := true.B }
  when (refill_done) { refill_valid := false.B }

  override def toString: String = BoomCoreStringPrefix(
    "==L1-ICache==",
    "Fetch bytes   : " + cacheParams.fetchBytes,
    "Block bytes   : " + (1 << blockOffBits),
    "Word bits     : " + wordBits,
    "Sets          : " + nSets,
    "Ways          : " + nWays,
    "Refill cycles : " + refillCycles,
    "RAMs          : (" +  wordBits/nBanks + " x " + nSets*refillCycles + ") using " + nBanks + " banks",
    "" + (if (nBanks == 2) "Dual-banked" else "Single-banked"),
    "I-TLB ways    : " + cacheParams.nTLBWays + "\n")
}


