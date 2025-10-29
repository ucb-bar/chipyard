package shuttle.ifu

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

class ShuttleICache(
  val icacheParams: ICacheParams,
  val staticIdForMetadataUseOnly: Int)(implicit p: Parameters)
    extends LazyModule
{
  lazy val module = new ShuttleICacheModule(this)
  val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    sourceId = IdRange(0, 1 + icacheParams.prefetch.toInt), // 0=refill, 1=hint
    name = s"Core ${staticIdForMetadataUseOnly} ICache")))))

  val size = icacheParams.nSets * icacheParams.nWays * icacheParams.blockBytes
  private val wordBytes = icacheParams.fetchBytes
}


class ShuttleICacheModule(outer: ShuttleICache) extends LazyModuleImp(outer)
  with HasL1ICacheParameters
{
  override val cacheParams = outer.icacheParams
  override def tlBundleParams = outer.masterNode.out.head._2.bundle

  val io = IO(new Bundle {
    val req = Flipped(new DecoupledIO(UInt(vaddrBits.W)))
    val s1_kill = Input(Bool())
    val s2_kill = Input(Bool())
    val s1_paddr = Input(UInt(paddrBits.W))
    val invalidate = Input(Bool())
    val resp = Output(Valid(UInt((outer.icacheParams.fetchBytes*8).W)))
  })
  val (tl_out, edge_out) = outer.masterNode.out(0)
  require(isPow2(nSets) && isPow2(nWays))
  require(usingVM && pgIdxBits >= untagBits)

  val s0_valid = io.req.fire
  val s0_vaddr = io.req.bits

  val s1_valid = RegInit(false.B)
  val s1_vaddr = RegEnable(s0_vaddr, s0_valid)
  val s1_tag_hit = Wire(Vec(nWays, Bool()))
  val s1_hit = s1_tag_hit.reduce(_||_)
  val s2_valid = RegNext(s1_valid && !io.s1_kill, false.B)
  val s2_hit = RegNext(s1_hit)

  val invalidated = Reg(Bool())
  val refill_valid = RegInit(false.B)
  val send_hint = RegInit(false.B)
  val refill_fire = tl_out.a.fire && !send_hint
  val hint_outstanding = RegInit(false.B)
  val s2_miss = s2_valid && !s2_hit && !io.s2_kill
  val s1_can_request_refill = !(s2_miss || refill_valid)
  val s2_request_refill = s2_miss && RegNext(s1_can_request_refill)
  val refill_paddr = RegEnable(io.s1_paddr, s1_valid && s1_can_request_refill)
  val refill_vaddr = RegEnable(s1_vaddr, s1_valid && s1_can_request_refill)
  val refill_tag = refill_paddr >> pgUntagBits
  val refill_idx = index(refill_vaddr, refill_paddr)
  val refill_one_beat = tl_out.d.fire && edge_out.hasData(tl_out.d.bits)

  io.req.ready := !refill_one_beat
  s1_valid := s0_valid

  val (_, _, d_done, refill_cnt) = edge_out.count(tl_out.d)
  val refill_done = refill_one_beat && d_done
  tl_out.d.ready := true.B
  require (edge_out.manager.minLatency > 0)

  val repl_way = if (isDM) 0.U(1.W) else {
    LFSR(16, refill_fire)(log2Up(nWays)-1,0)
  }

  val tag_array = DescribedSRAM(
    name = "tag_array",
    desc = "ICache Tag Array",
    size = nSets,
    data = Vec(nWays, UInt(tagBits.W))
  )
  val tag_rdata = tag_array.read(s0_vaddr(untagBits-1,blockOffBits), !refill_done && s0_valid)

  when (refill_done) {
    val enc_tag = refill_tag
    tag_array.write(refill_idx, VecInit(Seq.fill(nWays)(enc_tag)), Seq.tabulate(nWays)(repl_way === _.U))
  }

  val vb_array = RegInit(0.U((nSets*nWays).W))
  when (refill_one_beat) {
    // clear bit when refill starts so hit-under-miss doesn't fetch bad data
    vb_array := vb_array.bitSet(Cat(repl_way, refill_idx), refill_done && !invalidated)
  }
  val invalidate = WireInit(io.invalidate)
  when (invalidate) {
    vb_array := 0.U
    invalidated := true.B
  }

  val wordBits = outer.icacheParams.fetchBytes*8
  val s1_dout = Wire(Vec(nWays, UInt(wordBits.W)))
  s1_dout := DontCare

  for (i <- 0 until nWays) {
    val s1_idx = index(s1_vaddr, io.s1_paddr)
    val s1_tag = io.s1_paddr >> pgUntagBits
    val s1_vb = vb_array(Cat(i.U, s1_idx))
    val enc_tag = tag_rdata(i)
    val tag = enc_tag
    val tagMatch = s1_vb && tag === s1_tag
    s1_tag_hit(i) := tagMatch
  }
  assert(!s1_valid || PopCount(s1_tag_hit) <= 1.U)

  require(tl_out.d.bits.data.getWidth % wordBits == 0)

  override val refillCycles = cacheBlockBytes * 8 / tl_out.d.bits.data.getWidth
  val data_arrays = Seq.tabulate(tl_out.d.bits.data.getWidth / wordBits) {
    i =>
      DescribedSRAM(
        name = s"data_arrays_${i}",
        desc = "ICache Data Array",
        size = nSets * refillCycles,
        data = Vec(nWays, UInt(wordBits.W))
      )
  }

  for ((data_array, i) <- data_arrays.zipWithIndex) {
    def wordMatch(addr: UInt) = addr.extract(log2Ceil(tl_out.d.bits.data.getWidth/8)-1, log2Ceil(wordBits/8)) === i.U
    def row(addr: UInt) = addr(untagBits-1, blockOffBits-log2Ceil(refillCycles))
    val s0_ren = s0_valid && wordMatch(s0_vaddr)
    val wen = (refill_one_beat && !invalidated)
    val mem_idx = Mux(refill_one_beat, (refill_idx << log2Ceil(refillCycles)) | refill_cnt,
                  row(s0_vaddr))
    when (wen) {
      val data = tl_out.d.bits.data(wordBits*(i+1)-1, wordBits*i)
      val way = repl_way
      data_array.write(mem_idx, VecInit(Seq.fill(nWays)(data)), (0 until nWays).map(way === _.U))
    }
    val dout = data_array.read(mem_idx, !wen && s0_ren)
    when (wordMatch(io.s1_paddr)) {
      s1_dout := dout
    }
  }

  val s1_clk_en = s1_valid
  val s2_tag_hit = RegEnable(s1_tag_hit, s1_clk_en)
  val s2_hit_way = OHToUInt(s2_tag_hit)
  val s2_dout = RegEnable(s1_dout, s1_clk_en)
  val s2_way_mux = Mux1H(s2_tag_hit, s2_dout)

  val s2_data_decoded = s2_way_mux
  val s2_full_word_write = WireInit(false.B)

  require(outer.icacheParams.latency == 2)
  // when some sort of memory bit error have occurred

  io.resp.bits := s2_data_decoded
  io.resp.valid := s2_valid && s2_hit

  tl_out.a.valid := s2_request_refill
  tl_out.a.bits := edge_out.Get(
                    fromSource = 0.U,
                    toAddress = (refill_paddr >> blockOffBits) << blockOffBits,
                    lgSize = lgCacheBlockBytes.U)._2


  if (cacheParams.prefetch) {
    val (crosses_page, next_block) = Split(refill_paddr(pgIdxBits-1, blockOffBits) +& 1.U, pgIdxBits-blockOffBits)
    when (tl_out.a.fire) {
      send_hint := !hint_outstanding && !crosses_page
      when (send_hint) {
        send_hint := false.B
        hint_outstanding := true.B
      }
    }
    when (refill_done) {
      send_hint := false.B
    }
    when (tl_out.d.fire && !refill_one_beat) {
      hint_outstanding := false.B
    }

    when (send_hint) {
      tl_out.a.valid := true.B
      tl_out.a.bits := edge_out.Hint(
        fromSource = 1.U,
        toAddress = Cat(refill_paddr >> pgIdxBits, next_block) << blockOffBits,
        lgSize = lgCacheBlockBytes.U,
        param = TLHints.PREFETCH_READ)._2
    }
  }

  tl_out.b.ready := true.B
  tl_out.c.valid := false.B
  tl_out.e.valid := false.B


  when (!refill_valid) { invalidated := false.B }
  when (refill_fire) { refill_valid := true.B }
  when (refill_done) { refill_valid := false.B}

  def index(vaddr: UInt, paddr: UInt) = {
    val lsbs = paddr(pgUntagBits-1, blockOffBits)
    val msbs = (idxBits+blockOffBits > pgUntagBits).option(vaddr(idxBits+blockOffBits-1, pgUntagBits))
    msbs ## lsbs
  }

  //dontTouch(io)
}
