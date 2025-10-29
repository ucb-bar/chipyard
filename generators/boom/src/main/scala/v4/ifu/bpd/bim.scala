package boom.v4.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v4.common._
import boom.v4.util.{BoomCoreStringPrefix, WrapInc}
import scala.math.min



class BIMMeta(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomFrontendParameters
{
  val bims  = Vec(bankWidth, UInt(2.W))
}

case class BoomBIMParams(
  nSets: Int = 2048,
  nCols: Int = 8,
  singlePorted: Boolean = true,
  useFlops: Boolean = false,
  slow: Boolean = false
)

class BIMBranchPredictorBank(params: BoomBIMParams = BoomBIMParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  override val nSets = params.nSets
  val nCols = params.nCols
  val nSetsPerCol = nSets / nCols

  require(isPow2(nSets))
  require(isPow2(nCols))
  require(nCols < nSets)
  require(nCols > 1)

  val nWrBypassEntries = 2

  def bimWrite(v: UInt, taken: Bool): UInt = {
    val old_bim_sat_taken  = v === 3.U
    val old_bim_sat_ntaken = v === 0.U
    Mux(old_bim_sat_taken  &&  taken, 3.U,
      Mux(old_bim_sat_ntaken && !taken, 0.U,
      Mux(taken, v + 1.U, v - 1.U)))
  }
  val s2_meta           = Wire(new BIMMeta)
  override val metaSz   = s2_meta.asUInt.getWidth

  val doing_reset = RegInit(true.B)
  val reset_idx = RegInit(0.U(log2Ceil(nSetsPerCol).W))
  reset_idx := reset_idx + doing_reset
  when (reset_idx === (nSetsPerCol-1).U) { doing_reset := false.B }




  val mems = (0 until nCols) map {c => (f"bim_col$c", nSetsPerCol, bankWidth * 2)}

  val s0_col_mask = UIntToOH(s0_idx(log2Ceil(nCols)-1,0)) & Fill(nCols, s0_valid)
  val s1_col_mask = RegNext(s0_col_mask)
  val s0_col_idx  = s0_idx >> log2Ceil(nCols)
  val s1_col_idx  = RegNext(s0_col_idx)

  val s2_req_rdata_all = Wire(Vec(nCols, Vec(bankWidth, UInt(2.W))))
  val s2_req_rdata = Mux1H(RegNext(s1_col_mask), s2_req_rdata_all)

  val s2_resp         = Wire(Vec(bankWidth, Bool()))

  for (w <- 0 until bankWidth) {

    s2_resp(w)        := s2_valid && s2_req_rdata(w)(1) && !doing_reset
    s2_meta.bims(w)   := s2_req_rdata(w)

    if (!params.slow) {
      io.resp.f2(w).taken := s2_resp(w)
    }
    io.resp.f3(w).taken := RegNext(s2_resp(w))
  }
  io.f3_meta := RegNext(s2_meta.asUInt)

  val s1_update_wdata   = Wire(Vec(bankWidth, UInt(2.W)))
  val s1_update_wmask   = Wire(Vec(bankWidth, Bool()))
  val s1_update_meta    = s1_update.bits.meta.asTypeOf(new BIMMeta)
  val s1_update_col_mask = UIntToOH(s1_update_idx(log2Ceil(nCols)-1,0))
  val s1_update_col_idx = s1_update_idx >> log2Ceil(nCols)

  val wrbypass_idxs = Reg(Vec(nWrBypassEntries, UInt(log2Ceil(nSets).W)))
  val wrbypass      = Reg(Vec(nWrBypassEntries, Vec(bankWidth, UInt(2.W))))
  val wrbypass_enq_idx = RegInit(0.U(log2Ceil(nWrBypassEntries).W))

  val wrbypass_hits = VecInit((0 until nWrBypassEntries) map { i =>
    !doing_reset &&
    wrbypass_idxs(i) === s1_update_idx(log2Ceil(nSets)-1,0)
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

  for (c <- 0 until nCols) {


    val rdata = Wire(Vec(bankWidth, UInt(2.W)))
    rdata := DontCare
    val (ren, ridx) = if (params.slow) (s1_col_mask(c), s1_col_idx) else (s0_col_mask(c), s0_col_idx)
    val wen = WireInit(doing_reset || (s1_update.valid && s1_update.bits.is_commit_update && s1_update_col_mask(c) && !ren))
    if (params.slow) {
      s2_req_rdata_all(c) := rdata
    } else {
      s2_req_rdata_all(c) := RegNext(rdata)
    }
    if (params.useFlops) {
      val data = Reg(Vec(nSetsPerCol, Vec(bankWidth, UInt(2.W))))
      when (wen && doing_reset) {
        data(reset_idx) := VecInit(Seq.fill(bankWidth) { 2.U })
      } .elsewhen (wen) {
        for (i <- 0 until bankWidth) {
          when (s1_update_wmask(i)) {
            data(s1_update_col_idx)(i) := s1_update_wdata(i)
          }
        }
      }
      when (RegNext(ren) && !(wen && params.singlePorted.B)) {
        rdata := data(RegNext(ridx))
      }
    } else {
      val data = SyncReadMem(nSetsPerCol, Vec(bankWidth, UInt(2.W)))
      data.suggestName(s"bim_col_${c}")
      val r = if (params.singlePorted) data.read(ridx, ren && !wen) else data.read(ridx, ren)
      rdata := r
      when (wen) {
        val widx = Mux(doing_reset, reset_idx, s1_update_col_idx)
        val wdata = Mux(doing_reset, VecInit(Seq.fill(bankWidth) { 2.U }), s1_update_wdata)
        val wmask = Mux(doing_reset, (~(0.U(bankWidth.W))), s1_update_wmask.asUInt)
        data.write(widx, wdata, wmask.asBools)
      }
    }
  }
  when (s1_update_wmask.reduce(_||_) && s1_update.valid && s1_update.bits.is_commit_update) {
    when (wrbypass_hit) {
      wrbypass(wrbypass_hit_idx) := s1_update_wdata
    } .otherwise {
      wrbypass(wrbypass_enq_idx)      := s1_update_wdata
      wrbypass_idxs(wrbypass_enq_idx) := s1_update_idx
      wrbypass_enq_idx := WrapInc(wrbypass_enq_idx, nWrBypassEntries)
    }
  }


}
