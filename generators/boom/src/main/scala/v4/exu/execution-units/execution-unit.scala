//******************************************************************************
// Copyright (c) 2013 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Execution Units
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// The issue window schedules micro-ops onto a specific execution pipeline
// A given execution pipeline may contain multiple functional units; one or more
// read ports, and one or more writeports.

package boom.v4.exu

import scala.collection.mutable.{ArrayBuffer}

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket.{BP, SFenceReq, CSR}
import freechips.rocketchip.rocket.constants.{MemoryOpConstants}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tile
import freechips.rocketchip.util._

import boom.v4.common._
import boom.v4.ifu.FTQInfo
import boom.v4.util._


class Wakeup(implicit p: Parameters) extends BoomBundle
  with HasBoomUOP
{
  val bypassable = Bool()
  val speculative_mask = UInt(aluWidth.W)
  val rebusy = Bool()
}



class ExeUnitResp(val dataWidth: Int)(implicit p: Parameters) extends BoomBundle
  with HasBoomUOP
{
  val data = Bits(dataWidth.W)
  val predicated = Bool() // Was this predicated off?
  val fflags = Valid(UInt(tile.FPConstants.FLAGS_SZ.W))
}

class MemGen(implicit p: Parameters) extends BoomBundle
  with HasBoomUOP
{
  val data = UInt(xLen.W)
}

class CSRResp(implicit p: Parameters) extends BoomBundle
  with HasBoomUOP
{
  val data = UInt(xLen.W)
  val addr = UInt(CSR.ADDRSZ.W)
}

abstract class ExecutionUnit(name: String)(implicit p: Parameters) extends BoomMultiIOModule
{
  val fu_types = ArrayBuffer[(Int, Bool, String)]()
  def get_all_fu_types(): Vec[Bool] = {
    val r = WireInit(VecInit(Seq.fill(FC_SZ) { false.B }))
    fu_types.map { case (code, _, _) => { r(code) := true.B } }
    r
  }
  def get_ready_fu_types(): Vec[Bool] = {
    val r = WireInit(VecInit(Seq.fill(FC_SZ) { false.B }))
    fu_types.map { case (code, ready, _) => { when (ready) { r(code) := true.B } } }
    r
  }

  val io_kill = IO(Input(Bool()))
  val io_brupdate = IO(Input(new BrUpdateInfo))
  val io_status = IO(Input(new freechips.rocketchip.rocket.MStatus))
  val io_ready_fu_types = IO(Output(Vec(FC_SZ, Bool())))

  val io_fcsr_rm = IO(Input(UInt(tile.FPConstants.RM_SZ.W)))
  override def toString = {
    BoomCoreStringPrefix(s"===${name}ExeUnit") +
    fu_types.map { case (_, _, s) => BoomCoreStringPrefix(s" - ${s}") }.reduce(_+_)
  }

  val io_iss_uop = IO(Input(Valid(new MicroOp)))

  val arb_uop = Reg(Valid(new MicroOp))
  arb_uop.valid := io_iss_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, io_iss_uop.bits)
  arb_uop.bits  := UpdateBrMask(io_brupdate, io_iss_uop.bits)
  val rrd_uop = Reg(Valid(new MicroOp))
  rrd_uop.valid := arb_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, arb_uop.bits)
  rrd_uop.bits  := UpdateBrMask(io_brupdate, arb_uop.bits)
  val exe_uop = Reg(Valid(new MicroOp))
  exe_uop.valid := rrd_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, rrd_uop.bits)
  exe_uop.bits  := UpdateBrMask(io_brupdate, rrd_uop.bits)
}

trait HasIrfReadPorts { this: ExecutionUnit =>
  def nReaders: Int

  val io_arb_irf_reqs = IO(Vec(nReaders, Decoupled(UInt(maxPregSz.W))))
  val io_arb_rebusys  = IO(Input (Vec(lsuWidth, Valid(new Wakeup))))
  val io_rrd_irf_resps    = IO(Input (Vec(nReaders , UInt(xLen.W))))
  val io_rrd_irf_bypasses = IO(Input (Vec(coreWidth + lsuWidth, Valid(new ExeUnitResp(xLen)))))


