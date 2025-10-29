package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v3.common._
import boom.v3.util.{BoomCoreStringPrefix}




// A branch prediction for a single instruction
class BranchPrediction(implicit p: Parameters) extends BoomBundle()(p)
{
  // If this is a branch, do we take it?
  val taken           = Bool()

  // Is this a branch?
  val is_br           = Bool()
  // Is this a JAL?
  val is_jal          = Bool()
  // What is the target of his branch/jump? Do we know the target?
  val predicted_pc    = Valid(UInt(vaddrBitsExtended.W))


}

// A branch prediction for a entire fetch-width worth of instructions
// This is typically merged from individual predictions from the banked
// predictor
class BranchPredictionBundle(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomFrontendParameters
{
  val pc = UInt(vaddrBitsExtended.W)
  val preds = Vec(fetchWidth, new BranchPrediction)
  val meta = Output(Vec(nBanks, UInt(bpdMaxMetaLength.W)))
  val lhist = Output(Vec(nBanks, UInt(localHistoryLength.W)))
}


// A branch update for a fetch-width worth of instructions
class BranchPredictionUpdate(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomFrontendParameters
{
  // Indicates that this update is due to a speculated misprediction
  // Local predictors typically update themselves with speculative info
  // Global predictors only care about non-speculative updates
  val is_mispredict_update = Bool()
  val is_repair_update = Bool()
  val btb_mispredicts = UInt(fetchWidth.W)
  def is_btb_mispredict_update = btb_mispredicts =/= 0.U
  def is_commit_update = !(is_mispredict_update || is_repair_update || is_btb_mispredict_update)

  val pc            = UInt(vaddrBitsExtended.W)
  // Mask of instructions which are branches.
  // If these are not cfi_idx, then they were predicted not taken
  val br_mask       = UInt(fetchWidth.W)
  // Which CFI was taken/mispredicted (if any)
  val cfi_idx       = Valid(UInt(log2Ceil(fetchWidth).W))
  // Was the cfi taken?
  val cfi_taken     = Bool()
  // Was the cfi mispredicted from the original prediction?
  val cfi_mispredicted = Bool()
  // Was the cfi a br?
  val cfi_is_br     = Bool()
  // Was the cfi a jal/jalr?
  val cfi_is_jal  = Bool()
  // Was the cfi a jalr
  val cfi_is_jalr = Bool()
  //val cfi_is_ret  = Bool()

  val ghist = new GlobalHistory
  val lhist = Vec(nBanks, UInt(localHistoryLength.W))


  // What did this CFI jump to?
  val target        = UInt(vaddrBitsExtended.W)

  val meta          = Vec(nBanks, UInt(bpdMaxMetaLength.W))
}

// A branch update to a single bank
class BranchPredictionBankUpdate(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomFrontendParameters
{
  val is_mispredict_update     = Bool()
  val is_repair_update         = Bool()

  val btb_mispredicts  = UInt(bankWidth.W)
  def is_btb_mispredict_update = btb_mispredicts =/= 0.U

  def is_commit_update = !(is_mispredict_update || is_repair_update || is_btb_mispredict_update)

  val pc               = UInt(vaddrBitsExtended.W)

  val br_mask          = UInt(bankWidth.W)
  val cfi_idx          = Valid(UInt(log2Ceil(bankWidth).W))
  val cfi_taken        = Bool()
  val cfi_mispredicted = Bool()

  val cfi_is_br        = Bool()
  val cfi_is_jal       = Bool()
  val cfi_is_jalr      = Bool()

  val ghist            = UInt(globalHistoryLength.W)
  val lhist            = UInt(localHistoryLength.W)

  val target           = UInt(vaddrBitsExtended.W)

  val meta             = UInt(bpdMaxMetaLength.W)
}

class BranchPredictionRequest(implicit p: Parameters) extends BoomBundle()(p)
{
  val pc    = UInt(vaddrBitsExtended.W)
  val ghist = new GlobalHistory
}


class BranchPredictionBankResponse(implicit p: Parameters) extends BoomBundle()(p)
  with HasBoomFrontendParameters
{
  val f1 = Vec(bankWidth, new BranchPrediction)
  val f2 = Vec(bankWidth, new BranchPrediction)
  val f3 = Vec(bankWidth, new BranchPrediction)
}

abstract class BranchPredictorBank(implicit p: Parameters) extends BoomModule()(p)
  with HasBoomFrontendParameters
{
  val metaSz = 0
  def nInputs = 1

  val mems: Seq[Tuple3[String, Int, Int]]

  val io = IO(new Bundle {
    val f0_valid = Input(Bool())
    val f0_pc    = Input(UInt(vaddrBitsExtended.W))
    val f0_mask  = Input(UInt(bankWidth.W))
    // Local history not available until end of f1
    val f1_ghist = Input(UInt(globalHistoryLength.W))
    val f1_lhist = Input(UInt(localHistoryLength.W))

    val resp_in = Input(Vec(nInputs, new BranchPredictionBankResponse))
    val resp = Output(new BranchPredictionBankResponse)

    // Store the meta as a UInt, use width inference to figure out the shape
    val f3_meta = Output(UInt(bpdMaxMetaLength.W))

    val f3_fire = Input(Bool())

    val update = Input(Valid(new BranchPredictionBankUpdate))
  })
  io.resp := io.resp_in(0)

  io.f3_meta := 0.U

  val s0_idx       = fetchIdx(io.f0_pc)
  val s1_idx       = RegNext(s0_idx)
  val s2_idx       = RegNext(s1_idx)
  val s3_idx       = RegNext(s2_idx)

  val s0_valid = io.f0_valid
  val s1_valid = RegNext(s0_valid)
  val s2_valid = RegNext(s1_valid)
  val s3_valid = RegNext(s2_valid)

  val s0_mask = io.f0_mask
  val s1_mask = RegNext(s0_mask)
  val s2_mask = RegNext(s1_mask)
  val s3_mask = RegNext(s2_mask)

  val s0_pc = io.f0_pc
  val s1_pc = RegNext(s0_pc)

  val s0_update     = io.update
  val s0_update_idx = fetchIdx(io.update.bits.pc)
  val s0_update_valid = io.update.valid

  val s1_update     = RegNext(s0_update)
  val s1_update_idx = RegNext(s0_update_idx)
  val s1_update_valid = RegNext(s0_update_valid)



}



class BranchPredictor(implicit p: Parameters) extends BoomModule()(p)
 with HasBoomFrontendParameters
{
  val io = IO(new Bundle {

    // Requests and responses
    val f0_req = Input(Valid(new BranchPredictionRequest))

    val resp = Output(new Bundle {
      val f1 = new BranchPredictionBundle
      val f2 = new BranchPredictionBundle
      val f3 = new BranchPredictionBundle
    })

    val f3_fire = Input(Bool())

    // Update
    val update = Input(Valid(new BranchPredictionUpdate))
  })

  var total_memsize = 0
  val bpdStr = new StringBuilder
  bpdStr.append(BoomCoreStringPrefix("==Branch Predictor Memory Sizes==\n"))
  val banked_predictors = (0 until nBanks) map ( b => {
    val m = Module(if (useBPD) new ComposedBranchPredictorBank else new NullBranchPredictorBank)
    for ((n, d, w) <- m.mems) {
      bpdStr.append(BoomCoreStringPrefix(f"bank$b $n: $d x $w = ${d * w / 8}"))
      total_memsize = total_memsize + d * w / 8
    }
    m
  })
  bpdStr.append(BoomCoreStringPrefix(f"Total bpd size: ${total_memsize / 1024} KB\n"))
  override def toString: String = bpdStr.toString

  val banked_lhist_providers = Seq.fill(nBanks) { Module(if (localHistoryNSets > 0) new LocalBranchPredictorBank else new NullLocalBranchPredictorBank) }


  if (nBanks == 1) {
    banked_lhist_providers(0).io.f0_valid := io.f0_req.valid
    banked_lhist_providers(0).io.f0_pc    := bankAlign(io.f0_req.bits.pc)

    banked_predictors(0).io.f0_valid := io.f0_req.valid
    banked_predictors(0).io.f0_pc    := bankAlign(io.f0_req.bits.pc)
    banked_predictors(0).io.f0_mask  := fetchMask(io.f0_req.bits.pc)

    banked_predictors(0).io.f1_ghist := RegNext(io.f0_req.bits.ghist.histories(0))
    banked_predictors(0).io.f1_lhist := banked_lhist_providers(0).io.f1_lhist

    banked_predictors(0).io.resp_in(0)           := (0.U).asTypeOf(new BranchPredictionBankResponse)
  } else {
    require(nBanks == 2)

    banked_predictors(0).io.resp_in(0)           := (0.U).asTypeOf(new BranchPredictionBankResponse)
    banked_predictors(1).io.resp_in(0)           := (0.U).asTypeOf(new BranchPredictionBankResponse)

    banked_predictors(0).io.f1_lhist := banked_lhist_providers(0).io.f1_lhist
    banked_predictors(1).io.f1_lhist := banked_lhist_providers(1).io.f1_lhist

    when (bank(io.f0_req.bits.pc) === 0.U) {
      banked_lhist_providers(0).io.f0_valid := io.f0_req.valid
      banked_lhist_providers(0).io.f0_pc    := bankAlign(io.f0_req.bits.pc)

      banked_lhist_providers(1).io.f0_valid := io.f0_req.valid
      banked_lhist_providers(1).io.f0_pc    := nextBank(io.f0_req.bits.pc)

      banked_predictors(0).io.f0_valid := io.f0_req.valid
      banked_predictors(0).io.f0_pc    := bankAlign(io.f0_req.bits.pc)
      banked_predictors(0).io.f0_mask  := fetchMask(io.f0_req.bits.pc)

      banked_predictors(1).io.f0_valid := io.f0_req.valid
      banked_predictors(1).io.f0_pc    := nextBank(io.f0_req.bits.pc)
      banked_predictors(1).io.f0_mask  := ~(0.U(bankWidth.W))
    } .otherwise {
      banked_lhist_providers(0).io.f0_valid := io.f0_req.valid && !mayNotBeDualBanked(io.f0_req.bits.pc)
      banked_lhist_providers(0).io.f0_pc    := nextBank(io.f0_req.bits.pc)

      banked_lhist_providers(1).io.f0_valid := io.f0_req.valid
      banked_lhist_providers(1).io.f0_pc    := bankAlign(io.f0_req.bits.pc)

      banked_predictors(0).io.f0_valid := io.f0_req.valid && !mayNotBeDualBanked(io.f0_req.bits.pc)
      banked_predictors(0).io.f0_pc    := nextBank(io.f0_req.bits.pc)
      banked_predictors(0).io.f0_mask  := ~(0.U(bankWidth.W))

      banked_predictors(1).io.f0_valid := io.f0_req.valid
      banked_predictors(1).io.f0_pc    := bankAlign(io.f0_req.bits.pc)
      banked_predictors(1).io.f0_mask  := fetchMask(io.f0_req.bits.pc)
    }
    when (RegNext(bank(io.f0_req.bits.pc) === 0.U)) {
      banked_predictors(0).io.f1_ghist  := RegNext(io.f0_req.bits.ghist.histories(0))
      banked_predictors(1).io.f1_ghist  := RegNext(io.f0_req.bits.ghist.histories(1))
    } .otherwise {
      banked_predictors(0).io.f1_ghist  := RegNext(io.f0_req.bits.ghist.histories(1))
      banked_predictors(1).io.f1_ghist  := RegNext(io.f0_req.bits.ghist.histories(0))
    }
  }


  for (i <- 0 until nBanks) {
    banked_lhist_providers(i).io.f3_taken_br := banked_predictors(i).io.resp.f3.map ( p =>
      p.is_br && p.predicted_pc.valid && p.taken
    ).reduce(_||_)
  }

  if (nBanks == 1) {
    io.resp.f1.preds    := banked_predictors(0).io.resp.f1
    io.resp.f2.preds    := banked_predictors(0).io.resp.f2
    io.resp.f3.preds    := banked_predictors(0).io.resp.f3
    io.resp.f3.meta(0)  := banked_predictors(0).io.f3_meta
    io.resp.f3.lhist(0) := banked_lhist_providers(0).io.f3_lhist

    banked_predictors(0).io.f3_fire := io.f3_fire
    banked_lhist_providers(0).io.f3_fire := io.f3_fire
  } else {
    require(nBanks == 2)
    val b0_fire = io.f3_fire && RegNext(RegNext(RegNext(banked_predictors(0).io.f0_valid)))
    val b1_fire = io.f3_fire && RegNext(RegNext(RegNext(banked_predictors(1).io.f0_valid)))
    banked_predictors(0).io.f3_fire := b0_fire
    banked_predictors(1).io.f3_fire := b1_fire

    banked_lhist_providers(0).io.f3_fire := b0_fire
    banked_lhist_providers(1).io.f3_fire := b1_fire



    // The branch prediction metadata is stored un-shuffled
    io.resp.f3.meta(0)    := banked_predictors(0).io.f3_meta
    io.resp.f3.meta(1)    := banked_predictors(1).io.f3_meta

    io.resp.f3.lhist(0)   := banked_lhist_providers(0).io.f3_lhist
    io.resp.f3.lhist(1)   := banked_lhist_providers(1).io.f3_lhist

    when (bank(io.resp.f1.pc) === 0.U) {
      for (i <- 0 until bankWidth) {
        io.resp.f1.preds(i)           := banked_predictors(0).io.resp.f1(i)
        io.resp.f1.preds(i+bankWidth) := banked_predictors(1).io.resp.f1(i)
      }
    } .otherwise {
      for (i <- 0 until bankWidth) {
        io.resp.f1.preds(i)           := banked_predictors(1).io.resp.f1(i)
        io.resp.f1.preds(i+bankWidth) := banked_predictors(0).io.resp.f1(i)
      }
    }

    when (bank(io.resp.f2.pc) === 0.U) {
      for (i <- 0 until bankWidth) {
        io.resp.f2.preds(i)           := banked_predictors(0).io.resp.f2(i)
        io.resp.f2.preds(i+bankWidth) := banked_predictors(1).io.resp.f2(i)
      }
    } .otherwise {
      for (i <- 0 until bankWidth) {
        io.resp.f2.preds(i)           := banked_predictors(1).io.resp.f2(i)
        io.resp.f2.preds(i+bankWidth) := banked_predictors(0).io.resp.f2(i)
      }
    }

    when (bank(io.resp.f3.pc) === 0.U) {
      for (i <- 0 until bankWidth) {
        io.resp.f3.preds(i)           := banked_predictors(0).io.resp.f3(i)
        io.resp.f3.preds(i+bankWidth) := banked_predictors(1).io.resp.f3(i)
      }
    } .otherwise {
      for (i <- 0 until bankWidth) {
        io.resp.f3.preds(i)           := banked_predictors(1).io.resp.f3(i)
        io.resp.f3.preds(i+bankWidth) := banked_predictors(0).io.resp.f3(i)
      }
    }
  }

  io.resp.f1.pc := RegNext(io.f0_req.bits.pc)
  io.resp.f2.pc := RegNext(io.resp.f1.pc)
  io.resp.f3.pc := RegNext(io.resp.f2.pc)

  // We don't care about meta from the f1 and f2 resps
  // Use the meta from the latest resp
  io.resp.f1.meta := DontCare
  io.resp.f2.meta := DontCare
  io.resp.f1.lhist := DontCare
  io.resp.f2.lhist := DontCare


  for (i <- 0 until nBanks) {
    banked_predictors(i).io.update.bits.is_mispredict_update := io.update.bits.is_mispredict_update
    banked_predictors(i).io.update.bits.is_repair_update     := io.update.bits.is_repair_update

    banked_predictors(i).io.update.bits.meta             := io.update.bits.meta(i)
    banked_predictors(i).io.update.bits.lhist            := io.update.bits.lhist(i)
    banked_predictors(i).io.update.bits.cfi_idx.bits     := io.update.bits.cfi_idx.bits
    banked_predictors(i).io.update.bits.cfi_taken        := io.update.bits.cfi_taken
    banked_predictors(i).io.update.bits.cfi_mispredicted := io.update.bits.cfi_mispredicted
    banked_predictors(i).io.update.bits.cfi_is_br        := io.update.bits.cfi_is_br
    banked_predictors(i).io.update.bits.cfi_is_jal       := io.update.bits.cfi_is_jal
    banked_predictors(i).io.update.bits.cfi_is_jalr      := io.update.bits.cfi_is_jalr
    banked_predictors(i).io.update.bits.target           := io.update.bits.target

    banked_lhist_providers(i).io.update.mispredict := io.update.bits.is_mispredict_update
    banked_lhist_providers(i).io.update.repair     := io.update.bits.is_repair_update
    banked_lhist_providers(i).io.update.lhist      := io.update.bits.lhist(i)
  }

  if (nBanks == 1) {
    banked_predictors(0).io.update.valid                 := io.update.valid
    banked_predictors(0).io.update.bits.pc               := bankAlign(io.update.bits.pc)
    banked_predictors(0).io.update.bits.br_mask          := io.update.bits.br_mask
    banked_predictors(0).io.update.bits.btb_mispredicts  := io.update.bits.btb_mispredicts
    banked_predictors(0).io.update.bits.cfi_idx.valid    := io.update.bits.cfi_idx.valid
    banked_predictors(0).io.update.bits.ghist            := io.update.bits.ghist.histories(0)

    banked_lhist_providers(0).io.update.valid := io.update.valid && io.update.bits.br_mask =/= 0.U
    banked_lhist_providers(0).io.update.pc    := bankAlign(io.update.bits.pc)
  } else {
    require(nBanks == 2)
    // Split the single update bundle for the fetchpacket into two updates
    // 1 for each bank.

    when (bank(io.update.bits.pc) === 0.U) {
      val b1_update_valid = io.update.valid &&
        (!io.update.bits.cfi_idx.valid || io.update.bits.cfi_idx.bits >= bankWidth.U)

      banked_lhist_providers(0).io.update.valid := io.update.valid && io.update.bits.br_mask(bankWidth-1,0) =/= 0.U
      banked_lhist_providers(1).io.update.valid := b1_update_valid && io.update.bits.br_mask(fetchWidth-1,bankWidth) =/= 0.U

      banked_lhist_providers(0).io.update.pc := bankAlign(io.update.bits.pc)
      banked_lhist_providers(1).io.update.pc := nextBank(io.update.bits.pc)

      banked_predictors(0).io.update.valid := io.update.valid
      banked_predictors(1).io.update.valid := b1_update_valid

      banked_predictors(0).io.update.bits.pc := bankAlign(io.update.bits.pc)
      banked_predictors(1).io.update.bits.pc := nextBank(io.update.bits.pc)

      banked_predictors(0).io.update.bits.br_mask := io.update.bits.br_mask
      banked_predictors(1).io.update.bits.br_mask := io.update.bits.br_mask >> bankWidth

      banked_predictors(0).io.update.bits.btb_mispredicts  := io.update.bits.btb_mispredicts
      banked_predictors(1).io.update.bits.btb_mispredicts  := io.update.bits.btb_mispredicts >> bankWidth

      banked_predictors(0).io.update.bits.cfi_idx.valid := io.update.bits.cfi_idx.valid && io.update.bits.cfi_idx.bits < bankWidth.U
      banked_predictors(1).io.update.bits.cfi_idx.valid := io.update.bits.cfi_idx.valid && io.update.bits.cfi_idx.bits >= bankWidth.U

      banked_predictors(0).io.update.bits.ghist := io.update.bits.ghist.histories(0)
      banked_predictors(1).io.update.bits.ghist := io.update.bits.ghist.histories(1)
    } .otherwise {
      val b0_update_valid = io.update.valid && !mayNotBeDualBanked(io.update.bits.pc) &&
        (!io.update.bits.cfi_idx.valid || io.update.bits.cfi_idx.bits >= bankWidth.U)

      banked_lhist_providers(1).io.update.valid := io.update.valid && io.update.bits.br_mask(bankWidth-1,0) =/= 0.U
      banked_lhist_providers(0).io.update.valid := b0_update_valid && io.update.bits.br_mask(fetchWidth-1,bankWidth) =/= 0.U

      banked_lhist_providers(1).io.update.pc := bankAlign(io.update.bits.pc)
      banked_lhist_providers(0).io.update.pc := nextBank(io.update.bits.pc)

      banked_predictors(1).io.update.valid := io.update.valid
      banked_predictors(0).io.update.valid := b0_update_valid

      banked_predictors(1).io.update.bits.pc := bankAlign(io.update.bits.pc)
      banked_predictors(0).io.update.bits.pc := nextBank(io.update.bits.pc)

      banked_predictors(1).io.update.bits.br_mask := io.update.bits.br_mask
      banked_predictors(0).io.update.bits.br_mask := io.update.bits.br_mask >> bankWidth

      banked_predictors(1).io.update.bits.btb_mispredicts  := io.update.bits.btb_mispredicts
      banked_predictors(0).io.update.bits.btb_mispredicts  := io.update.bits.btb_mispredicts >> bankWidth

      banked_predictors(1).io.update.bits.cfi_idx.valid := io.update.bits.cfi_idx.valid && io.update.bits.cfi_idx.bits < bankWidth.U
      banked_predictors(0).io.update.bits.cfi_idx.valid := io.update.bits.cfi_idx.valid && io.update.bits.cfi_idx.bits >= bankWidth.U

      banked_predictors(1).io.update.bits.ghist := io.update.bits.ghist.histories(0)
      banked_predictors(0).io.update.bits.ghist := io.update.bits.ghist.histories(1)
    }

  }

  when (io.update.valid) {
    when (io.update.bits.cfi_is_br && io.update.bits.cfi_idx.valid) {
      assert(io.update.bits.br_mask(io.update.bits.cfi_idx.bits))
    }
  }
}

class NullBranchPredictorBank(implicit p: Parameters) extends BranchPredictorBank()(p) {
  val mems = Nil
}


