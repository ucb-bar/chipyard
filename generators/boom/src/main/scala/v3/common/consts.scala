//******************************************************************************
// Copyright (c) 2011 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISCV Processor Constants
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.common.constants

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.Str
import freechips.rocketchip.rocket.RVCExpander

/**
 * Mixin for issue queue types
 */
trait IQType
{
  val IQT_SZ  = 3
  val IQT_INT = 1.U(IQT_SZ.W)
  val IQT_MEM = 2.U(IQT_SZ.W)
  val IQT_FP  = 4.U(IQT_SZ.W)

  val IQT_MFP = 6.U(IQT_SZ.W)
}


/**
 * Mixin for scalar operation constants
 */
trait ScalarOpConstants
{
  val X = BitPat("b?")
  val Y = BitPat("b1")
  val N = BitPat("b0")

  //************************************
  // Extra Constants

  // Which branch predictor predicted us
  val BSRC_SZ = 2
  val BSRC_1 = 0.U(BSRC_SZ.W) // 1-cycle branch pred
  val BSRC_2 = 1.U(BSRC_SZ.W) // 2-cycle branch pred
  val BSRC_3 = 2.U(BSRC_SZ.W) // 3-cycle branch pred
  val BSRC_C = 3.U(BSRC_SZ.W) // core branch resolution

  //************************************
  // Control Signals

  // CFI types
  val CFI_SZ   = 3
  val CFI_X    = 0.U(CFI_SZ.W) // Not a CFI instruction
  val CFI_BR   = 1.U(CFI_SZ.W) // Branch
  val CFI_JAL  = 2.U(CFI_SZ.W) // JAL
  val CFI_JALR = 3.U(CFI_SZ.W) // JALR

  // PC Select Signal
  val PC_PLUS4 = 0.U(2.W)  // PC + 4
  val PC_BRJMP = 1.U(2.W)  // brjmp_target
  val PC_JALR  = 2.U(2.W)  // jump_reg_target

  // Branch Type
  val BR_N   = 0.U(4.W)  // Next
  val BR_NE  = 1.U(4.W)  // Branch on NotEqual
  val BR_EQ  = 2.U(4.W)  // Branch on Equal
  val BR_GE  = 3.U(4.W)  // Branch on Greater/Equal
  val BR_GEU = 4.U(4.W)  // Branch on Greater/Equal Unsigned
  val BR_LT  = 5.U(4.W)  // Branch on Less Than
  val BR_LTU = 6.U(4.W)  // Branch on Less Than Unsigned
  val BR_J   = 7.U(4.W)  // Jump
  val BR_JR  = 8.U(4.W)  // Jump Register

  // RS1 Operand Select Signal
  val OP1_RS1 = 0.U(2.W) // Register Source #1
  val OP1_ZERO= 1.U(2.W)
  val OP1_PC  = 2.U(2.W)
  val OP1_X   = BitPat("b??")

  // RS2 Operand Select Signal
  val OP2_RS2 = 0.U(3.W) // Register Source #2
  val OP2_IMM = 1.U(3.W) // immediate
  val OP2_ZERO= 2.U(3.W) // constant 0
  val OP2_NEXT= 3.U(3.W) // constant 2/4 (for PC+2/4)
  val OP2_IMMC= 4.U(3.W) // for CSR imm found in RS1
  val OP2_X   = BitPat("b???")

  // Register File Write Enable Signal
  val REN_0   = false.B
  val REN_1   = true.B

  // Is 32b Word or 64b Doubldword?
  val SZ_DW = 1
  val DW_X   = true.B // Bool(xLen==64)
  val DW_32  = false.B
  val DW_64  = true.B
  val DW_XPR = true.B // Bool(xLen==64)

  // Memory Enable Signal
  val MEN_0   = false.B
  val MEN_1   = true.B
  val MEN_X   = false.B

  // Immediate Extend Select
  val IS_I   = 0.U(3.W)  // I-Type  (LD,ALU)
  val IS_S   = 1.U(3.W)  // S-Type  (ST)
  val IS_B   = 2.U(3.W)  // SB-Type (BR)
  val IS_U   = 3.U(3.W)  // U-Type  (LUI/AUIPC)
  val IS_J   = 4.U(3.W)  // UJ-Type (J/JAL)
  val IS_X   = BitPat("b???")

  // Decode Stage Control Signals
  val RT_FIX   = 0.U(2.W)
  val RT_FLT   = 1.U(2.W)
  val RT_PAS   = 3.U(2.W) // pass-through (prs1 := lrs1, etc)
  val RT_X     = 2.U(2.W) // not-a-register (but shouldn't get a busy-bit, etc.)
                             // TODO rename RT_NAR

  // Micro-op opcodes
  // TODO change micro-op opcodes into using enum
  val UOPC_SZ = 7
  val uopX    = BitPat.dontCare(UOPC_SZ)
  val uopNOP  =  0.U(UOPC_SZ.W)
  val uopLD   =  1.U(UOPC_SZ.W)
  val uopSTA  =  2.U(UOPC_SZ.W)  // store address generation
  val uopSTD  =  3.U(UOPC_SZ.W)  // store data generation
  val uopLUI  =  4.U(UOPC_SZ.W)

