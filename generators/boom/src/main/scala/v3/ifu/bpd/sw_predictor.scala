package boom.v3.ifu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import boom.v3.common._
import boom.v3.exu.{CommitExceptionSignals}
import boom.v3.util.{BoomCoreStringPrefix}


class SwBranchPredictorBank(implicit p: Parameters) extends BranchPredictorBank()(p)
{
  val mems = Nil
  for (w <- 0 until bankWidth) {
    val btb_harness = Module(new BTBHarness)
    val pred_harness = Module(new BranchPredictorHarness)

    btb_harness.io.clock := clock
    btb_harness.io.reset := reset.asBool

    pred_harness.io.clock := clock
    pred_harness.io.reset := reset.asBool


    btb_harness.io.req_valid := s1_valid
    btb_harness.io.req_pc    := bankAlign(s1_pc) + (w << 1).U
    btb_harness.io.req_hist  := io.f1_ghist

    pred_harness.io.req_valid := s1_valid
    pred_harness.io.req_pc    := bankAlign(s1_pc) + (w << 1).U
    pred_harness.io.req_hist  := io.f1_ghist

    btb_harness.io.update_valid  := io.update.valid && io.update.bits.is_commit_update && io.update.bits.cfi_idx.valid && (w == 0).B
    btb_harness.io.update_pc     := io.update.bits.pc + (io.update.bits.cfi_idx.bits << 1)
    btb_harness.io.update_hist   := io.update.bits.ghist
    btb_harness.io.update_target := io.update.bits.target
    btb_harness.io.update_is_br  := io.update.bits.cfi_is_br
    btb_harness.io.update_is_jal := io.update.bits.cfi_is_jal

    pred_harness.io.update_valid := io.update.valid && io.update.bits.is_commit_update && io.update.bits.br_mask(w)
    pred_harness.io.update_pc    := io.update.bits.pc + (w << 1).U
    pred_harness.io.update_hist  := io.update.bits.ghist
    pred_harness.io.update_taken := w.U === io.update.bits.cfi_idx.bits &&
                                      io.update.bits.cfi_idx.valid && io.update.bits.cfi_taken


    io.resp.f2(w).taken              := pred_harness.io.req_taken
    io.resp.f2(w).predicted_pc.valid := btb_harness.io.req_target_valid
    io.resp.f2(w).predicted_pc.bits  := btb_harness.io.req_target_pc
    io.resp.f2(w).is_br              := btb_harness.io.req_is_br  && btb_harness.io.req_target_valid
    io.resp.f2(w).is_jal             := btb_harness.io.req_is_jal && btb_harness.io.req_target_valid

    // The Harness to software assumes output comes out in f2
    io.resp.f3(w).is_br := RegNext(pred_harness.io.req_taken)
    io.resp.f3(w).taken := RegNext(pred_harness.io.req_taken)
  }
}

class BranchPredictorHarness(implicit p: Parameters)
    extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())

    val req_valid = Input(Bool())
    val req_pc = Input(UInt(64.W))
    val req_hist = Input(UInt(64.W))
    val req_taken = Output(Bool())

    val update_valid = Input(Bool())
    val update_pc = Input(UInt(64.W))
    val update_hist = Input(UInt(64.W))
    val update_taken = Input(Bool())
  })

  addResource("/vsrc/predictor_harness.v")
  addResource("/csrc/predictor_sw.cc")
}

class BTBHarness(implicit p: Parameters)
    extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())

    val req_valid = Input(Bool())
    val req_pc = Input(UInt(64.W))
    val req_hist = Input(UInt(64.W))
    val req_target_valid = Output(Bool())
    val req_target_pc = Output(UInt(64.W))
    val req_is_br = Output(Bool())
    val req_is_jal = Output(Bool())

    val update_valid = Input(Bool())
    val update_pc = Input(UInt(64.W))
    val update_hist = Input(UInt(64.W))
    val update_target = Input(UInt(64.W))
    val update_is_br = Input(Bool())
    val update_is_jal = Input(Bool())
  })

  addResource("/vsrc/btb_harness.v")
  addResource("/csrc/btb_sw.cc")
}
