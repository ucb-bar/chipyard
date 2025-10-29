//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package boom.v4.exu

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.FPConstants._
import freechips.rocketchip.tile.{FPUCtrlSigs, HasFPUParameters}
import freechips.rocketchip.tile
import freechips.rocketchip.rocket
import freechips.rocketchip.util.uintToBitPat
import boom.v4.common._

/**
 * Bundle representing data to be sent to the FPU
 */
class FpuReq()(implicit p: Parameters) extends BoomBundle
{
  val uop      = new MicroOp()
  val rs1_data = Bits(65.W)
  val rs2_data = Bits(65.W)
  val rs3_data = Bits(65.W)
  val fcsr_rm  = Bits(tile.FPConstants.RM_SZ.W)
}

/**
 * FPU unit that wraps the RocketChip FPU units (which in turn wrap hardfloat)
 */
class FPU(implicit p: Parameters) extends BoomModule with tile.HasFPUParameters
{
  val io = IO(new Bundle {
    val req = Flipped(new ValidIO(new FpuReq))
    val resp = new ValidIO(new ExeUnitResp(65))
  })

  // all FP units are padded out to the same latency for easy scheduling of the write port
  val fpu_latency = dfmaLatency
  val io_req = io.req.bits

  val fp_ctrl = io.req.bits.uop.fp_ctrl
  val fp_rm = io_req.uop.fp_rm

  def fuInput(minT: Option[tile.FType]): tile.FPInput = {
    val req = Wire(new tile.FPInput)
    val tag = fp_ctrl.typeTagIn
    req.viewAsSupertype(new tile.FPUCtrlSigs) <> fp_ctrl
    req.rm := fp_rm
    req.in1 := unbox(io_req.rs1_data, tag, minT)
    req.in2 := unbox(io_req.rs2_data, tag, minT)
    req.in3 := unbox(io_req.rs3_data, tag, minT)
    when (fp_ctrl.swap23) { req.in3 := req.in2 }
    req.typ := io_req.uop.fp_typ
    req.fmt := Mux(tag === S, 0.U, 1.U) // TODO support Zfh and avoid special-case below
    when (fp_ctrl.toint && fp_ctrl.typeTagOut === S && !fp_ctrl.wflags) { // fmv_x_w
      req.fmt := 0.U
    }

    req.fmaCmd := io_req.uop.fcn_op
    req
  }

  val dfma = Module(new tile.FPUFMAPipe(latency = fpu_latency, t = tile.FType.D))
  dfma.io.in.valid := io.req.valid && fp_ctrl.fma && (fp_ctrl.typeTagOut === D)
  dfma.io.in.bits := fuInput(Some(dfma.t))

  val sfma = Module(new tile.FPUFMAPipe(latency = fpu_latency, t = tile.FType.S))
  sfma.io.in.valid := io.req.valid && fp_ctrl.fma && (fp_ctrl.typeTagOut === S)
  sfma.io.in.bits := fuInput(Some(sfma.t))

  val fpiu = Module(new tile.FPToInt)
  fpiu.io.in.valid := io.req.valid && (fp_ctrl.toint || (fp_ctrl.fastpipe && fp_ctrl.wflags))
  fpiu.io.in.bits := fuInput(None)
  val fpiu_out = Pipe(RegNext(fpiu.io.in.valid && !fp_ctrl.fastpipe),
                              fpiu.io.out.bits, fpu_latency-1)
  val fpiu_result  = Wire(new tile.FPResult)
  fpiu_result.data := fpiu_out.bits.toint
  fpiu_result.exc  := fpiu_out.bits.exc

  val fpmu = Module(new tile.FPToFP(fpu_latency)) // latency 2 for rocket
  fpmu.io.in.valid := io.req.valid && fp_ctrl.fastpipe
  fpmu.io.in.bits := fpiu.io.in.bits
  fpmu.io.lt := fpiu.io.out.bits.lt
  val fpmu_double = Pipe(io.req.valid && fp_ctrl.fastpipe, fp_ctrl.typeTagOut === D, fpu_latency).bits

  // Response (all FP units have been padded out to the same latency)
  io.resp.valid := fpiu_out.valid ||
                   fpmu.io.out.valid ||
                   sfma.io.out.valid ||
                   dfma.io.out.valid
  val fpu_out_data =
    Mux(dfma.io.out.valid, box(dfma.io.out.bits.data, true.B),
    Mux(sfma.io.out.valid, box(sfma.io.out.bits.data, false.B),
    Mux(fpiu_out.valid,    fpiu_result.data,
                           box(fpmu.io.out.bits.data, fpmu_double))))

  val fpu_out_exc =
    Mux(dfma.io.out.valid, dfma.io.out.bits.exc,
    Mux(sfma.io.out.valid, sfma.io.out.bits.exc,
    Mux(fpiu_out.valid,    fpiu_result.exc,
                           fpmu.io.out.bits.exc)))

  io.resp.bits.uop          := DontCare
  io.resp.bits.predicated   := DontCare
  io.resp.bits.data         := fpu_out_data
  io.resp.bits.fflags.valid := io.resp.valid
  io.resp.bits.fflags.bits  := fpu_out_exc
}
