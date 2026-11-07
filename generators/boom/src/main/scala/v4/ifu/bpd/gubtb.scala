package boom.v4.ifu

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v4.common._
import boom.v4.util.{BoomCoreStringPrefix, WrapInc, MaskLower}

import scala.math.min

case class BoomGMicroBTBParams(
  nWays: Int = 24,
  tagSz: Int = 12
)

class GMicroBTBBranchPredictorBank(params: BoomGMicroBTBParams = BoomGMicroBTBParams())(implicit p: Parameters) extends BranchPredictorBank()(p)
{

  def bimWrite(v: UInt, taken: Bool): UInt = {
    val old_bim_sat_taken  = v === 3.U
    val old_bim_sat_ntaken = v === 0.U
    Mux(old_bim_sat_taken  &&  taken, 3.U,
      Mux(old_bim_sat_ntaken && !taken, 0.U,
      Mux(taken, v + 1.U, v - 1.U)))
  }


  override val nWays = params.nWays
  val tagSz = params.tagSz

  class MicroBTBMeta extends Bundle {
    val tag   = UInt(tagSz.W)
    val ctr   = UInt(2.W)
    val is_br = Bool()
    val is_jal = Bool()
  }

  class MicroBTBPredictMeta extends Bundle {
    val hit = Bool()
  }
  val s1_meta = Wire(new MicroBTBPredictMeta)
  override val metaSz = s1_meta.asUInt.getWidth

  val valids = RegInit(VecInit(0.U(nWays.W).asBools))
  val meta = Reg(Vec(nWays, new MicroBTBMeta))
  val btb = Reg(Vec(nWays, UInt(vaddrBitsExtended.W)))
  val mems = Nil

  val s1_req_tag = RegNext(io.f0_pc(tagSz-1,0))

  val s1_hit_oh = (0 until nWays) map { w => valids(w) && meta(w).tag === s1_req_tag }
  val s1_hit = s1_hit_oh.reduce(_||_) && s1_valid

  val s1_hit_meta = Mux1H(s1_hit_oh, meta)
  val s1_hit_btb = Mux1H(s1_hit_oh, btb)
  s1_meta.hit := s1_hit

  for (w <- 0 until bankWidth-1) {
    io.resp.f1(w).predicted_pc.valid := false.B
    io.resp.f1(w).predicted_pc.bits  := 0.U
    io.resp.f1(w).is_br              := s1_hit_meta.is_br
    io.resp.f1(w).is_jal             := false.B
    io.resp.f1(w).taken              := false.B
  }


  io.resp.f1(bankWidth-1).predicted_pc.valid := s1_hit
  io.resp.f1(bankWidth-1).predicted_pc.bits  := s1_hit_btb
  io.resp.f1(bankWidth-1).is_br              := s1_hit_meta.is_br && !s1_hit_meta.is_jal
  io.resp.f1(bankWidth-1).is_jal             := s1_hit_meta.is_jal
  io.resp.f1(bankWidth-1).taken              := s1_hit && (s1_hit_meta.is_jal || s1_hit_meta.ctr(1))


  io.f3_meta := RegNext(RegNext(s1_meta.asUInt))

  val s1_update_pc = RegNext(io.update.bits.pc)
  val s1_update_hit_oh = (0 until nWays) map { w =>
    meta(w).tag === s1_update_pc(tagSz-1,0) && valids(w)
  }
  val s1_update_hit = s1_update_hit_oh.reduce(_||_)
  val s1_update_write_way = Mux(s1_update_hit, OHToUInt(s1_update_hit_oh), LFSR(16))

  when (s1_update.valid && s1_update.bits.is_commit_update) {
    val rmeta = meta(s1_update_write_way)
    val wmeta = Wire(new MicroBTBMeta)

    wmeta.tag := s1_update_pc
    when (s1_update.bits.cfi_idx.valid) {
      btb(s1_update_write_way) := s1_update.bits.target
      valids(s1_update_write_way) := true.B

      val is_jal = s1_update.bits.cfi_is_jal || s1_update.bits.cfi_is_jalr

      wmeta.is_br := (s1_update.bits.br_mask & MaskLower(UIntToOH(s1_update.bits.cfi_idx.bits))) =/= 0.U || !is_jal
      wmeta.is_jal := is_jal
      when (s1_update_hit) {
        wmeta.ctr := bimWrite(rmeta.ctr, s1_update.bits.cfi_taken)
      } .otherwise {
        wmeta.ctr := Mux(s1_update.bits.cfi_idx.valid && s1_update.bits.cfi_taken, 3.U, 0.U)
      }
    } .otherwise {
      wmeta.is_br := true.B
      wmeta.is_jal := false.B
      when (s1_update_hit) {
        wmeta.ctr := bimWrite(rmeta.ctr, false.B)
      } .otherwise {
        wmeta.ctr := 0.U
      }
    }
    when (s1_update.bits.cfi_idx.valid || s1_update.bits.br_mask =/= 0.U) {
      meta(s1_update_write_way) := wmeta
    }
  }

}