  def rrd_bypass_hit(prs: UInt, rdata: UInt): (Bool, UInt) = {
    val hits = io_rrd_irf_bypasses map { b => b.valid && prs === b.bits.uop.pdst }
    (hits.reduce(_||_), Mux(hits.reduce(_||_), Mux1H(hits, io_rrd_irf_bypasses.map(_.bits.data)), rdata))
  }

  def rebusied(prs: UInt): Bool = {
    io_arb_rebusys.map { r => r.valid && r.bits.rebusy && r.bits.uop.pdst === prs }.reduce(_||_)
  }

  io_arb_irf_reqs(0).valid := arb_uop.valid && arb_uop.bits.lrs1_rtype === RT_FIX && !arb_uop.bits.iw_p1_bypass_hint
  io_arb_irf_reqs(0).bits  := arb_uop.bits.prs1

  if (nReaders == 2) {
    io_arb_irf_reqs(1).valid := arb_uop.valid && arb_uop.bits.lrs2_rtype === RT_FIX && !arb_uop.bits.iw_p2_bypass_hint
    io_arb_irf_reqs(1).bits  := arb_uop.bits.prs2
  }
  val arb_rebusied_prs1 = arb_uop.bits.lrs1_rtype === RT_FIX && rebusied(arb_uop.bits.prs1)
  val arb_rebusied_prs2 = arb_uop.bits.lrs2_rtype === RT_FIX && rebusied(arb_uop.bits.prs2) && (nReaders == 2).B
  val arb_rebusied      = arb_rebusied_prs1 || arb_rebusied_prs2


  val exe_rs1_data = Reg(UInt(xLen.W))
  val exe_rs2_data = Reg(UInt(xLen.W))
  val (rs1_hit, rs1_data) = rrd_bypass_hit(rrd_uop.bits.prs1, io_rrd_irf_resps(0))
  assert(!(rrd_uop.valid && rrd_uop.bits.lrs1_rtype === RT_FIX && rrd_uop.bits.iw_p1_bypass_hint && !rs1_hit))
  exe_rs1_data := Mux(rrd_uop.bits.lrs1_rtype === RT_ZERO, 0.U, rs1_data)

  if (nReaders == 2) {
    val (rs2_hit, rs2_data) = rrd_bypass_hit(rrd_uop.bits.prs2, io_rrd_irf_resps(1))
    assert(!(rrd_uop.valid && rrd_uop.bits.lrs2_rtype === RT_FIX && rrd_uop.bits.iw_p2_bypass_hint && !rs2_hit))
    exe_rs2_data := Mux(rrd_uop.bits.lrs2_rtype === RT_ZERO, 0.U, rs2_data)
  } else {
    exe_rs2_data := DontCare
  }


}

trait HasImmrfReadPort { this: ExecutionUnit =>
  val io_arb_immrf_req    = IO(Decoupled(UInt(immPregSz.W)))
  assert(io_arb_immrf_req.ready)
  val io_rrd_immrf_resp   = IO(Input(UInt(xLen.W)))
  val io_rrd_immrf_wakeup = IO(Output(Valid(new Wakeup)))


  io_arb_immrf_req.valid := (arb_uop.valid &&
    !arb_uop.bits.imm_sel.isOneOf(IS_N, IS_SH)
  )
  io_arb_immrf_req.bits  := arb_uop.bits.pimm

  io_rrd_immrf_wakeup.valid := (rrd_uop.valid &&
    !rrd_uop.bits.imm_sel.isOneOf(IS_N, IS_SH)
  )
  io_rrd_immrf_wakeup.bits.speculative_mask := false.B
  io_rrd_immrf_wakeup.bits.rebusy := false.B
  io_rrd_immrf_wakeup.bits.bypassable := false.B
  io_rrd_immrf_wakeup.bits.uop := rrd_uop.bits

  val exe_imm_data = RegNext(Mux(rrd_uop.bits.imm_sel === IS_SH,
    Sext(rrd_uop.bits.pimm, xLen),
    Sext(ImmGen(io_rrd_immrf_resp, rrd_uop.bits.imm_sel), xLen)
  ))


}

trait HasPrfReadPort { this: ExecutionUnit =>
  val io_arb_prf_req    = IO(Decoupled(UInt(log2Ceil(ftqSz).W)))
  assert(io_arb_prf_req.ready)
  val io_rrd_prf_resp   = IO(Input(Bool()))

