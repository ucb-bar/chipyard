package boom.v4.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v4.common._
import boom.v4.util.{BoomCoreStringPrefix}

import scala.math.min

case class BoomSlowBTBParams(
  nSets: Int = 128,
  nWays: Int = 2,
  offsetSz: Int = 13,
  extendedNSets: Int = 128,
  singlePorted: Boolean = false
)


class SlowBTBBranchPredictorBank(params: BoomSlowBTBParams = BoomSlowBTBParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  override val nSets         = params.nSets
  override val nWays         = params.nWays
  val tagSz         = vaddrBitsExtended - log2Ceil(nSets) - log2Ceil(fetchWidth) - 1
  val offsetSz      = params.offsetSz
  val extendedNSets = params.extendedNSets

  require(isPow2(nSets))
  require(isPow2(extendedNSets) || extendedNSets == 0)
  require(extendedNSets <= nSets)
  require(extendedNSets >= 1)

  class BTBEntry extends Bundle {
    val offset   = SInt(offsetSz.W)
    val extended = Bool()
  }
  val btbEntrySz = offsetSz + 1

  class BTBMeta extends Bundle {
    val is_br = Bool()
    val tag   = UInt(tagSz.W)
  }
  val btbMetaSz = tagSz + 1

  class BTBPredictMeta extends Bundle {
    val write_way = UInt(log2Ceil(nWays).W)
  }

  val s2_meta = Wire(new BTBPredictMeta)
  val f3_meta = RegNext(s2_meta)


  io.f3_meta := f3_meta.asUInt

  override val metaSz = s2_meta.asUInt.getWidth

  val doing_reset = RegInit(true.B)
  val reset_idx   = RegInit(0.U(log2Ceil(nSets).W))
  reset_idx := reset_idx + doing_reset
  when (reset_idx === (nSets-1).U) { doing_reset := false.B }


  val mems = (((0 until nWays) map ({w:Int => Seq(
    (f"btb_meta_way$w", nSets, bankWidth * btbMetaSz),
    (f"btb_data_way$w", nSets, bankWidth * btbEntrySz))})).flatten ++ Seq(("ebtb", extendedNSets, vaddrBitsExtended)))
  val s2_req_rmeta = Wire(Vec(nWays, Vec(bankWidth, new BTBMeta)))
  val s2_req_rbtb  = Wire(Vec(nWays, Vec(bankWidth, new BTBEntry)))
  val s2_req_rebtb = Wire(UInt(vaddrBitsExtended.W))

  val s1_req_tag   = s1_idx >> log2Ceil(nSets)
  val s2_req_tag   = RegNext(s1_req_tag)

  val s2_resp   = Wire(Vec(bankWidth, Valid(UInt(vaddrBitsExtended.W))))
  val s2_is_br  = Wire(Vec(bankWidth, Bool()))
  val s2_is_jal = Wire(Vec(bankWidth, Bool()))

  val s2_hit_ohs = VecInit((0 until bankWidth) map { i =>
    VecInit((0 until nWays) map { w =>
      s2_req_rmeta(w)(i).tag === s2_req_tag(tagSz-1,0)
    })
  })
  val s2_hits     = s2_hit_ohs.map { oh => oh.reduce(_||_) }
  val s2_hit_ways = s2_hit_ohs.map { oh => PriorityEncoder(oh) }
  val s2_targs    = Wire(Vec(nWays, Vec(bankWidth, UInt(vaddrBitsExtended.W))))
  for (w <- 0 until bankWidth) {
    for (b <- 0 until nWays) {
      val entry_btb = WireInit(s2_req_rbtb(b)(w))
      s2_targs(b)(w) := Mux(entry_btb.extended,
        s2_req_rebtb,
        (s2_pc.asSInt + (w << 1).S + entry_btb.offset).asUInt)
    }

    val entry_meta = s2_req_rmeta(s2_hit_ways(w))(w)
    s2_resp(w).valid := !doing_reset && s2_valid && s2_hits(w)
    s2_resp(w).bits  := s2_targs(s2_hit_ways(w))(w)
    s2_is_br(w)  := !doing_reset && s2_resp(w).valid &&  entry_meta.is_br
    s2_is_jal(w) := !doing_reset && s2_resp(w).valid && !entry_meta.is_br

    io.resp.f1(w) := io.resp_in(0).f1(w)
    io.resp.f2(w) := io.resp_in(0).f2(w)
    io.resp.f3(w) := io.resp_in(0).f3(w)
    when (RegNext(s2_hits(w))) {
      io.resp.f3(w).predicted_pc := RegNext(s2_resp(w))
      //io.resp.f3(w).is_br        := RegNext(s2_is_br(w))
      //io.resp.f3(w).is_jal       := RegNext(s2_is_jal(w))
      // when (RegNext(s2_is_jal(w))) {
      //   io.resp.f3(w).taken      := true.B
      // }
    }
  }

  val alloc_way = if (nWays > 1) {
    val r_metas = Cat(VecInit(s2_req_rmeta.map { w => VecInit(w.map(_.tag)) }).asUInt, s2_req_tag(tagSz-1,0))
    val l = log2Ceil(nWays)
    val nChunks = (r_metas.getWidth + l - 1) / l
    val chunks = (0 until nChunks) map { i =>
      r_metas(min((i+1)*l, r_metas.getWidth)-1, i*l)
    }
    chunks.reduce(_^_)
  } else {
    0.U
  }
  s2_meta.write_way := Mux(s2_hits.reduce(_||_),
    PriorityEncoder(s2_hit_ohs.map(_.asUInt).reduce(_|_)),
    alloc_way)

  val s1_update_cfi_idx = s1_update.bits.cfi_idx.bits
  val s1_update_meta    = s1_update.bits.meta.asTypeOf(new BTBPredictMeta)

  val max_offset_value = Cat(0.B, ~(0.U((offsetSz-1).W))).asSInt
  val min_offset_value = Cat(1.B,  (0.U((offsetSz-1).W))).asSInt
  val new_offset_value = (s1_update.bits.target.asSInt -
    (s1_update.bits.pc + (s1_update.bits.cfi_idx.bits << 1)).asSInt)
  val offset_is_extended = (new_offset_value > max_offset_value ||
                            new_offset_value < min_offset_value)


  val s1_update_wbtb_data  = Wire(new BTBEntry)
  s1_update_wbtb_data.extended := offset_is_extended
  s1_update_wbtb_data.offset   := new_offset_value
  val s1_update_wbtb_mask = (UIntToOH(s1_update_cfi_idx) &
    Fill(bankWidth, s1_update.bits.cfi_idx.valid && s1_update.valid && s1_update.bits.cfi_taken && s1_update.bits.is_commit_update))

  val s1_update_wmeta_mask = ((s1_update_wbtb_mask | s1_update.bits.br_mask) &
    (Fill(bankWidth, s1_update.valid && s1_update.bits.is_commit_update) |
     (Fill(bankWidth, s1_update.valid) & s1_update.bits.btb_mispredicts)
    )
  )
  val s1_update_wmeta_data = Wire(Vec(bankWidth, new BTBMeta))

  for (w <- 0 until bankWidth) {
    s1_update_wmeta_data(w).tag     := Mux(s1_update.bits.btb_mispredicts(w), 0.U, s1_update_idx >> log2Ceil(nSets))
    s1_update_wmeta_data(w).is_br   := s1_update.bits.br_mask(w)
  }


  val ebtb = SyncReadMem(extendedNSets, UInt(vaddrBitsExtended.W))

  val ewen = WireInit(s1_update_wbtb_mask =/= 0.U && offset_is_extended)
  val rebtb = if (params.singlePorted) ebtb.read(s1_idx, s1_valid && !ewen) else ebtb.read(s1_idx, s1_valid)
  s2_req_rebtb := rebtb
  when (ewen) {
    ebtb.write(s1_update_idx, s1_update.bits.target)
  }

  for (w <- 0 until nWays) {
    val meta = SyncReadMem(nSets, Vec(bankWidth, UInt(btbMetaSz.W)))
    val btb  = SyncReadMem(nSets, Vec(bankWidth, UInt(btbEntrySz.W)))
    meta.suggestName(s"slowbtb_meta_way_${w}")
    btb.suggestName(s"slowbtb_data_way_${w}")


    val btbwen  = WireInit(doing_reset || ((s1_update_meta.write_way === w.U || (nWays == 1).B) && s1_update_wbtb_mask =/= 0.U))
    val metawen = WireInit(doing_reset || ((s1_update_meta.write_way === w.U || (nWays == 1).B) && s1_update_wmeta_mask =/= 0.U))

    val rbtb = if (params.singlePorted) btb.read(s1_idx, s1_valid && !btbwen) else btb.read(s1_idx, s1_valid)
    s2_req_rbtb(w) := VecInit(rbtb.map(_.asTypeOf(new BTBEntry)))

    when (btbwen) {
      val widx = Mux(doing_reset, reset_idx, s1_update_idx)
      val wdata = Mux(doing_reset, VecInit(Seq.fill(bankWidth) { 0.U(btbEntrySz.W) }),
                                   VecInit(Seq.fill(bankWidth) { s1_update_wbtb_data.asUInt }))
      val wmask = Mux(doing_reset, ~(0.U(bankWidth.W)), s1_update_wbtb_mask)
      btb.write(widx, wdata, wmask.asBools)
    }

    val rmeta = if (params.singlePorted) meta.read(s1_idx, s1_valid && !metawen) else meta.read(s1_idx, s1_valid)
    when (RegNext(btbwen || metawen) && params.singlePorted.B) {
      s2_req_rmeta(w) := 0.U.asTypeOf(Vec(bankWidth, new BTBMeta))
    } .otherwise {
      s2_req_rmeta(w) := VecInit(rmeta.map(_.asTypeOf(new BTBMeta)))
    }
    when (metawen) {
      val widx = Mux(doing_reset, reset_idx, s1_update_idx)
      val wdata = Mux(doing_reset, VecInit(Seq.fill(bankWidth) { 0.U(btbMetaSz.W) }),
                                   VecInit(s1_update_wmeta_data.map(_.asUInt)))
      val wmask = Mux(doing_reset, ~(0.U(bankWidth.W)), s1_update_wmeta_mask)
      meta.write(widx, wdata, wmask.asBools)
    }
  }

}

