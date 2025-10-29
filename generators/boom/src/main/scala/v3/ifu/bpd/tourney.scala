package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v3.common._
import boom.v3.util.{BoomCoreStringPrefix}

import scala.math.min

case class BoomTourneyBPDParams(
  nSets: Int = 128,
  histLength: Int = 32
)

class TourneyBranchPredictorBank(params: BoomTourneyBPDParams = BoomTourneyBPDParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  override def nInputs = 2
  override val nSets = params.nSets

  def bimWrite(v: UInt, taken: Bool): UInt = {
    val old_bim_sat_taken  = v === 3.U
    val old_bim_sat_ntaken = v === 0.U
    Mux(old_bim_sat_taken  &&  taken, 3.U,
      Mux(old_bim_sat_ntaken && !taken, 0.U,
      Mux(taken, v + 1.U, v - 1.U)))
  }
  def compute_folded_hist(hist: UInt, l: Int) = {
    val nChunks = (params.histLength + l - 1) / l
    val hist_chunks = (0 until nChunks) map {i =>
      hist(min((i+1)*l, params.histLength)-1, i*l)
    }
    hist_chunks.reduce(_^_)
  }

  class TourneyMeta extends Bundle {
    val ctrs   = Vec(bankWidth, UInt(2.W))
  }
  val s3_meta = Wire(new TourneyMeta)
  override val metaSz = s3_meta.asUInt.getWidth

  val doing_reset = RegInit(true.B)
  val reset_idx = RegInit(0.U(log2Ceil(nSets).W))
  reset_idx := reset_idx + doing_reset
  when (reset_idx === (nSets-1).U) { doing_reset := false.B }

  val data = Seq.fill(bankWidth) { SyncReadMem(nSets, UInt(2.W)) } // TODO make this masked mem
  val mems = Seq(("tourney", nSets, 2*bankWidth))

  val f1_req_idx = compute_folded_hist(io.f1_ghist, log2Ceil(nSets)) ^ s1_idx
  val s3_req_rdata = RegNext(VecInit(data.map(_.read(f1_req_idx, s1_valid))))
  val s3_resp = Wire(Vec(bankWidth, Bool()))

  io.resp := io.resp_in(0)
  for (w <- 0 until bankWidth) {
    s3_resp(w) := Mux(s3_req_rdata(w)(1), io.resp_in(1).f3(w).taken, io.resp_in(0).f3(w).taken)  && !doing_reset
    s3_meta.ctrs(w) := s3_req_rdata(w)

    io.resp.f3(w).taken := s3_resp(w)
  }
  io.f3_meta := s3_meta.asUInt

  val s1_update_wdata = Wire(Vec(bankWidth, UInt(2.W)))
  val s1_update_wmask = Wire(Vec(bankWidth, Bool()))
  val s1_update_meta  = s1_update.bits.meta.asTypeOf(new TourneyMeta)
  val s1_update_index = compute_folded_hist(s1_update.bits.ghist, log2Ceil(nSets)) ^ s1_update_idx

  for (w <- 0 until bankWidth) {
    s1_update_wmask(w) := false.B
    s1_update_wdata(w) := DontCare

    when (s1_update.bits.br_mask(w) ||
      (s1_update.bits.cfi_idx.valid && s1_update.bits.cfi_idx.bits === w.U)) {
      when (s1_update.bits.cfi_mispredicted && s1_update.bits.cfi_idx.valid && s1_update.bits.cfi_idx.bits === w.U) {
        s1_update_wmask(w) := true.B
        s1_update_wdata(w) := bimWrite(s1_update_meta.ctrs(w), !s1_update_meta.ctrs(w)(1))
      } .otherwise {
        s1_update_wmask(w) := true.B
        s1_update_wdata(w) := bimWrite(s1_update_meta.ctrs(w),  s1_update_meta.ctrs(w)(1))
      }
    }

  }

  for (w <- 0 until bankWidth) {
    when (doing_reset || (s1_update_wmask(w) && s1_update.valid && s1_update.bits.is_commit_update)) {
      data(w).write(
        Mux(doing_reset, reset_idx, s1_update_index),
        Mux(doing_reset, 2.U, s1_update_wdata(w)))
    }
  }

}