  io_arb_prf_req.valid := arb_uop.valid
  io_arb_prf_req.bits  := arb_uop.bits.ppred

  val exe_pred_data = Reg(Bool())
  exe_pred_data := io_rrd_prf_resp

}

trait HasBrfReadPort { this: ExecutionUnit =>
  val io_arb_brf_req    = IO(Decoupled(UInt(brTagSz.W)))
  assert(io_arb_brf_req.ready)
  val io_rrd_brf_resp   = IO(Input(new BrInfoBundle))

  io_arb_brf_req.valid := (arb_uop.valid && arb_uop.bits.is_br)
  io_arb_brf_req.bits  := arb_uop.bits.br_tag

  exe_uop.bits.ldq_idx   := io_rrd_brf_resp.ldq_idx
  exe_uop.bits.stq_idx   := io_rrd_brf_resp.stq_idx
  exe_uop.bits.rxq_idx   := io_rrd_brf_resp.rxq_idx

}

trait HasFrfReadPorts { this: ExecutionUnit =>
  val io_arb_frf_reqs  = IO(Vec(3, Decoupled(UInt(maxPregSz.W))))
  val io_rrd_frf_resps = IO(Input (Vec(3, UInt((xLen+1).W))))
  val io_rrd_frf_bypasses = IO(Input(Vec(fpWidth, Valid(new ExeUnitResp(fLen+1)))))

  def rrd_bypass_hit(prs: UInt, rdata: UInt): (Bool, UInt) = {
    val hits = io_rrd_frf_bypasses map { b => b.valid && prs === b.bits.uop.pdst }
    (hits.reduce(_||_), Mux(hits.reduce(_||_), Mux1H(hits, io_rrd_frf_bypasses.map(_.bits.data)), rdata))
  }

  io_arb_frf_reqs(0).valid := arb_uop.valid && arb_uop.bits.lrs1_rtype === RT_FLT && !arb_uop.bits.iw_p1_bypass_hint
  io_arb_frf_reqs(0).bits  := arb_uop.bits.prs1
  io_arb_frf_reqs(1).valid := arb_uop.valid && arb_uop.bits.lrs2_rtype === RT_FLT && !arb_uop.bits.iw_p2_bypass_hint
  io_arb_frf_reqs(1).bits  := arb_uop.bits.prs2
  io_arb_frf_reqs(2).valid := arb_uop.valid && arb_uop.bits.frs3_en && !arb_uop.bits.iw_p3_bypass_hint
  io_arb_frf_reqs(2).bits  := arb_uop.bits.prs3

  val exe_rs1_data = Reg(UInt((fLen+1).W))
  val exe_rs2_data = Reg(UInt((fLen+1).W))
  val exe_rs3_data = Reg(UInt((fLen+1).W))

  val (rs1_hit, rs1_data) = rrd_bypass_hit(rrd_uop.bits.prs1, io_rrd_frf_resps(0))
  assert(!(rrd_uop.valid && rrd_uop.bits.lrs1_rtype === RT_FLT && rrd_uop.bits.iw_p1_bypass_hint && !rs1_hit))
  exe_rs1_data := rs1_data

  val (rs2_hit, rs2_data) = rrd_bypass_hit(rrd_uop.bits.prs2, io_rrd_frf_resps(1))
  assert(!(rrd_uop.valid && rrd_uop.bits.lrs2_rtype === RT_FLT && rrd_uop.bits.iw_p2_bypass_hint && !rs2_hit))
  exe_rs2_data := rs2_data

  val (rs3_hit, rs3_data) = rrd_bypass_hit(rrd_uop.bits.prs3, io_rrd_frf_resps(2))
  assert(!(rrd_uop.valid && rrd_uop.bits.iw_p3_bypass_hint && !rs3_hit))
  exe_rs3_data := rs3_data
}

trait HasFtqReadPort { this: ExecutionUnit =>
  val io_arb_ftq_reqs  = IO(Vec(2, Decoupled(UInt(log2Ceil(ftqSz).W))))
  val io_rrd_ftq_resps = IO(Vec(2, Input(new FTQInfo)))

  // Only allow one SFB branch through multiple pipes, to avoid unnecessary predicate wakeup logic
  io_arb_ftq_reqs(0).valid := arb_uop.valid && (arb_uop.bits.op1_sel === OP1_PC || arb_uop.bits.is_sfb_br)
  io_arb_ftq_reqs(0).bits  := arb_uop.bits.ftq_idx

