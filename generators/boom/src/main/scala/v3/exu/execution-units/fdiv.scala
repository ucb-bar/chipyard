//******************************************************************************
// Copyright (c) 2016 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// FDiv/FSqrt Unit
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.exu

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.FPConstants._
import freechips.rocketchip.tile
import boom.v3.common._
import boom.v3.util._
import freechips.rocketchip.tile.HasFPUParameters
import freechips.rocketchip.util.uintToBitPat

/**
 * Decoder for FPU divide and square root signals
 */
class UOPCodeFDivDecoder(implicit p: Parameters) extends BoomModule
  with HasFPUParameters
{
  val io = IO(new Bundle {
    val uopc = Input(Bits(UOPC_SZ.W))
    val sigs = Output(new tile.FPUCtrlSigs())
  })

  val N = BitPat("b0")
  val Y = BitPat("b1")
  val X = BitPat("b?")

  val decoder = freechips.rocketchip.rocket.DecodeLogic(io.uopc,
    // Note: not all of these signals are used or necessary, but we're
    // constrained by the need to fit the rocket.FPU units' ctrl signals.
    //                                      swap12         fma
    //                                      | swap32       | div
    //                                      | | typeTagIn  | | sqrt
    //                           ldst       | | | typeTagOut | | wflags
    //                           | wen      | | | | from_int | | |
    //                           | | ren1   | | | | | to_int | | |
    //                           | | | ren2 | | | | | | fast | | |
    //                           | | | | ren3 | | | | | |  | | | |
    //                           | | | | |  | | | | | | |  | | | |
    /* Default */           List(X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    Array(
      BitPat(uopFDIV_S)  -> List(X,X,Y,Y,X, X,X,S,S,X,X,X, X,Y,N,Y),
      BitPat(uopFDIV_D)  -> List(X,X,Y,Y,X, X,X,D,D,X,X,X, X,Y,N,Y),
      BitPat(uopFSQRT_S) -> List(X,X,Y,N,X, X,X,S,S,X,X,X, X,N,Y,Y),
      BitPat(uopFSQRT_D) -> List(X,X,Y,N,X, X,X,D,D,X,X,X, X,N,Y,Y)
    ): Array[(BitPat, List[BitPat])])

  val s = io.sigs
  val sigs = Seq(s.ldst, s.wen, s.ren1, s.ren2, s.ren3, s.swap12,
                 s.swap23, s.typeTagIn, s.typeTagOut, s.fromint, s.toint, s.fastpipe, s.fma,
                 s.div, s.sqrt, s.wflags)
  s.vec := false.B
  sigs zip decoder map {case(s,d) => s := d}
}

/**
 * fdiv/fsqrt is douple-precision. Must upconvert inputs and downconvert outputs
 * as necessary.  Must wait till killed uop finishes before we're ready again.
 * fdiv/fsqrt unit uses an unstable FIFO interface, and thus we must spend a
 * cycle buffering up an uop to provide slack between the issue queue and the
 * fdiv/fsqrt unit.  FDivUnit inherents directly from FunctionalUnit, because
 * UnpipelinedFunctionalUnit can only handle 1 inflight uop, whereas FDivUnit
 * contains up to 2 inflight uops due to the need to buffer the input as the
 * fdiv unit uses an unstable FIFO interface.
 * TODO extend UnpipelinedFunctionalUnit to handle a >1 uops inflight.
 *
 * @param isPipelined is the functional unit pipelined
 * @param numStages number of stages for the functional unit
 * @param numBypassStages number of bypass stages
 * @param dataWidth width of the data out of the functional unit
 */
class FDivSqrtUnit(implicit p: Parameters)
  extends FunctionalUnit(
    isPipelined = false,
    numStages = 1,
    numBypassStages = 0,
    dataWidth = 65,
    needsFcsr = true)
  with tile.HasFPUParameters
{
  //--------------------------------------
  // buffer inputs and upconvert as needed

  // provide a one-entry queue to store incoming uops while waiting for the fdiv/fsqrt unit to become available.
  val r_buffer_val = RegInit(false.B)
  val r_buffer_req = Reg(new FuncUnitReq(dataWidth=65))
  val r_buffer_fin = Reg(new tile.FPInput)

  val fdiv_decoder = Module(new UOPCodeFDivDecoder)
  fdiv_decoder.io.uopc := io.req.bits.uop.uopc

  // handle branch kill on queued entry
  r_buffer_val := !IsKilledByBranch(io.brupdate, r_buffer_req.uop) && !io.req.bits.kill && r_buffer_val
  r_buffer_req.uop.br_mask := GetNewBrMask(io.brupdate, r_buffer_req.uop)

  // handle incoming uop, including upconversion as needed, and push back if our input queue is already occupied
  io.req.ready := !r_buffer_val

  def upconvert(x: UInt) = {
    val s2d = Module(new hardfloat.RecFNToRecFN(inExpWidth = 8, inSigWidth = 24, outExpWidth = 11, outSigWidth = 53))
    s2d.io.in := x
    s2d.io.roundingMode := 0.U
    s2d.io.detectTininess := DontCare
    s2d.io.out
  }
  val in1_upconvert = upconvert(unbox(io.req.bits.rs1_data, false.B, Some(tile.FType.S)))
  val in2_upconvert = upconvert(unbox(io.req.bits.rs2_data, false.B, Some(tile.FType.S)))

  when (io.req.valid && !IsKilledByBranch(io.brupdate, io.req.bits.uop) && !io.req.bits.kill) {
    r_buffer_val := true.B
    r_buffer_req := io.req.bits
    r_buffer_req.uop.br_mask := GetNewBrMask(io.brupdate, io.req.bits.uop)
    r_buffer_fin.viewAsSupertype(new tile.FPUCtrlSigs) := fdiv_decoder.io.sigs

    r_buffer_fin.rm := Mux(ImmGenRm(io.req.bits.uop.imm_packed) === 7.U, io.fcsr_rm, ImmGenRm(io.req.bits.uop.imm_packed))
    r_buffer_fin.typ := 0.U // unused for fdivsqrt
    val tag = fdiv_decoder.io.sigs.typeTagIn
    r_buffer_fin.in1 := unbox(io.req.bits.rs1_data, tag, Some(tile.FType.D))
    r_buffer_fin.in2 := unbox(io.req.bits.rs2_data, tag, Some(tile.FType.D))
    when (tag === S) {
      r_buffer_fin.in1 := in1_upconvert
      r_buffer_fin.in2 := in2_upconvert
    }
  }

  assert (!(r_buffer_val && io.req.valid), "[fdiv] a request is incoming while the buffer is already full.")

  //-----------
  // fdiv/fsqrt

  val divsqrt = Module(new hardfloat.DivSqrtRecF64)

  val r_divsqrt_val = RegInit(false.B)  // inflight uop?
  val r_divsqrt_killed = Reg(Bool())           // has inflight uop been killed?
  val r_divsqrt_fin = Reg(new tile.FPInput)
  val r_divsqrt_uop = Reg(new MicroOp)

  // Need to buffer output until RF writeport is available.
  val output_buffer_available = Wire(Bool())

  val may_fire_input =
    r_buffer_val &&
    (r_buffer_fin.div || r_buffer_fin.sqrt) &&
    !r_divsqrt_val &&
    output_buffer_available

  val divsqrt_ready = Mux(divsqrt.io.sqrtOp, divsqrt.io.inReady_sqrt, divsqrt.io.inReady_div)
  divsqrt.io.inValid := may_fire_input // must be setup early
  divsqrt.io.sqrtOp := r_buffer_fin.sqrt
  divsqrt.io.a := r_buffer_fin.in1
  divsqrt.io.b := Mux(divsqrt.io.sqrtOp, r_buffer_fin.in1, r_buffer_fin.in2)
  divsqrt.io.roundingMode := r_buffer_fin.rm
  divsqrt.io.detectTininess := DontCare

  r_divsqrt_killed := r_divsqrt_killed || IsKilledByBranch(io.brupdate, r_divsqrt_uop) || io.req.bits.kill
  r_divsqrt_uop.br_mask := GetNewBrMask(io.brupdate, r_divsqrt_uop)

  when (may_fire_input && divsqrt_ready) {
    // Remove entry from the input buffer.
    // We don't have time to kill divsqrt request so must track if killed on entry.
    r_buffer_val := false.B
    r_divsqrt_val := true.B
    r_divsqrt_fin := r_buffer_fin
    r_divsqrt_uop := r_buffer_req.uop
    r_divsqrt_killed := IsKilledByBranch(io.brupdate, r_buffer_req.uop) || io.req.bits.kill
    r_divsqrt_uop.br_mask := GetNewBrMask(io.brupdate, r_buffer_req.uop)
  }

  //-----------------------------------------
  // buffer output and down-convert as needed

  val r_out_val = RegInit(false.B)
  val r_out_uop = Reg(new MicroOp)
  val r_out_flags_double = Reg(Bits())
  val r_out_wdata_double = Reg(Bits())

  output_buffer_available := !r_out_val

  r_out_uop.br_mask := GetNewBrMask(io.brupdate, r_out_uop)

  when (io.resp.ready || IsKilledByBranch(io.brupdate, r_out_uop) || io.req.bits.kill) {
    r_out_val := false.B
  }
  when (divsqrt.io.outValid_div || divsqrt.io.outValid_sqrt) {
    r_divsqrt_val := false.B

    r_out_val := !r_divsqrt_killed && !IsKilledByBranch(io.brupdate, r_divsqrt_uop) && !io.req.bits.kill
    r_out_uop := r_divsqrt_uop
    r_out_uop.br_mask := GetNewBrMask(io.brupdate, r_divsqrt_uop)
    r_out_wdata_double := sanitizeNaN(divsqrt.io.out, tile.FType.D)
    r_out_flags_double := divsqrt.io.exceptionFlags

    assert (r_divsqrt_val, "[fdiv] a response is being generated for no request.")
  }

  assert (!(r_out_val && (divsqrt.io.outValid_div || divsqrt.io.outValid_sqrt)),
    "[fdiv] Buffered output being overwritten by another output from the fdiv/fsqrt unit.")

  val downvert_d2s = Module(new hardfloat.RecFNToRecFN(
    inExpWidth = 11, inSigWidth = 53, outExpWidth = 8, outSigWidth = 24))
  downvert_d2s.io.in := r_out_wdata_double
  downvert_d2s.io.roundingMode := r_divsqrt_fin.rm
  downvert_d2s.io.detectTininess := DontCare
  val out_flags = r_out_flags_double | Mux(r_divsqrt_fin.typeTagIn === S, downvert_d2s.io.exceptionFlags, 0.U)

  io.resp.valid := r_out_val && !IsKilledByBranch(io.brupdate, r_out_uop)
  io.resp.bits.uop := r_out_uop
  io.resp.bits.data :=
    Mux(r_divsqrt_fin.typeTagIn === S,
      box(downvert_d2s.io.out, false.B),
      box(r_out_wdata_double, true.B))
  io.resp.bits.fflags.valid := io.resp.valid
  io.resp.bits.fflags.bits.uop := r_out_uop
  io.resp.bits.fflags.bits.uop.br_mask := GetNewBrMask(io.brupdate, r_out_uop)
  io.resp.bits.fflags.bits.flags := out_flags
}
