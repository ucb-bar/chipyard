package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v3.common._
import boom.v3.util.{BoomCoreStringPrefix, WrapInc}

case class BoomMicroBTBParams(
  nSets: Int = 256,
  offsetSz: Int = 13
)


class MicroBTBBranchPredictorBank(params: BoomMicroBTBParams)(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  override val nSets         = params.nSets
  val tagSz         = vaddrBitsExtended - log2Ceil(nSets) - log2Ceil(fetchWidth) - 1
  val offsetSz      = params.offsetSz
  val nWrBypassEntries = 2

  def bimWrite(v: UInt, taken: Bool): UInt = {
    val old_bim_sat_taken  = v === 3.U
    val old_bim_sat_ntaken = v === 0.U
    Mux(old_bim_sat_taken  &&  taken, 3.U,
      Mux(old_bim_sat_ntaken && !taken, 0.U,
      Mux(taken, v + 1.U, v - 1.U)))
  }

  require(isPow2(nSets))

  class MicroBTBEntry extends Bundle {
    val offset   = SInt(offsetSz.W)
  }
  val btbEntrySz = offsetSz

  class MicroBTBMeta extends Bundle {
    val is_br = Bool()
    val tag   = UInt(tagSz.W)
    val ctr   = UInt(2.W)
  }
  val btbMetaSz = tagSz + 1 + 2

  class MicroBTBPredictMeta extends Bundle {
    val ctrs  = Vec(bankWidth, UInt(2.W))
  }

  val s1_meta = Wire(new MicroBTBPredictMeta)
  override val metaSz = s1_meta.asUInt.getWidth

  val doing_reset = RegInit(true.B)
  val reset_idx   = RegInit(0.U(log2Ceil(nSets).W))
  reset_idx := reset_idx + doing_reset
  when (reset_idx === (nSets-1).U) { doing_reset := false.B }

  val meta     = SyncReadMem(nSets, Vec(bankWidth, UInt(btbMetaSz.W)))
  val btb      = SyncReadMem(nSets, Vec(bankWidth, UInt(btbEntrySz.W)))

  val mems = Seq(("ubtb_meta", nSets, bankWidth * btbMetaSz),
                 ("ubtb_data", nSets, bankWidth * btbEntrySz))

  val s1_req_rbtb  = VecInit(btb.read(s0_idx , s0_valid).map(_.asTypeOf(new MicroBTBEntry)))
  val s1_req_rmeta = VecInit(meta.read(s0_idx, s0_valid).map(_.asTypeOf(new MicroBTBMeta)))
  val s1_req_tag   = s1_idx >> log2Ceil(nSets)


  val s1_resp   = Wire(Vec(bankWidth, Valid(UInt(vaddrBitsExtended.W))))
  val s1_taken  = Wire(Vec(bankWidth, Bool()))
  val s1_is_br  = Wire(Vec(bankWidth, Bool()))
  val s1_is_jal = Wire(Vec(bankWidth, Bool()))

  for (w <- 0 until bankWidth) {
    s1_resp(w).valid := !doing_reset && s1_valid && s1_req_tag(tagSz-1,0) === s1_req_rmeta(w).tag
    s1_resp(w).bits  := (s1_pc.asSInt + (w << 1).S + s1_req_rbtb(w).offset).asUInt
    s1_is_br(w)  := !doing_reset && s1_resp(w).valid &&  s1_req_rmeta(w).is_br
    s1_is_jal(w) := !doing_reset && s1_resp(w).valid && !s1_req_rmeta(w).is_br
    s1_taken(w)  := !doing_reset && (!s1_req_rmeta(w).is_br || s1_req_rmeta(w).ctr(1))
    s1_meta.ctrs(w) := s1_req_rmeta(w).ctr
  }

  for (w <- 0 until bankWidth) {
    io.resp.f1(w).predicted_pc := s1_resp(w)
    io.resp.f1(w).is_br        := s1_is_br(w)
    io.resp.f1(w).is_jal       := s1_is_jal(w)
    io.resp.f1(w).taken        := s1_taken(w)

    io.resp.f2(w) := RegNext(io.resp.f1(w))
    io.resp.f3(w) := RegNext(io.resp.f2(w))
  }
  io.f3_meta := RegNext(RegNext(s1_meta.asUInt))

  val s1_update_cfi_idx = s1_update.bits.cfi_idx.bits
  val s1_update_meta    = s1_update.bits.meta.asTypeOf(new MicroBTBPredictMeta)

  val wrbypass_idxs    = Reg(Vec(nWrBypassEntries, UInt(log2Ceil(nSets).W)))
  val wrbypass         = Reg(Vec(nWrBypassEntries, Vec(bankWidth, new MicroBTBMeta)))
  val wrbypass_enq_idx = RegInit(0.U(log2Ceil(nWrBypassEntries).W))

  val wrbypass_hits = VecInit((0 until nWrBypassEntries) map { i =>
    !doing_reset &&
    wrbypass_idxs(i) === s1_update_idx(log2Ceil(nSets)-1,0)
  })
  val wrbypass_hit  = wrbypass_hits.reduce(_||_)
  val wrbypass_hit_idx = PriorityEncoder(wrbypass_hits)

  val max_offset_value = (~(0.U)((offsetSz-1).W)).asSInt
  val min_offset_value = Cat(1.B, (0.U)((offsetSz-1).W)).asSInt
  val new_offset_value = (s1_update.bits.target.asSInt -
    (s1_update.bits.pc + (s1_update.bits.cfi_idx.bits << 1)).asSInt)

  val s1_update_wbtb_data     = Wire(new MicroBTBEntry)
  s1_update_wbtb_data.offset := new_offset_value
  val s1_update_wbtb_mask = (UIntToOH(s1_update_cfi_idx) &
    Fill(bankWidth, s1_update.bits.cfi_idx.valid && s1_update.valid && s1_update.bits.cfi_taken && s1_update.bits.is_commit_update))

  val s1_update_wmeta_mask = ((s1_update_wbtb_mask | s1_update.bits.br_mask) &
    Fill(bankWidth, s1_update.valid && s1_update.bits.is_commit_update))
  val s1_update_wmeta_data = Wire(Vec(bankWidth, new MicroBTBMeta))
  for (w <- 0 until bankWidth) {
    s1_update_wmeta_data(w).tag     := s1_update_idx >> log2Ceil(nSets)
    s1_update_wmeta_data(w).is_br   := s1_update.bits.br_mask(w)

    val was_taken =  (s1_update.bits.cfi_idx.valid && s1_update.bits.cfi_idx.bits === w.U &&
      (
        (s1_update.bits.cfi_is_br && s1_update.bits.cfi_taken) ||
        s1_update.bits.cfi_is_jal
      )
    )
    val old_bim_value = Mux(wrbypass_hit, wrbypass(wrbypass_hit_idx)(w).ctr, s1_update_meta.ctrs(w))
    s1_update_wmeta_data(w).ctr     := bimWrite(old_bim_value, was_taken)
  }

  btb.write(
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
  meta.write(
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

  when (s1_update_wmeta_mask =/= 0.U) {
    when (wrbypass_hit) {
      wrbypass(wrbypass_hit_idx) := s1_update_wmeta_data
    } .otherwise {
      wrbypass(wrbypass_enq_idx)      := s1_update_wmeta_data
      wrbypass_idxs(wrbypass_enq_idx) := s1_update_idx
      wrbypass_enq_idx := WrapInc(wrbypass_enq_idx, nWrBypassEntries)
    }
  }
}

