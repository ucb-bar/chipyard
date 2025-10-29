package boom.v4.ifu

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v4.common._
import boom.v4.util.{BoomCoreStringPrefix, WrapInc}

import scala.math.min

case class BoomFA2MicroBTBParams(
  nWays: Int = 24,
  tagSz: Int = 12,
  useDualEntries: Boolean = true
)


class FA2MicroBTBBranchPredictorBank(params: BoomFA2MicroBTBParams = BoomFA2MicroBTBParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  override val nWays         = params.nWays
  val tagSz         = params.tagSz
  require(tagSz <= vaddrBitsExtended - log2Ceil(fetchWidth) - 1)
  val nWrBypassEntries = 2
  val useDualEntries = params.useDualEntries

  def bimWrite(v: UInt, taken: Bool): UInt = {
    val old_bim_sat_taken  = v === 3.U
    val old_bim_sat_ntaken = v === 0.U
    Mux(old_bim_sat_taken  &&  taken, 3.U,
      Mux(old_bim_sat_ntaken && !taken, 0.U,
      Mux(taken, v + 1.U, v - 1.U)))
  }


  class MicroBTBMeta extends Bundle {
    val tag   = UInt(tagSz.W)
    val ctr   = UInt(2.W)
    val cfi_idx = UInt(log2Ceil(bankWidth).W)
    val br_mask = UInt(bankWidth.W)
    val jal_mask = UInt(bankWidth.W)
  }

  class MicroBTBPredictMeta extends Bundle {
    val hit = Bool()
    //val hits  = Vec(bankWidth, Bool())
    //val write_way = UInt(log2Ceil(nWays).W)
  }

  val s1_meta = Wire(new MicroBTBPredictMeta)
  override val metaSz = s1_meta.asUInt.getWidth

  val valids   = RegInit(VecInit(0.U(nWays.W).asBools))
  val meta     = Reg(Vec(nWays, new MicroBTBMeta))
  val btb      = Reg(Vec(nWays, Vec(if (useDualEntries) 2 else 1, UInt(vaddrBitsExtended.W))))

  val mems = Nil

  val s1_req_tag   = s1_idx


  val s1_resp   = Wire(Vec(bankWidth, Valid(UInt(vaddrBitsExtended.W))))
  val s1_taken  = Wire(Vec(bankWidth, Bool()))
  val s1_is_br  = Wire(Vec(bankWidth, Bool()))
  val s1_is_jal = Wire(Vec(bankWidth, Bool()))

  val s1_hit_oh = (0 until nWays) map { w => valids(w) && meta(w).tag === s1_req_tag(tagSz-1,0) }
  val s1_hit = s1_hit_oh.reduce(_||_) && s1_valid

  val s1_hit_meta = Mux1H(s1_hit_oh, meta)
  val s1_hit_btb = Mux1H(s1_hit_oh, btb)
  for (w <- 0 until bankWidth) {
    val is_cfi = s1_hit_meta.cfi_idx === w.U
    s1_resp(w).valid := s1_hit && ((is_cfi && s1_hit_meta.br_mask(w)) || s1_hit_meta.jal_mask(w))
    s1_resp(w).bits  := (if (useDualEntries) s1_hit_btb(s1_hit_meta.br_mask(w)) else s1_hit_btb(0))
    s1_is_br(w)      := s1_hit && s1_hit_meta.br_mask(w)
    s1_is_jal(w)     := s1_hit && s1_hit_meta.jal_mask(w)
    s1_taken(w)      := s1_hit && (s1_is_jal(w) || s1_hit_meta.ctr(1))
  }
  s1_meta.hit := s1_hit

  // val s1_hit_ohs = (0 until bankWidth) map { i =>
  //   (0 until nWays) map { w =>
  //     meta(w)(i).tag === s1_req_tag(tagSz-1,0)
  //   }
  // }
  // val s1_hits     = s1_hit_ohs.map { oh => oh.reduce(_||_) }
  // val s1_hit_ways = s1_hit_ohs.map { oh => OHToUInt(oh) }
  // val s1_hit_multi = VecInit(s1_hit_ohs.map { oh => PopCount(oh) > 1.U })
  // dontTouch(s1_hit_multi)

  // for (w <- 0 until bankWidth) {
  //   val entry_meta = Mux1H(s1_hit_ohs(w), meta)(w)
  //   s1_resp(w).valid := s1_valid && s1_hits(w) && !s1_hit_multi(w)
  //   s1_resp(w).bits  := Mux1H(s1_hit_ohs(w), btb)(w)
  //   s1_is_br(w)      := s1_resp(w).valid &&  entry_meta.is_br
  //   s1_is_jal(w)     := s1_resp(w).valid && !entry_meta.is_br
  //   s1_taken(w)      := !entry_meta.is_br || entry_meta.ctr(1)

  //   s1_meta.hits(w)     := s1_hits(w)
  // }
  // val alloc_way = {
  //   val r_metas = Cat(VecInit(meta.map(e => VecInit(e.map(_.tag)))).asUInt, s1_idx(tagSz-1,0))
  //   val l = log2Ceil(nWays)
  //   val nChunks = (r_metas.getWidth + l - 1) / l
  //   val chunks = (0 until nChunks) map { i =>
  //     r_metas(min((i+1)*l, r_metas.getWidth)-1, i*l)
  //   }
  //   chunks.reduce(_^_)
  // }
  // s1_meta.write_way := Mux(s1_hits.reduce(_||_),
  //   PriorityEncoder(s1_hit_ohs.map(_.asUInt).reduce(_|_)),
  //   alloc_way)

  for (w <- 0 until bankWidth) {
    io.resp.f1(w).predicted_pc := s1_resp(w)
    io.resp.f1(w).is_br        := s1_is_br(w)
    io.resp.f1(w).is_jal       := s1_is_jal(w)
    io.resp.f1(w).taken        := s1_taken(w)

    io.resp.f2(w) := RegNext(io.resp.f1(w))
    io.resp.f3(w) := RegNext(io.resp.f2(w))
  }
  io.f3_meta := RegNext(RegNext(s1_meta.asUInt))

  val s1_update_hit_oh = (0 until nWays) map { w =>
    meta(w).tag === s1_update_idx(tagSz-1,0) && valids(w)
  }
  val s1_update_hit = s1_update_hit_oh.reduce(_||_)
  val s1_update_write_way = Mux(s1_update_hit, OHToUInt(s1_update_hit_oh), LFSR(16))

  when (s1_update.valid && s1_update.bits.is_commit_update) {
    when (s1_update.bits.cfi_idx.valid) {
      if (useDualEntries)
        btb(s1_update_write_way)(s1_update.bits.cfi_is_br)  := s1_update.bits.target
      else
        btb(s1_update_write_way)(0) := s1_update.bits.target
      valids(s1_update_write_way) := true.B
    }
    val rmeta = meta(s1_update_write_way)
    val wmeta = Wire(new MicroBTBMeta)
    wmeta.tag := s1_update_idx
    when (!s1_update_hit) {
      wmeta.br_mask := s1_update.bits.br_mask
      wmeta.ctr := Mux(s1_update.bits.cfi_idx.valid && s1_update.bits.cfi_taken, 3.U, 0.U)
      wmeta.jal_mask := UIntToOH(s1_update.bits.cfi_idx.bits) & Fill(bankWidth, s1_update.bits.cfi_idx.valid && (s1_update.bits.cfi_is_jal || s1_update.bits.cfi_is_jalr))
    } .otherwise {
      wmeta.br_mask := rmeta.br_mask | s1_update.bits.br_mask
      wmeta.ctr := Mux(s1_update.bits.cfi_idx.valid,
        Mux(s1_update.bits.cfi_idx.bits === rmeta.cfi_idx, bimWrite(rmeta.ctr, s1_update.bits.cfi_taken),
                                                           Mux(s1_update.bits.cfi_taken, 3.U, 0.U)),
        Mux(s1_update.bits.br_mask(rmeta.cfi_idx)        , bimWrite(rmeta.ctr, false.B),
                                                           rmeta.ctr)
      )
      wmeta.jal_mask := rmeta.jal_mask | UIntToOH(s1_update.bits.cfi_idx.bits) & Fill(bankWidth, s1_update.bits.cfi_idx.valid && (s1_update.bits.cfi_is_jal || s1_update.bits.cfi_is_jalr))
    }
    wmeta.cfi_idx := Mux(s1_update.bits.cfi_idx.valid, s1_update.bits.cfi_idx.bits, rmeta.cfi_idx)
    when (s1_update.bits.cfi_idx.valid || s1_update.bits.br_mask =/= 0.U) {
      meta(s1_update_write_way) := wmeta
    }
  }



  // val s1_update_cfi_idx = s1_update.bits.cfi_idx.bits
  // val s1_update_meta    = s1_update.bits.meta.asTypeOf(new MicroBTBPredictMeta)



  // val s1_update_wbtb_data = s1_update.bits.target
  // val s1_update_wbtb_mask = (UIntToOH(s1_update_cfi_idx) &
  //   Fill(bankWidth, s1_update.bits.cfi_idx.valid && s1_update.valid && s1_update.bits.cfi_taken && s1_update.bits.is_commit_update))

  // val s1_update_wmeta_mask = ((s1_update_wbtb_mask | s1_update.bits.br_mask) &
  //   Fill(bankWidth, s1_update.valid && s1_update.bits.is_commit_update))

  // val s1_update_hit_ohs = (0 until bankWidth) map { i =>
  //   (0 until nWays) map { w =>
  //     meta(w)(i).tag === s1_update_idx(tagSz-1,0)
  //   }
  // }
  // val s1_update_hits = s1_update_hit_ohs.map { oh => oh.reduce(_||_) }
  // val s1_update_write_way = VecInit((0 until bankWidth) map { i =>
  //   Mux(s1_update_hits(i), OHToUInt(s1_update_hit_ohs(i)), LFSR(16))
  // })

  // // Write the BTB with the target
  // when (s1_update.valid && s1_update.bits.cfi_taken && s1_update.bits.cfi_idx.valid && s1_update.bits.is_commit_update) {
  //   btb(s1_update_write_way(s1_update_cfi_idx))(s1_update_cfi_idx) := s1_update_wbtb_data
  // }

  // // Write the meta
  // for (w <- 0 until bankWidth) {
  //   when (s1_update.valid && s1_update.bits.is_commit_update &&
  //     (s1_update.bits.br_mask(w) ||
  //       (s1_update_cfi_idx === w.U && s1_update.bits.cfi_taken && s1_update.bits.cfi_idx.valid))) {
  //     val was_taken = (s1_update_cfi_idx === w.U && s1_update.bits.cfi_idx.valid &&
  //       (s1_update.bits.cfi_taken || s1_update.bits.cfi_is_jal))

  //     meta(s1_update_write_way(w))(w).is_br := s1_update.bits.br_mask(w)
  //     meta(s1_update_write_way(w))(w).tag   := s1_update_idx
  //     meta(s1_update_write_way(w))(w).ctr   := Mux(!s1_update_meta.hits(w),
  //       Mux(was_taken, 3.U, 0.U),
  //       bimWrite(meta(s1_update_write_way(w))(w).ctr, was_taken)
  //     )
  //   }
  // }

}