  // Only JALR checks the next-pc
  io_arb_ftq_reqs(1).valid := arb_uop.valid && (arb_uop.bits.br_type === B_JR || arb_uop.bits.is_sfb_br)
  io_arb_ftq_reqs(1).bits  := WrapInc(arb_uop.bits.ftq_idx, ftqSz)

  val exe_ftq_data = Reg(Vec(2, new FTQInfo))
  exe_ftq_data := io_rrd_ftq_resps
}

class MemExeUnit(
  val hasAGen          : Boolean       = false,
  val hasDGen          : Boolean       = false
)(implicit p: Parameters) extends ExecutionUnit("Mem")
  with HasIrfReadPorts
  with HasImmrfReadPort
{
  def nReaders = 1

  val io_squash_iss = IO(Output(Bool()))
  io_squash_iss := (io_arb_irf_reqs(0).valid && !io_arb_irf_reqs(0).ready)

  when (io_squash_iss || arb_rebusied) {
    val will_replay = arb_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, arb_uop.bits) && !arb_rebusied
    arb_uop.valid := will_replay
    arb_uop.bits  := UpdateBrMask(io_brupdate, arb_uop.bits)
    arb_uop.bits.iw_p1_bypass_hint := false.B
    arb_uop.bits.iw_p2_bypass_hint := false.B

    rrd_uop.valid := false.B
  }


  val io_agen = if (hasAGen) {
    val loads_saturating = exe_uop.valid && exe_uop.bits.uses_ldq && exe_uop.bits.fu_code(FC_AGEN)
    val saturating_loads_counter = RegInit(0.U(5.W))
    when (loads_saturating) { saturating_loads_counter := saturating_loads_counter + 1.U }
      .otherwise { saturating_loads_counter := 0.U }
    val pause_mem = RegNext(loads_saturating) && saturating_loads_counter === ~(0.U(5.W))
    val load_ready = !pause_mem
    fu_types += ((FC_AGEN, load_ready, "AGen"))

    val sum = (exe_rs1_data.asSInt + exe_imm_data.asSInt).asUInt
    val ea_sign = Mux(sum(vaddrBits-1), ~sum(63,vaddrBits) === 0.U,
                                         sum(63,vaddrBits) =/= 0.U)
    val effective_address = Cat(ea_sign, sum(vaddrBits-1,0)).asUInt

    val agen = IO(Output(Valid(new MemGen)))
    if (enableAgenStage) {
      val agen_reg = Reg(Valid(new MemGen))
      agen_reg.valid    := (
        exe_uop.valid &&
        exe_uop.bits.fu_code(FC_AGEN) &&
        !IsKilledByBranch(io_brupdate, io_kill, exe_uop.bits)
      )
      agen_reg.bits.uop  := UpdateBrMask(io_brupdate, exe_uop.bits)
      agen_reg.bits.data := Sext(effective_address, xLen)

      agen := agen_reg
    } else {
      agen.valid     := exe_uop.valid && exe_uop.bits.fu_code(FC_AGEN)
      agen.bits.uop  := exe_uop.bits
      agen.bits.data := Sext(effective_address, xLen)
    }
    Some(agen)
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_AGEN)))
    None
  }

  val io_dgen = if (hasDGen) {
    fu_types += ((FC_DGEN, true.B, "DGen"))
    val dgen = IO(Output(Valid(new MemGen)))
    dgen.valid     := exe_uop.valid && exe_uop.bits.fu_code(FC_DGEN)
    dgen.bits.data := exe_rs1_data
    dgen.bits.uop  := exe_uop.bits
    Some(dgen)
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_DGEN)))
    None
  }

  io_ready_fu_types := get_ready_fu_types()
}