  val uopADDI =  5.U(UOPC_SZ.W)
  val uopANDI =  6.U(UOPC_SZ.W)
  val uopORI  =  7.U(UOPC_SZ.W)
  val uopXORI =  8.U(UOPC_SZ.W)
  val uopSLTI =  9.U(UOPC_SZ.W)
  val uopSLTIU= 10.U(UOPC_SZ.W)
  val uopSLLI = 11.U(UOPC_SZ.W)
  val uopSRAI = 12.U(UOPC_SZ.W)
  val uopSRLI = 13.U(UOPC_SZ.W)

  val uopSLL  = 14.U(UOPC_SZ.W)
  val uopADD  = 15.U(UOPC_SZ.W)
  val uopSUB  = 16.U(UOPC_SZ.W)
  val uopSLT  = 17.U(UOPC_SZ.W)
  val uopSLTU = 18.U(UOPC_SZ.W)
  val uopAND  = 19.U(UOPC_SZ.W)
  val uopOR   = 20.U(UOPC_SZ.W)
  val uopXOR  = 21.U(UOPC_SZ.W)
  val uopSRA  = 22.U(UOPC_SZ.W)
  val uopSRL  = 23.U(UOPC_SZ.W)

  val uopBEQ  = 24.U(UOPC_SZ.W)
  val uopBNE  = 25.U(UOPC_SZ.W)
  val uopBGE  = 26.U(UOPC_SZ.W)
  val uopBGEU = 27.U(UOPC_SZ.W)
  val uopBLT  = 28.U(UOPC_SZ.W)
  val uopBLTU = 29.U(UOPC_SZ.W)
  val uopCSRRW= 30.U(UOPC_SZ.W)
  val uopCSRRS= 31.U(UOPC_SZ.W)
  val uopCSRRC= 32.U(UOPC_SZ.W)
  val uopCSRRWI=33.U(UOPC_SZ.W)
  val uopCSRRSI=34.U(UOPC_SZ.W)
  val uopCSRRCI=35.U(UOPC_SZ.W)

  val uopJ    = 36.U(UOPC_SZ.W)
  val uopJAL  = 37.U(UOPC_SZ.W)
  val uopJALR = 38.U(UOPC_SZ.W)
  val uopAUIPC= 39.U(UOPC_SZ.W)

//val uopSRET = 40.U(UOPC_SZ.W)
  val uopCFLSH= 41.U(UOPC_SZ.W)
  val uopFENCE= 42.U(UOPC_SZ.W)

  val uopADDIW= 43.U(UOPC_SZ.W)
  val uopADDW = 44.U(UOPC_SZ.W)
  val uopSUBW = 45.U(UOPC_SZ.W)
  val uopSLLIW= 46.U(UOPC_SZ.W)
  val uopSLLW = 47.U(UOPC_SZ.W)
  val uopSRAIW= 48.U(UOPC_SZ.W)
  val uopSRAW = 49.U(UOPC_SZ.W)
  val uopSRLIW= 50.U(UOPC_SZ.W)
  val uopSRLW = 51.U(UOPC_SZ.W)
  val uopMUL  = 52.U(UOPC_SZ.W)
  val uopMULH = 53.U(UOPC_SZ.W)
  val uopMULHU= 54.U(UOPC_SZ.W)
  val uopMULHSU=55.U(UOPC_SZ.W)
  val uopMULW = 56.U(UOPC_SZ.W)
  val uopDIV  = 57.U(UOPC_SZ.W)
  val uopDIVU = 58.U(UOPC_SZ.W)
  val uopREM  = 59.U(UOPC_SZ.W)
  val uopREMU = 60.U(UOPC_SZ.W)
  val uopDIVW = 61.U(UOPC_SZ.W)
  val uopDIVUW= 62.U(UOPC_SZ.W)
  val uopREMW = 63.U(UOPC_SZ.W)
  val uopREMUW= 64.U(UOPC_SZ.W)

  val uopFENCEI    =  65.U(UOPC_SZ.W)
  //               =  66.U(UOPC_SZ.W)
  val uopAMO_AG    =  67.U(UOPC_SZ.W) // AMO-address gen (use normal STD for datagen)

  val uopFMV_W_X   =  68.U(UOPC_SZ.W)
  val uopFMV_D_X   =  69.U(UOPC_SZ.W)
  val uopFMV_X_W   =  70.U(UOPC_SZ.W)
  val uopFMV_X_D   =  71.U(UOPC_SZ.W)

  val uopFSGNJ_S   =  72.U(UOPC_SZ.W)
  val uopFSGNJ_D   =  73.U(UOPC_SZ.W)

  val uopFCVT_S_D  =  74.U(UOPC_SZ.W)
  val uopFCVT_D_S  =  75.U(UOPC_SZ.W)

