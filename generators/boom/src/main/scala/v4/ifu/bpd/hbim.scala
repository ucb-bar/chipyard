package boom.v4.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v4.common._
import boom.v4.util.{BoomCoreStringPrefix, WrapInc}
import scala.math.min


case class BoomHBIMParams(
  nSets: Int = 2048,
  useLocal: Boolean = false,
  histLength: Int = 32
)

class HBIMBranchPredictorBank(params: BoomHBIMParams = BoomHBIMParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  override val nSets = params.nSets

  require(isPow2(nSets))

  val nWrBypassEntries = 2

  def bimWrite(v: UInt, taken: Bool): UInt = {
    val old_bim_sat_taken  = v === 3.U
    val old_bim_sat_ntaken = v === 0.U
    Mux(old_bim_sat_taken  &&  taken, 3.U,
      Mux(old_bim_sat_ntaken && !taken, 0.U,
      Mux(taken, v + 1.U, v - 1.U)))
  }
  val s3_meta           = Wire(new BIMMeta)
  override val metaSz   = s3_meta.asUInt.getWidth

  val doing_reset = RegInit(true.B)
  val reset_idx = RegInit(0.U(log2Ceil(nSets).W))
  reset_idx := reset_idx + doing_reset
  when (reset_idx === (nSets-1).U) { doing_reset := false.B }


  val data  = Seq.fill(bankWidth) { SyncReadMem(nSets, UInt(2.W)) }
  val mems = Seq(("hbim", nSets, bankWidth * 2))
  def compute_folded_hist(hist: UInt, l: Int) = {
    val nChunks = (params.histLength + l - 1) / l
    val hist_chunks = (0 until nChunks) map {i =>
      hist(min((i+1)*l, params.histLength)-1, i*l)
    }
    hist_chunks.reduce(_^_)
  }

  val f1_idx = compute_folded_hist(
    if (params.useLocal) io.f1_lhist else io.f1_ghist,
    log2Ceil(nSets)) ^ s1_idx

  val s3_req_rdata    = RegNext(VecInit(data.map(_.read(f1_idx, s1_valid))))

  val s3_resp         = Wire(Vec(bankWidth, Bool()))

  for (w <- 0 until bankWidth) {

    s3_resp(w)        := s3_valid && s3_req_rdata(w)(1) && !doing_reset
    s3_meta.bims(w)   := s3_req_rdata(w)
  }


  val s1_update_wdata   = Wire(Vec(bankWidth, UInt(2.W)))
  val s1_update_wmask   = Wire(Vec(bankWidth, Bool()))
  val s1_update_meta    = s1_update.bits.meta.asTypeOf(new BIMMeta)
  val s1_update_index   = compute_folded_hist(
    if (params.useLocal) s1_update.bits.lhist else s1_update.bits.ghist,
    log2Ceil(nSets)) ^ s1_update_idx

  val wrbypass_idxs = Reg(Vec(nWrBypassEntries, UInt(log2Ceil(nSets).W)))
  val wrbypass      = Reg(Vec(nWrBypassEntries, Vec(bankWidth, UInt(2.W))))
  val wrbypass_enq_idx = RegInit(0.U(log2Ceil(nWrBypassEntries).W))

  val wrbypass_hits = VecInit((0 until nWrBypassEntries) map { i =>
    !doing_reset &&
    wrbypass_idxs(i) === s1_update_index(log2Ceil(nSets)-1,0)
  })
  val wrbypass_hit = wrbypass_hits.reduce(_||_)
  val wrbypass_hit_idx = PriorityEncoder(wrbypass_hits)



  for (w <- 0 until bankWidth) {
    s1_update_wmask(w)         := false.B
    s1_update_wdata(w)         := DontCare

    val update_pc = s1_update.bits.pc + (w << 1).U

    when (s1_update.bits.br_mask(w) ||
      (s1_update.bits.cfi_idx.valid && s1_update.bits.cfi_idx.bits === w.U)) {
      val was_taken = (
        s1_update.bits.cfi_idx.valid &&
        (s1_update.bits.cfi_idx.bits === w.U) &&
        (
          (s1_update.bits.cfi_is_br && s1_update.bits.br_mask(w) && s1_update.bits.cfi_taken) ||
          s1_update.bits.cfi_is_jal
        )
      )
      val old_bim_value = Mux(wrbypass_hit, wrbypass(wrbypass_hit_idx)(w), s1_update_meta.bims(w))

      s1_update_wmask(w)     := true.B

      s1_update_wdata(w)     := bimWrite(old_bim_value, was_taken)
    }


  }

  for (w <- 0 until bankWidth) {
    when (doing_reset || (s1_update_wmask(w) && s1_update.valid && s1_update.bits.is_commit_update)) {
      data(w).write(
        Mux(doing_reset, reset_idx, s1_update_index),
        Mux(doing_reset, 2.U, s1_update_wdata(w))
      )
    }
  }
  when (s1_update_wmask.reduce(_||_) && s1_update.valid && s1_update.bits.is_commit_update) {
    when (wrbypass_hit) {
      wrbypass(wrbypass_hit_idx) := s1_update_wdata
    } .otherwise {
      wrbypass(wrbypass_enq_idx)      := s1_update_wdata
      wrbypass_idxs(wrbypass_enq_idx) := s1_update_index
      wrbypass_enq_idx := WrapInc(wrbypass_enq_idx, nWrBypassEntries)
    }
  }

  for (w <- 0 until bankWidth) {
    io.resp.f3(w).taken := s3_resp(w)
  }
  io.f3_meta := s3_meta.asUInt
}