class UniqueExeUnit(
  val hasCSR           : Boolean       = false,
  val hasMul           : Boolean       = false,
  val hasDiv           : Boolean       = false,
  val hasIfpu          : Boolean       = false,
  val hasRocc          : Boolean       = false
)(implicit p: Parameters) extends ExecutionUnit("Unq")
  with HasIrfReadPorts
  with HasImmrfReadPort
  with MemoryOpConstants
{
  def nReaders = 2

  val io_squash_iss = IO(Output(Bool()))
  io_squash_iss := ((io_arb_irf_reqs(0).valid && !io_arb_irf_reqs(0).ready) ||
                    (io_arb_irf_reqs(1).valid && !io_arb_irf_reqs(1).ready))

  when (io_squash_iss || arb_rebusied) {
    val will_replay = arb_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, arb_uop.bits) && !arb_rebusied
    arb_uop.valid := will_replay
    arb_uop.bits  := UpdateBrMask(io_brupdate, arb_uop.bits)
    arb_uop.bits.iw_p1_bypass_hint := false.B
    arb_uop.bits.iw_p2_bypass_hint := false.B

    rrd_uop.valid := false.B
  }

  val exe_int_req = Wire(new FuncUnitReq(xLen))
  exe_int_req.uop       := exe_uop.bits
  exe_int_req.rs1_data  := exe_rs1_data
  exe_int_req.rs2_data  := exe_rs2_data
  exe_int_req.rs3_data  := DontCare
  exe_int_req.imm_data  := exe_imm_data
  exe_int_req.pred_data := DontCare
  exe_int_req.ftq_info  := DontCare

  val io_mul_resp = if (hasMul) {
    fu_types += ((FC_MUL, true.B, "IMul"))
    val imul = Module(new PipelinedMulUnit(imulLatency, xLen))
    imul.io.req.valid  := exe_uop.valid && exe_uop.bits.fu_code(FC_MUL)
    imul.io.req.bits   := exe_int_req
    imul.io.brupdate   := io_brupdate
    imul.io.kill       := io_kill
    imul.io.resp.ready := true.B

    val resp = IO(Output(Valid(new ExeUnitResp(xLen))))
    resp := imul.io.resp
    Some(resp)
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_MUL)))
    None
  }

  val (io_csr_resp, io_sfence) = if (hasCSR) {
    fu_types += ((FC_CSR, true.B, "CSR"))
    val alu = Module(new ALUUnit(dataWidth = xLen))
    alu.io.req.valid := exe_uop.valid && exe_uop.bits.fu_code(FC_CSR)
    alu.io.req.bits  := exe_int_req
    alu.io.resp.ready := true.B
    alu.io.brupdate   := io_brupdate
    alu.io.kill       := io_kill

    val c = IO(Output(Valid(new CSRResp)))
    c.valid     := RegNext(alu.io.resp.valid && exe_uop.bits.csr_cmd =/= CSR.N)
    c.bits.uop  := RegNext(alu.io.resp.bits.uop)
    c.bits.data := RegNext(alu.io.resp.bits.data)
    c.bits.addr := RegNext(exe_imm_data)

    val s = IO(Valid(new SFenceReq))
    s.valid    := RegNext(exe_uop.valid && exe_uop.bits.is_sfence)
    s.bits.rs1 := RegNext(exe_uop.bits.pimm(0))
    s.bits.rs2 := RegNext(exe_uop.bits.pimm(1))
    s.bits.addr := RegNext(exe_rs1_data)
    s.bits.asid := RegNext(exe_rs2_data)
    s.bits.hv := RegNext(exe_uop.bits.mem_cmd === M_HFENCEV)
    s.bits.hg := RegNext(exe_uop.bits.mem_cmd === M_HFENCEG)

    (Some(c), Some(s))
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_CSR)))
    assert(!(exe_uop.valid && exe_uop.bits.mem_cmd === M_SFENCE))
    (None, None)
  }

  val (io_rocc_resp, io_rocc_core) = if (hasRocc) {
    require(hasCSR)
    val rocc_core = IO(new RoCCShimCoreIO)
    val rocc_resp = IO(Decoupled(new ExeUnitResp(xLen)))
    val rocc = Module(new RoCCShim)
    rocc.io.req.valid         := exe_uop.valid && exe_uop.bits.is_rocc
    rocc.io.req.bits          := exe_int_req
    rocc.io.brupdate          := io_brupdate // We should assert on this somewhere
    rocc.io.status            := io_status
    rocc.io.exception         := io_kill
    rocc_core                 <> rocc.io.core

    rocc_resp <> rocc.io.resp
    (Some(rocc_resp), Some(rocc_core))
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.is_rocc))
    (None, None)
  }

  val (io_ifpu_resp) = if (hasIfpu) {
    val ifpu_ready = Wire(Bool())
    fu_types += ((FC_I2F, ifpu_ready, "IFPU"))

    val ifpu = Module(new IntToFPUnit(latency=intToFpLatency))
    ifpu.io.req.valid  := exe_uop.valid && exe_uop.bits.fu_code(FC_I2F)
    ifpu.io.req.bits   := exe_int_req
    ifpu.io.req.bits.uop.fp_rm  := exe_uop.bits.prs2(4,2)
    ifpu.io.req.bits.uop.fp_typ := exe_uop.bits.prs2(1,0)
    ifpu.io.fcsr_rm    := io_fcsr_rm
    ifpu.io.brupdate   := io_brupdate
    ifpu.io.kill       := io_kill

    // buffer up results since we share write-port on integer regfile.
    val queue = Module(new BranchKillableQueue(new ExeUnitResp(xLen+1),
      entries = intToFpLatency + 6)) // TODO being overly conservative
    queue.io.enq <> ifpu.io.resp
    queue.io.brupdate := io_brupdate
    queue.io.flush    := io_kill
    assert (!(queue.io.enq.valid && !queue.io.enq.ready))
    ifpu_ready := RegNext(queue.io.count < 2.U)

    val ifpu_resp = IO(Decoupled(new ExeUnitResp(xLen+1)))
    ifpu_resp <> queue.io.deq
    (Some(ifpu_resp))
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_I2F)))
    (None)
  }

  val (io_div_resp) = if (hasDiv) {
    val div_ready = Wire(Bool())
    fu_types += ((FC_DIV, div_ready, "IDiv"))

    val div = Module(new DivUnit(xLen))
    assert(!(div.io.req.valid && !div.io.req.ready))
    div.io.req.valid := exe_uop.valid && exe_uop.bits.fu_code(FC_DIV)
    div.io.req.bits  := exe_int_req
    div.io.brupdate  := io_brupdate
    div.io.kill      := io_kill

    div_ready := (div.io.req.ready &&
      !(exe_uop.valid && exe_uop.bits.fu_code(FC_DIV)) &&
      !(rrd_uop.valid && rrd_uop.bits.fu_code(FC_DIV)) &&
      !(arb_uop.valid && arb_uop.bits.fu_code(FC_DIV))
    )

    val div_resp = IO(Decoupled(new ExeUnitResp(xLen)))
    div_resp <> div.io.resp
    Some(div_resp)
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_DIV)))
    (None)
  }

  io_ready_fu_types := get_ready_fu_types()

}

