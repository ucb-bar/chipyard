package shuttle.exu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import freechips.rocketchip.tile._

import shuttle.common._

class ShuttleFPPipe(implicit p: Parameters) extends FPUModule()(p) {
  val latency = tileParams.core.fpu.get.dfmaLatency
  val io = IO(new Bundle {
    val in = Input(Valid(new ShuttleUOP))
    val frs1_data = Input(UInt(65.W))
    val frs2_data = Input(UInt(65.W))
    val frs3_data = Input(UInt(65.W))
    val fcsr_rm = Input(UInt(3.W))
    val s1_kill = Input(Bool())
    val s1_store_data = Output(UInt(64.W))
    val s1_fpiu_toint = Output(UInt(64.W))
    val s1_fpiu_fexc = Output(UInt())
    val s1_fpiu_fdiv = Output(new FPInput)
    val s2_kill = Input(Bool())
    val out = Valid(new FPResult)
    val out_rd = Output(UInt(5.W))
    val out_tag = Output(UInt(2.W))
  })

  def fuInput(minT: Option[FType], ctrl: FPUCtrlSigs, rm: UInt, inst: UInt): FPInput = {
    val req = Wire(new FPInput)
    val tag = ctrl.typeTagIn
    req.ldst := ctrl.ldst
    req.wen := ctrl.wen
    req.ren1 := ctrl.ren1
    req.ren2 := ctrl.ren2
    req.ren3 := ctrl.ren3
    req.swap12 := ctrl.swap12
    req.swap23 := ctrl.swap23
    req.typeTagIn := ctrl.typeTagIn
    req.typeTagOut := ctrl.typeTagOut
    req.fromint := ctrl.fromint
    req.toint := ctrl.toint
    req.fastpipe := ctrl.fastpipe
    req.fma := ctrl.fma
    req.div := ctrl.div
    req.sqrt := ctrl.sqrt
    req.wflags := ctrl.wflags
    req.vec := ctrl.vec
    req.rm := rm
    req.in1 := unbox(io.frs1_data, tag, minT)
    req.in2 := unbox(io.frs2_data, tag, minT)
    req.in3 := unbox(io.frs3_data, tag, minT)
    req.typ := inst(21,20)
    req.fmt := inst(26,25)
    req.fmaCmd := inst(3,2) | (!ctrl.ren3 && inst(27))
    req.vec := false.B
    req
  }



  val inst = io.in.bits.inst
  val fp_ctrl = io.in.bits.fp_ctrl
  val rm = Mux(inst(14,12) === 7.U, io.fcsr_rm, inst(14,12))

  val fpiu = Module(new FPToInt)
  fpiu.io.in.valid := io.in.valid && (fp_ctrl.toint || fp_ctrl.div || fp_ctrl.sqrt || (fp_ctrl.fastpipe && fp_ctrl.wflags))
  fpiu.io.in.bits := fuInput(None, fp_ctrl, rm, inst)
  io.s1_store_data := fpiu.io.out.bits.store
  io.s1_fpiu_toint := fpiu.io.out.bits.toint
  io.s1_fpiu_fexc := fpiu.io.out.bits.exc
  io.s1_fpiu_fdiv := fpiu.io.out.bits.in

  val dfma = Module(new FPUFMAPipe(latency, FType.D))
  val sfma = Module(new FPUFMAPipe(latency, FType.S))
  val hfma = Module(new FPUFMAPipe(latency, FType.H))

  Seq(dfma, sfma, hfma).foreach { fma =>
    fma.io.in.valid := io.in.valid && fp_ctrl.typeTagOut === typeTagGroup(fma.t) && fp_ctrl.fma
    fma.io.in.bits := fuInput(Some(fma.t), fp_ctrl, rm, inst)
  }

  val ifpu = Module(new IntToFP(latency))
  ifpu.io.in.valid := io.in.valid && fp_ctrl.fromint
  ifpu.io.in.bits := fuInput(None, fp_ctrl, rm, inst)
  ifpu.io.in.bits.in1 := io.in.bits.rs1_data

  val fpmu = Module(new FPToFP(latency))
  fpmu.io.lt := fpiu.io.out.bits.lt
  fpmu.io.in.valid := io.in.valid && fp_ctrl.fastpipe
  fpmu.io.in.bits := fuInput(None, fp_ctrl, rm, inst)


  io.out.valid := (ShiftRegister(io.in.valid && !(fp_ctrl.toint || fp_ctrl.div || fp_ctrl.sqrt), latency)
    && !ShiftRegister(io.s1_kill, latency-1)
    && !ShiftRegister(io.s2_kill, latency-2)
  )
  io.out.bits := Mux1H(Seq(dfma.io.out, sfma.io.out, hfma.io.out, ifpu.io.out, fpmu.io.out).map { o =>
    o.valid -> o.bits
  })
  io.out_rd := ShiftRegister(io.in.bits.rd, latency)
  io.out_tag := ShiftRegister(fp_ctrl.typeTagOut, latency)

}
