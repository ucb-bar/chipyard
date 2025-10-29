package shuttle.common
import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util._

class ShuttleUOP(implicit p: Parameters) extends CoreBundle {
  val nRAS = tileParams.btb.get.nRAS
  val inst = UInt(32.W)
  val raw_inst = UInt(32.W)
  val pc = UInt(vaddrBitsExtended.W)
  val edge_inst = Bool()
  val ctrl = new IntCtrlSigs
  val fp_ctrl = new FPUCtrlSigs
  val rvc = Bool()

  val sets_vcfg = Bool()

  val btb_resp = Valid(new BTBResp)
  val sfb_br = Bool()
  val sfb_shadow = Bool()
  val next_pc = Valid(UInt(vaddrBitsExtended.W))
  val ras_head = UInt(log2Ceil(nRAS).W)
  val taken = Bool()

  val xcpt = Bool()
  val xcpt_cause = UInt(64.W)

  val needs_replay = Bool()

  val rs1_data = UInt(64.W)
  val rs2_data = UInt(64.W)
  val rs3_data = UInt(64.W)
  val uses_memalu = Bool()
  val uses_latealu = Bool()

  val wdata = Valid(UInt(64.W))

  val RD_MSB  = 11
  val RD_LSB  = 7
  val RS1_MSB = 19
  val RS1_LSB = 15
  val RS2_MSB = 24
  val RS2_LSB = 20
  val RS3_MSB = 31
  val RS3_LSB = 27

  def rs1 = inst(RS1_MSB, RS1_LSB)
  def rs2 = inst(RS2_MSB, RS2_LSB)
  def rs3 = inst(RS3_MSB, RS3_LSB)
  def rd = inst(RD_MSB, RD_LSB)

  val fra1 = UInt(5.W)
  val fra2 = UInt(5.W)
  val fra3 = UInt(5.W)

  val fexc = UInt(FPConstants.FLAGS_SZ.W)

  val fdivin = new FPInput

  val mem_size = UInt(2.W)
  val flush_pipe = Bool()

  def csr_en = ctrl.csr.isOneOf(CSR.S, CSR.C, CSR.W)
  def csr_ren = ctrl.csr.isOneOf(CSR.S, CSR.C) && rs1 === 0.U
  def csr_wen = csr_en && !csr_ren
  def system_insn = ctrl.csr === CSR.I
  def sfence = ctrl.mem && ctrl.mem_cmd === M_SFENCE
  def wfi = inst === WFI
  def cfi = ctrl.branch || ctrl.jal || ctrl.jalr

  def uses_brjmp = cfi || sfence
  def uses_alu = ctrl.wxd && !ctrl.mem && !ctrl.div && !ctrl.mul && !csr_en && !ctrl.fp && !ctrl.rocc && !ctrl.jalr && !ctrl.vec
  def uses_fp = ctrl.fp && !(fp_ctrl.ldst && fp_ctrl.wen)
}