class ALUExeUnit(
  val id: Int
)(implicit p: Parameters) extends ExecutionUnit("Alu")
  with HasIrfReadPorts
  with HasPrfReadPort
  with HasImmrfReadPort
  with HasBrfReadPort
  with HasFtqReadPort
{
  def nReaders = 2

  val io_fast_wakeup = IO(Output(Valid(new Wakeup)))
  io_fast_wakeup.valid    := (
    io_iss_uop.valid &&
    (io_iss_uop.bits.dst_rtype === RT_FIX)
  )
  io_fast_wakeup.bits.uop := io_iss_uop.bits
  io_fast_wakeup.bits.speculative_mask := (1 << id).U
  io_fast_wakeup.bits.rebusy := false.B
  io_fast_wakeup.bits.bypassable := true.B

  val io_fast_pred_wakeup = IO(Output(Valid(new Wakeup)))
  io_fast_pred_wakeup.valid    := rrd_uop.valid && rrd_uop.bits.is_sfb_br
  io_fast_pred_wakeup.bits.uop := rrd_uop.bits
  io_fast_pred_wakeup.bits.speculative_mask := 0.U
  io_fast_pred_wakeup.bits.rebusy := false.B
  io_fast_pred_wakeup.bits.bypassable := false.B


  val io_squash_iss = IO(Output(Bool()))

  io_squash_iss := ((io_arb_irf_reqs(0).valid && !io_arb_irf_reqs(0).ready) ||
                    (io_arb_irf_reqs(1).valid && !io_arb_irf_reqs(1).ready) ||
                    (io_arb_ftq_reqs(0).valid && !io_arb_ftq_reqs(0).ready) ||
                    (io_arb_ftq_reqs(1).valid && !io_arb_ftq_reqs(1).ready))

  val io_child_rebusy = IO(Output(UInt(aluWidth.W)))
  io_child_rebusy := 0.U
  when (arb_rebusied && arb_uop.valid) {
    io_child_rebusy := (1 << id).U
  }

  // The arbiter didn't grant us a slot. Thus, we should replay the instruction in this slot,
  // But next time we read, it reads from the regfile, not the bypass paths, so disable the bypass hints
  when (io_squash_iss || arb_rebusied) {
    val will_replay = arb_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, arb_uop.bits) && !arb_rebusied
    arb_uop.valid := will_replay
    arb_uop.bits  := UpdateBrMask(io_brupdate, arb_uop.bits)
    arb_uop.bits.iw_p1_bypass_hint := false.B
    arb_uop.bits.iw_p2_bypass_hint := false.B

    rrd_uop.valid := false.B

  }

  val exe_int_req = Wire(new FuncUnitReq(xLen))
  exe_int_req.uop       := exe_uop.bits
  exe_int_req.rs1_data  := exe_rs1_data
  exe_int_req.rs2_data  := exe_rs2_data
  exe_int_req.rs3_data  := DontCare
  exe_int_req.imm_data  := exe_imm_data
  exe_int_req.pred_data := exe_pred_data
  exe_int_req.ftq_info  := exe_ftq_data

  fu_types += ((FC_ALU, true.B, "ALU"))

  val alu = Module(new ALUUnit(dataWidth = xLen))
  alu.io.req.valid  := exe_uop.valid && exe_uop.bits.fu_code(FC_ALU)
  alu.io.req.bits   := exe_int_req
  alu.io.resp.ready := true.B
  alu.io.brupdate   := io_brupdate
  alu.io.kill       := io_kill

  val io_alu_resp = IO(Output(Valid(new ExeUnitResp(xLen))))
  io_alu_resp.valid := alu.io.resp.valid
  io_alu_resp.bits  := alu.io.resp.bits

  val io_brinfo = IO(Output(Valid(new BrResolutionInfo)))
  io_brinfo := alu.io.brinfo

  io_ready_fu_types := get_ready_fu_types()

}

