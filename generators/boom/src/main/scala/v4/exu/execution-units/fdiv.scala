//******************************************************************************
// Copyright (c) 2016 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// FDiv/FSqrt Unit
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v4.exu

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.FPConstants._
import freechips.rocketchip.tile
import boom.v4.common._
import boom.v4.util._
import freechips.rocketchip.tile.HasFPUParameters
import freechips.rocketchip.util.uintToBitPat

/**
  * Modern implementation that doesn't rely on upconverting, compared to original FDivSqrtUnit
  */
class FDivSqrtUnit2(implicit p: Parameters)
  extends FunctionalUnit(
    dataWidth = 65,
    needsFcsr = true)
  with tile.HasFPUParameters
{
  val divSqrt_inFlight = WireInit(false.B)

  val r_req = Reg(Valid(new FuncUnitReq(dataWidth=65)))
  val r_sigs = Reg(new tile.FPUCtrlSigs)
  val r_rm = Reg(UInt())
  val r_out_valid = Reg(Bool())
  val r_out_flags = Reg(UInt())
  val r_out_wdata = Reg(UInt())
  val kill = IsKilledByBranch(io.brupdate, io.kill, r_req)
  io.req.ready := !r_req.valid

  r_req := UpdateBrMask(io.brupdate, io.kill, r_req)
  r_out_valid := r_out_valid && !kill
  io.req.ready := !r_req.valid && !divSqrt_inFlight && !r_out_valid

  val fpiu = Module(new tile.FPToInt)
  fpiu.io.in.valid := io.req.valid
  fpiu.io.in.bits.viewAsSupertype(new tile.FPUCtrlSigs)  := io.req.bits.uop.fp_ctrl
  fpiu.io.in.bits.rm := io.req.bits.uop.fp_rm
  fpiu.io.in.bits.in1 := unbox(io.req.bits.rs1_data, io.req.bits.uop.fp_ctrl.typeTagIn, None)
  fpiu.io.in.bits.in2 := unbox(io.req.bits.rs2_data, io.req.bits.uop.fp_ctrl.typeTagIn, None)
  fpiu.io.in.bits.in3 := DontCare
  fpiu.io.in.bits.typ := io.req.bits.uop.fp_typ
  fpiu.io.in.bits.fmaCmd := 0.U
  fpiu.io.in.bits.fmt := DontCare

  when (io.req.fire) {
    r_req.valid := !IsKilledByBranch(io.brupdate, io.kill, io.req.bits.uop.br_mask)
    r_req.bits  := UpdateBrMask(io.brupdate, io.req.bits)
    r_sigs := io.req.bits.uop.fp_ctrl
    r_rm := io.fcsr_rm
  }
  for (t <- floatTypes) {
    val tag = r_sigs.typeTagOut
    val divSqrt = Module(new hardfloat.DivSqrtRecFN_small(t.exp, t.sig, 0))
    divSqrt.io.inValid := r_req.valid && (tag === typeTag(t).U) && (r_sigs.div || r_sigs.sqrt) && !divSqrt_inFlight && !r_out_valid
    divSqrt.io.sqrtOp := r_sigs.sqrt
    divSqrt.io.a := maxType.unsafeConvert(fpiu.io.out.bits.in.in1, t)
    divSqrt.io.b := maxType.unsafeConvert(fpiu.io.out.bits.in.in2, t)
    divSqrt.io.roundingMode := fpiu.io.out.bits.in.rm
    divSqrt.io.detectTininess := hardfloat.consts.tininess_afterRounding
    when (!divSqrt.io.inReady || divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt) { divSqrt_inFlight := true.B }
    when (divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt) {
      r_out_valid := r_req.valid && !kill
      r_out_flags := divSqrt.io.exceptionFlags
      r_out_wdata := box(sanitizeNaN(divSqrt.io.out, t), tag)
    }
  }

  io.resp.valid := r_out_valid && r_req.valid
  io.resp.bits.uop := r_req.bits.uop
  io.resp.bits.data := r_out_wdata
  io.resp.bits.fflags.valid := io.resp.valid
  io.resp.bits.fflags.bits  := r_out_flags
  when (io.resp.fire || reset.asBool) {
    r_req.valid := false.B
    r_out_valid := false.B
  }
}
