package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v3.common._
import boom.v3.util.{BoomCoreStringPrefix}

import scala.math.min

case class BoomLoopPredictorParams(
  nWays: Int = 4,
  threshold: Int = 7
)

class LoopBranchPredictorBank(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  val tagSz = 10
  override val nSets = 16



  class LoopMeta extends Bundle {
    val s_cnt   = UInt(10.W)
  }

  class LoopEntry extends Bundle {
    val tag   = UInt(tagSz.W)
    val conf  = UInt(3.W)
    val age   = UInt(3.W)
    val p_cnt = UInt(10.W)
    val s_cnt = UInt(10.W)
  }

  class LoopBranchPredictorColumn extends Module {



    val io = IO(new Bundle {
      val f2_req_valid = Input(Bool())
      val f2_req_idx  = Input(UInt())
      val f3_req_fire = Input(Bool())

      val f3_pred_in  = Input(Bool())
      val f3_pred     = Output(Bool())
      val f3_meta     = Output(new LoopMeta)

      val update_mispredict = Input(Bool())
      val update_repair     = Input(Bool())
      val update_idx   = Input(UInt())
      val update_resolve_dir = Input(Bool())
      val update_meta  = Input(new LoopMeta)
    })

    val doing_reset = RegInit(true.B)
    val reset_idx = RegInit(0.U(log2Ceil(nSets).W))
    reset_idx := reset_idx + doing_reset
    when (reset_idx === (nSets-1).U) { doing_reset := false.B }


    val entries = Reg(Vec(nSets, new LoopEntry))
    val f2_entry = WireInit(entries(io.f2_req_idx))
    when (io.update_repair && io.update_idx === io.f2_req_idx) {
      f2_entry.s_cnt := io.update_meta.s_cnt
    } .elsewhen (io.update_mispredict && io.update_idx === io.f2_req_idx) {
      f2_entry.s_cnt := 0.U
    }
    val f3_entry = RegNext(f2_entry)
    val f3_scnt  = Mux(io.update_repair && io.update_idx === RegNext(io.f2_req_idx),
      io.update_meta.s_cnt,
      f3_entry.s_cnt)
    val f3_tag   = RegNext(io.f2_req_idx(tagSz+log2Ceil(nSets)-1,log2Ceil(nSets)))

    io.f3_pred := io.f3_pred_in
    io.f3_meta.s_cnt := f3_scnt

    when (f3_entry.tag === f3_tag) {
      when (f3_scnt === f3_entry.p_cnt && f3_entry.conf === 7.U) {
        io.f3_pred := !io.f3_pred_in
      }
    }


    val f4_fire  = RegNext(io.f3_req_fire)
    val f4_entry = RegNext(f3_entry)
    val f4_tag   = RegNext(f3_tag)
    val f4_scnt  = RegNext(f3_scnt)
    val f4_idx   = RegNext(RegNext(io.f2_req_idx))


    when (f4_fire) {
      when (f4_entry.tag === f4_tag) {
        when (f4_scnt === f4_entry.p_cnt && f4_entry.conf === 7.U) {
          entries(f4_idx).age   := 7.U
          entries(f4_idx).s_cnt := 0.U
        } .otherwise {
          entries(f4_idx).s_cnt := f4_scnt + 1.U
          entries(f4_idx).age   := Mux(f4_entry.age === 7.U, 7.U, f4_entry.age + 1.U)
        }
      }
    }


    val entry = entries(io.update_idx)
    val tag = io.update_idx(tagSz+log2Ceil(nSets)-1,log2Ceil(nSets))
    val tag_match = entry.tag === tag
    val ctr_match = entry.p_cnt === io.update_meta.s_cnt
    val wentry = WireInit(entry)

    when (io.update_mispredict && !doing_reset) {

      // Learned, tag match -> decrement confidence
      when (entry.conf === 7.U && tag_match) {
        wentry.s_cnt := 0.U
        wentry.conf  := 0.U

      // Learned, no tag match -> do nothing? Don't evict super-confident entries?
      } .elsewhen (entry.conf === 7.U && !tag_match) {

      // Confident, tag match, ctr_match -> increment confidence, reset counter
      } .elsewhen (entry.conf =/= 0.U && tag_match && ctr_match) {
        wentry.conf  := entry.conf + 1.U
        wentry.s_cnt := 0.U

      // Confident, tag match, no ctr match -> zero confidence, reset counter, set previous counter
      } .elsewhen (entry.conf =/= 0.U && tag_match && !ctr_match) {
        wentry.conf  := 0.U
        wentry.s_cnt := 0.U
        wentry.p_cnt := io.update_meta.s_cnt

      // Confident, no tag match, age is 0 -> replace this entry with our own, set our age high to avoid ping-pong
      } .elsewhen (entry.conf =/= 0.U && !tag_match && entry.age === 0.U) {
        wentry.tag   := tag
        wentry.conf  := 1.U
        wentry.s_cnt := 0.U
        wentry.p_cnt := io.update_meta.s_cnt

      // Confident, no tag match, age > 0 -> decrement age
      } .elsewhen (entry.conf =/= 0.U && !tag_match && entry.age =/= 0.U) {
        wentry.age := entry.age - 1.U

      // Unconfident, tag match, ctr match -> increment confidence
      } .elsewhen (entry.conf === 0.U && tag_match && ctr_match) {
        wentry.conf  := 1.U
        wentry.age   := 7.U
        wentry.s_cnt := 0.U

      // Unconfident, tag match, no ctr match -> set previous counter
      } .elsewhen (entry.conf === 0.U && tag_match && !ctr_match) {
        wentry.p_cnt := io.update_meta.s_cnt
        wentry.age   := 7.U
        wentry.s_cnt := 0.U

      // Unconfident, no tag match -> set previous counter and tag
      } .elsewhen (entry.conf === 0.U && !tag_match) {
        wentry.tag   := tag
        wentry.conf  := 1.U
        wentry.age   := 7.U
        wentry.s_cnt := 0.U
        wentry.p_cnt := io.update_meta.s_cnt
      }

      entries(io.update_idx) := wentry
    } .elsewhen (io.update_repair && !doing_reset) {
      when (tag_match && !(f4_fire && io.update_idx === f4_idx)) {
        wentry.s_cnt := io.update_meta.s_cnt
        entries(io.update_idx) := wentry
      }
    }

    when (doing_reset) {
      entries(reset_idx) := (0.U).asTypeOf(new LoopEntry)
   }

  }


  val columns = Seq.fill(bankWidth) { Module(new LoopBranchPredictorColumn) }
  val mems = Nil // TODO fix
  val f3_meta = Wire(Vec(bankWidth, new LoopMeta))
  override val metaSz = f3_meta.asUInt.getWidth

  val update_meta = s1_update.bits.meta.asTypeOf(Vec(bankWidth, new LoopMeta))

  for (w <- 0 until bankWidth) {
    columns(w).io.f2_req_valid := s2_valid
    columns(w).io.f2_req_idx  := s2_idx
    columns(w).io.f3_req_fire := (s3_valid && s3_mask(w) && io.f3_fire &&
      RegNext(io.resp_in(0).f2(w).predicted_pc.valid && io.resp_in(0).f2(w).is_br))

    columns(w).io.f3_pred_in  := io.resp_in(0).f3(w).taken
    io.resp.f3(w).taken       := columns(w).io.f3_pred

    columns(w).io.update_mispredict      := (s1_update.valid &&
                                             s1_update.bits.br_mask(w) &&
                                             s1_update.bits.is_mispredict_update &&
                                             s1_update.bits.cfi_mispredicted)
    columns(w).io.update_repair          := (s1_update.valid &&
                                             s1_update.bits.br_mask(w) &&
                                            s1_update.bits.is_repair_update)
    columns(w).io.update_idx             := s1_update_idx
    columns(w).io.update_resolve_dir     := s1_update.bits.cfi_taken
    columns(w).io.update_meta            := update_meta(w)

    f3_meta(w) := columns(w).io.f3_meta
  }

  io.f3_meta := f3_meta.asUInt

}