class FPExeUnit(val hasFDiv: Boolean = false, val hasFpiu: Boolean = false)(implicit p: Parameters)
  extends ExecutionUnit("FP")
  with tile.HasFPUParameters
  with HasFrfReadPorts
{
  val io_squash_iss = IO(Output(Bool()))
  io_squash_iss := (
    (io_arb_frf_reqs(0).valid && !io_arb_frf_reqs(0).ready) ||
    (io_arb_frf_reqs(1).valid && !io_arb_frf_reqs(1).ready) ||
    (io_arb_frf_reqs(2).valid && !io_arb_frf_reqs(2).ready)
  )

  when (io_squash_iss) {
    val will_replay = arb_uop.valid && !IsKilledByBranch(io_brupdate, io_kill, arb_uop.bits)
    arb_uop.valid := will_replay
    arb_uop.bits  := UpdateBrMask(io_brupdate, arb_uop.bits)
    arb_uop.bits.iw_p1_bypass_hint := false.B
    arb_uop.bits.iw_p2_bypass_hint := false.B
    arb_uop.bits.iw_p3_bypass_hint := false.B
    rrd_uop.valid := false.B
  }

  val exe_fp_req = Wire(new FuncUnitReq(xLen+1))
  exe_fp_req.uop := exe_uop.bits
  exe_fp_req.rs1_data := exe_rs1_data
  exe_fp_req.rs2_data := exe_rs2_data
  exe_fp_req.rs3_data := exe_rs3_data
  exe_fp_req.pred_data := DontCare
  exe_fp_req.imm_data := DontCare
  exe_fp_req.ftq_info := DontCare

  val fpu = Module(new FPUUnit)
  fu_types += ((FC_FPU, true.B, "FPU"))
  fpu.io.req.valid := exe_uop.valid && (
    exe_uop.bits.fu_code(FC_FPU) || (if (hasFpiu) exe_uop.bits.fu_code(FC_F2I) else false.B)
  )
  fpu.io.req.bits := exe_fp_req
  fpu.io.fcsr_rm  := io_fcsr_rm
  fpu.io.brupdate := io_brupdate
  fpu.io.kill     := io_kill
  fpu.io.resp.ready := true.B

  val io_wakeup = IO(Output(Valid(new Wakeup)))
  val fastWakeupLatency = dfmaLatency - 3 // Three stages WAKE-ISS-ARB
  require (fastWakeupLatency >= 0)
  val fast_wakeups = Wire(Vec(fastWakeupLatency + 1, Valid(new Wakeup)))
  fast_wakeups(0).valid    := exe_uop.valid && exe_uop.bits.fu_code(FC_FPU)
  fast_wakeups(0).bits.uop := exe_uop.bits
  fast_wakeups(0).bits.speculative_mask := 0.U
  fast_wakeups(0).bits.rebusy           := false.B
  fast_wakeups(0).bits.bypassable       := true.B
  for (i <- 0 until fastWakeupLatency) {
    fast_wakeups(i+1) := RegNext(UpdateBrMask(io_brupdate, io_kill, fast_wakeups(i)))
  }
  io_wakeup := fast_wakeups(fastWakeupLatency)

  val io_fpu_resp = IO(Output(Valid(new ExeUnitResp(xLen+1))))
  io_fpu_resp.valid := fpu.io.resp.valid && !fpu.io.resp.bits.uop.fu_code(FC_F2I)
  io_fpu_resp.bits  := fpu.io.resp.bits

  val io_fdiv_resp = if (hasFDiv) {
    val fdivsqrt_ready = Wire(Bool())
    fu_types += ((FC_FDV, fdivsqrt_ready, "FDiv"))

    val fdivsqrt = Module(new FDivSqrtUnit2)
    assert(!(fdivsqrt.io.req.valid && !fdivsqrt.io.req.ready))
    fdivsqrt.io.req.valid := exe_uop.valid && exe_uop.bits.fu_code(FC_FDV)
    fdivsqrt.io.req.bits := exe_fp_req
    fdivsqrt.io.brupdate := io_brupdate
    fdivsqrt.io.kill     := io_kill
    fdivsqrt.io.fcsr_rm  := io_fcsr_rm

    fdivsqrt_ready := (fdivsqrt.io.req.ready &&
      !(exe_uop.valid && exe_uop.bits.fu_code(FC_FDV)) &&
      !(rrd_uop.valid && rrd_uop.bits.fu_code(FC_FDV)) &&
      !(arb_uop.valid && arb_uop.bits.fu_code(FC_FDV))
    )

    val fdiv_resp = IO(Decoupled(new ExeUnitResp(xLen+1)))
    fdiv_resp <> fdivsqrt.io.resp
    Some(fdiv_resp)
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_FDV)))
    None
  }

  val (io_fpiu_resp, io_dgen) = if (hasFpiu) {
    val fpiu_ready = Wire(Bool())
    fu_types += ((FC_F2I, fpiu_ready, "Fpiu"))

    val queue = Module(new BranchKillableQueue(new ExeUnitResp(xLen+1),
      entries = dfmaLatency + 6)) // TODO being overly conservative
    fpiu_ready               := RegNext(queue.io.count < 2.U)
    queue.io.enq.valid       := ( fpu.io.resp.valid &&
                                  fpu.io.resp.bits.uop.fu_code(FC_F2I) &&
                                 !fpu.io.resp.bits.uop.uses_stq) // STA means store data gen for floating point
    queue.io.enq.bits        := fpu.io.resp.bits
    queue.io.brupdate        := io_brupdate
    queue.io.flush           := io_kill
    assert(!(queue.io.enq.valid && !queue.io.enq.ready))

    val fpiu_resp = IO(Decoupled(new ExeUnitResp(xLen)))
    fpiu_resp <> queue.io.deq

    val dgen = IO(Valid(new MemGen))
    dgen.valid     := RegNext(exe_uop.valid && exe_uop.bits.uses_stq && !IsKilledByBranch(io_brupdate, io_kill, exe_uop.bits))
    dgen.bits.uop  := RegNext(exe_uop.bits)
    dgen.bits.data := RegNext(ieee(exe_rs2_data))
    (Some(fpiu_resp), Some(dgen))
  } else {
    assert(!(exe_uop.valid && exe_uop.bits.fu_code(FC_F2I)))
    (None, None)
  }

  io_ready_fu_types := get_ready_fu_types
}
