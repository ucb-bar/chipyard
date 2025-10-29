package shuttle.common

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.Str
import freechips.rocketchip.rocket.RVCExpander

trait RISCVConstants
{
  // abstract out instruction decode magic numbers
  val RD_MSB  = 11
  val RD_LSB  = 7
  val RS1_MSB = 19
  val RS1_LSB = 15
  val RS2_MSB = 24
  val RS2_LSB = 20
  val RS3_MSB = 31
  val RS3_LSB = 27

  val CSR_ADDR_MSB = 31
  val CSR_ADDR_LSB = 20
  val CSR_ADDR_SZ = 12

  // location of the fifth bit in the shamt (for checking for illegal ops for SRAIW,etc.)
  val SHAMT_5_BIT = 25
  val LONGEST_IMM_SZ = 20
  val X0 = 0.U
  val RA = 1.U // return address register

  // memory consistency model
  // The C/C++ atomics MCM requires that two loads to the same address maintain program order.
  // The Cortex A9 does NOT enforce load/load ordering (which leads to buggy behavior).
  val MCM_ORDER_DEPENDENT_LOADS = true

  val jal_opc = (0x6f).U
  val jalr_opc = (0x67).U

  def ExpandRVC(inst: UInt)(implicit p: Parameters): UInt = {
    val rvc_exp = Module(new RVCExpander)
    rvc_exp.io.in := inst
    Mux(rvc_exp.io.rvc, rvc_exp.io.out.bits, inst)
  }

  // Note: Accepts only EXPANDED rvc instructions
  def ComputeBranchTarget(pc: UInt, inst: UInt, xlen: Int)(implicit p: Parameters): UInt = {
    val b_imm32 = Cat(Fill(20,inst(31)), inst(7), inst(30,25), inst(11,8), 0.U(1.W))
    ((pc.asSInt + b_imm32.asSInt).asSInt & (-2).S).asUInt
  }

  // Note: Accepts only EXPANDED rvc instructions
  def ComputeJALTarget(pc: UInt, inst: UInt, xlen: Int)(implicit p: Parameters): UInt = {
    val j_imm32 = Cat(Fill(12,inst(31)), inst(19,12), inst(20), inst(30,25), inst(24,21), 0.U(1.W))
    ((pc.asSInt + j_imm32.asSInt).asSInt & (-2).S).asUInt
  }

}