  val uopFCVT_S_X  =  76.U(UOPC_SZ.W)
  val uopFCVT_D_X  =  77.U(UOPC_SZ.W)

  val uopFCVT_X_S  =  78.U(UOPC_SZ.W)
  val uopFCVT_X_D  =  79.U(UOPC_SZ.W)

  val uopCMPR_S    =  80.U(UOPC_SZ.W)
  val uopCMPR_D    =  81.U(UOPC_SZ.W)

  val uopFCLASS_S  =  82.U(UOPC_SZ.W)
  val uopFCLASS_D  =  83.U(UOPC_SZ.W)

  val uopFMINMAX_S =  84.U(UOPC_SZ.W)
  val uopFMINMAX_D =  85.U(UOPC_SZ.W)

  //               =  86.U(UOPC_SZ.W)
  val uopFADD_S    =  87.U(UOPC_SZ.W)
  val uopFSUB_S    =  88.U(UOPC_SZ.W)
  val uopFMUL_S    =  89.U(UOPC_SZ.W)
  val uopFADD_D    =  90.U(UOPC_SZ.W)
  val uopFSUB_D    =  91.U(UOPC_SZ.W)
  val uopFMUL_D    =  92.U(UOPC_SZ.W)

  val uopFMADD_S   =  93.U(UOPC_SZ.W)
  val uopFMSUB_S   =  94.U(UOPC_SZ.W)
  val uopFNMADD_S  =  95.U(UOPC_SZ.W)
  val uopFNMSUB_S  =  96.U(UOPC_SZ.W)
  val uopFMADD_D   =  97.U(UOPC_SZ.W)
  val uopFMSUB_D   =  98.U(UOPC_SZ.W)
  val uopFNMADD_D  =  99.U(UOPC_SZ.W)
  val uopFNMSUB_D  = 100.U(UOPC_SZ.W)

  val uopFDIV_S    = 101.U(UOPC_SZ.W)
  val uopFDIV_D    = 102.U(UOPC_SZ.W)
  val uopFSQRT_S   = 103.U(UOPC_SZ.W)
  val uopFSQRT_D   = 104.U(UOPC_SZ.W)

  val uopWFI       = 105.U(UOPC_SZ.W) // pass uop down the CSR pipeline
  val uopERET      = 106.U(UOPC_SZ.W) // pass uop down the CSR pipeline, also is ERET
  val uopSFENCE    = 107.U(UOPC_SZ.W)

  val uopROCC      = 108.U(UOPC_SZ.W)

  val uopMOV       = 109.U(UOPC_SZ.W) // conditional mov decoded from "add rd, x0, rs2"

  // The Bubble Instruction (Machine generated NOP)
  // Insert (XOR x0,x0,x0) which is different from software compiler
  // generated NOPs which are (ADDI x0, x0, 0).
  // Reasoning for this is to let visualizers and stat-trackers differentiate
  // between software NOPs and machine-generated Bubbles in the pipeline.
  val BUBBLE  = (0x4033).U(32.W)

  def NullMicroOp()(implicit p: Parameters): boom.v3.common.MicroOp = {
    val uop = Wire(new boom.v3.common.MicroOp)
    uop            := DontCare // Overridden in the following lines
    uop.uopc       := uopNOP // maybe not required, but helps on asserts that try to catch spurious behavior
    uop.bypassable := false.B
    uop.fp_val     := false.B
    uop.uses_stq   := false.B
    uop.uses_ldq   := false.B
    uop.pdst       := 0.U
    uop.dst_rtype  := RT_X

    val cs = Wire(new boom.v3.common.CtrlSignals())
    cs             := DontCare // Overridden in the following lines
    cs.br_type     := BR_N
    cs.csr_cmd     := freechips.rocketchip.rocket.CSR.N
    cs.is_load     := false.B
    cs.is_sta      := false.B
    cs.is_std      := false.B

    uop.ctrl := cs
    uop
  }
}

/**
 * Mixin for RISCV constants
 */
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

  def GetUop(inst: UInt): UInt = inst(6,0)
  def GetRd (inst: UInt): UInt = inst(RD_MSB,RD_LSB)
  def GetRs1(inst: UInt): UInt = inst(RS1_MSB,RS1_LSB)

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

  // Note: Accepts only EXPANDED rvc instructions
  def GetCfiType(inst: UInt)(implicit p: Parameters): UInt = {
    val bdecode = Module(new boom.v3.exu.BranchDecode)
    bdecode.io.inst := inst
    bdecode.io.pc := 0.U
    bdecode.io.out.cfi_type
  }
}

/**
 * Mixin for exception cause constants
 */
trait ExcCauseConstants
{
  // a memory disambigious misspeculation occurred
  val MINI_EXCEPTION_MEM_ORDERING = 16.U
  val MINI_EXCEPTION_CSR_REPLAY = 17.U

  require (!freechips.rocketchip.rocket.Causes.all.contains(16))
  require (!freechips.rocketchip.rocket.Causes.all.contains(17))
}
