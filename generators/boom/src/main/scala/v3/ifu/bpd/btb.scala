package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v3.common._
import boom.v3.util.{BoomCoreStringPrefix}

import scala.math.min

case class BoomBTBParams(
  nSets: Int = 128,
  nWays: Int = 2,
  offsetSz: Int = 13,
  extendedNSets: Int = 128
)


class BTBBranchPredictorBank(params: BoomBTBParams = BoomBTBParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
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

  val s1_meta = Wire(new BTBPredictMeta)
  val f3_meta = RegNext(RegNext(s1_meta))


  io.f3_meta := f3_meta.asUInt

  override val metaSz = s1_meta.asUInt.getWidth

  val doing_reset = RegInit(true.B)
  val reset_idx   = RegInit(0.U(log2Ceil(nSets).W))
  reset_idx := reset_idx + doing_reset
  when (reset_idx === (nSets-1).U) { doing_reset := false.B }

  val meta     = Seq.fill(nWays) { SyncReadMem(nSets, Vec(bankWidth, UInt(btbMetaSz.W))) }
  val btb      = Seq.fill(nWays) { SyncReadMem(nSets, Vec(bankWidth, UInt(btbEntrySz.W))) }
  val ebtb     = SyncReadMem(extendedNSets, UInt(vaddrBitsExtended.W))

  val mems = (((0 until nWays) map ({w:Int => Seq(
    (f"btb_meta_way$w", nSets, bankWidth * btbMetaSz),
    (f"btb_data_way$w", nSets, bankWidth * btbEntrySz))})).flatten ++ Seq(("ebtb", extendedNSets, vaddrBitsExtended)))

  val s1_req_rbtb  = VecInit(btb.map { b => VecInit(b.read(s0_idx , s0_valid).map(_.asTypeOf(new BTBEntry))) })
  val s1_req_rmeta = VecInit(meta.map { m => VecInit(m.read(s0_idx, s0_valid).map(_.asTypeOf(new BTBMeta))) })
  val s1_req_rebtb = ebtb.read(s0_idx, s0_valid)
  val s1_req_tag   = s1_idx >> log2Ceil(nSets)

  val s1_resp   = Wire(Vec(bankWidth, Valid(UInt(vaddrBitsExtended.W))))
  val s1_is_br  = Wire(Vec(bankWidth, Bool()))
  val s1_is_jal = Wire(Vec(bankWidth, Bool()))

  val s1_hit_ohs = VecInit((0 until bankWidth) map { i =>
    VecInit((0 until nWays) map { w =>
      s1_req_rmeta(w)(i).tag === s1_req_tag(tagSz-1,0)
    })
  })
  val s1_hits     = s1_hit_ohs.map { oh => oh.reduce(_||_) }
  val s1_hit_ways = s1_hit_ohs.map { oh => PriorityEncoder(oh) }

  for (w <- 0 until bankWidth) {
    val entry_meta = s1_req_rmeta(s1_hit_ways(w))(w)
    val entry_btb  = s1_req_rbtb(s1_hit_ways(w))(w)
    s1_resp(w).valid := !doing_reset && s1_valid && s1_hits(w)
    s1_resp(w).bits  := Mux(
      entry_btb.extended,
      s1_req_rebtb,
      (s1_pc.asSInt + (w << 1).S + entry_btb.offset).asUInt)
    s1_is_br(w)  := !doing_reset && s1_resp(w).valid &&  entry_meta.is_br
    s1_is_jal(w) := !doing_reset && s1_resp(w).valid && !entry_meta.is_br


    io.resp.f2(w) := io.resp_in(0).f2(w)
    io.resp.f3(w) := io.resp_in(0).f3(w)
    when (RegNext(s1_hits(w))) {
      io.resp.f2(w).predicted_pc := RegNext(s1_resp(w))
      io.resp.f2(w).is_br        := RegNext(s1_is_br(w))
      io.resp.f2(w).is_jal       := RegNext(s1_is_jal(w))
      when (RegNext(s1_is_jal(w))) {
        io.resp.f2(w).taken      := true.B
      }
    }
    when (RegNext(RegNext(s1_hits(w)))) {
      io.resp.f3(w).predicted_pc := RegNext(io.resp.f2(w).predicted_pc)
      io.resp.f3(w).is_br        := RegNext(io.resp.f2(w).is_br)
      io.resp.f3(w).is_jal       := RegNext(io.resp.f2(w).is_jal)
      when (RegNext(RegNext(s1_is_jal(w)))) {
        io.resp.f3(w).taken      := true.B
      }
    }
  }

  val alloc_way = if (nWays > 1) {
    val r_metas = Cat(VecInit(s1_req_rmeta.map { w => VecInit(w.map(_.tag)) }).asUInt, s1_req_tag(tagSz-1,0))
    val l = log2Ceil(nWays)
    val nChunks = (r_metas.getWidth + l - 1) / l
    val chunks = (0 until nChunks) map { i =>
      r_metas(min((i+1)*l, r_metas.getWidth)-1, i*l)
    }
    chunks.reduce(_^_)
  } else {
    0.U
  }
  s1_meta.write_way := Mux(s1_hits.reduce(_||_),
    PriorityEncoder(s1_hit_ohs.map(_.asUInt).reduce(_|_)),
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

  for (w <- 0 until nWays) {
    when (doing_reset || s1_update_meta.write_way === w.U || (w == 0 && nWays == 1).B) {
      btb(w).write(
        Mux(doing_reset,
          reset_idx,
          s1_update_idx),
        Mux(doing_reset,
          VecInit(Seq.fill(bankWidth) { 0.U(btbEntrySz.W) }),
          VecInit(Seq.fill(bankWidth) { s1_update_wbtb_data.asUInt })),
        Mux(doing_reset,
          (~(0.U(bankWidth.W))),
          s1_update_wbtb_mask).asBools
      )
      meta(w).write(
        Mux(doing_reset,
          reset_idx,
          s1_update_idx),
        Mux(doing_reset,
          VecInit(Seq.fill(bankWidth) { 0.U(btbMetaSz.W) }),
          VecInit(s1_update_wmeta_data.map(_.asUInt))),
        Mux(doing_reset,
          (~(0.U(bankWidth.W))),
          s1_update_wmeta_mask).asBools
      )


    }
  }
  when (s1_update_wbtb_mask =/= 0.U && offset_is_extended) {
    ebtb.write(s1_update_idx, s1_update.bits.target)
  }

}

