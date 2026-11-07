//******************************************************************************
// Copyright (c) 2015 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package boom.v4.exu

import chisel3._
import chisel3.util._

import freechips.rocketchip.tile.FPConstants
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.rocket.Instructions32
import freechips.rocketchip.rocket.CustomInstructions._
import freechips.rocketchip.rocket.RVCExpander
import freechips.rocketchip.rocket.ALU._
import freechips.rocketchip.rocket.{CSR, Causes, DecodeLogic}
import freechips.rocketchip.util._

import boom.v4.common._
import boom.v4.util._

// scalastyle:off
/**
 * Abstract trait giving defaults and other relevant values to different Decode constants/
 */
object DecodeTables
  extends freechips.rocketchip.rocket.constants.ScalarOpConstants
  with freechips.rocketchip.rocket.constants.MemoryOpConstants
  with freechips.rocketchip.tile.HasFPUParameters
{
  lazy val fLen = 64
  lazy val minFLen = 32
  def xLen = 64
  def xpr64 = Y // TODO inform this from xLen
  def DC(i: Int) = BitPat.dontCare(i)

  def fc2oh(fc: Int): UInt = (1 << fc).U(FC_SZ.W)
  // FP stores generate data through FP F2I, and generate address through MemAddrCalc
  def FCOH_F2IMEM = ((1 << FC_AGEN) | (1 << FC_F2I )).U(FC_SZ.W)
  def FCOH_STORE  = ((1 << FC_AGEN) | (1 << FC_DGEN)).U(FC_SZ.W)

  def FN_00 = BitPat("b???00")
  def FN_01 = BitPat("b???01")
  def FN_10 = BitPat("b???10")
  def FN_11 = BitPat("b???11")

  def decode_default: List[BitPat] =
              //                                                             frs3_en
              //               is val inst?                                  |  imm sel
              //               |  is fp inst?                                |  |     uses_ldq
              //               |  |                          rs1 regtype     |  |     |  uses_stq        is unique? (clear pipeline for it)
              //               |  |                          |       rs2 type|  |     |  |  is_amo       |  flush on commit
              //               |  |  func unit               |       |       |  |     |  |  |            |  |  csr cmd
              //               |  |  |                       |       |       |  |     |  |  |            |  |  |      fcn_dw                      swap12         fma
              //               |  |  |               dst     |       |       |  |     |  |  |  mem       |  |  |      |       fcn_op              | swap32       | div
              //               |  |  |               regtype |       |       |  |     |  |  |  cmd       |  |  |      |       |                   | | typeTagIn  | | sqrt
              //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        ldst       | | | typeTagOut | | wflags
              //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | wen      | | | | from_int | | |
              //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | ren1   | | | | | to_int | | |
              //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | | ren2 | | | | | | fast | | |
              //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | | | ren3 | | | | | |  | | | |
              //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | | | |  | | | | | | |  | | | |
                          List(N, N, DC(FC_SZ)     , RT_X  , DC(2) , DC(2) , X, IS_N, X, X, X, M_X,      N, X, CSR.X, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)

  def X32_table: Seq[(BitPat, List[BitPat])] = { import Instructions32._; Seq(
    SLLI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SL  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRLI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRAI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SRA , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)
  ) }
  def X64_table: Seq[(BitPat, List[BitPat])] = Seq(
    LD                 -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    LWU                -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SD                 -> List(Y, N, FCOH_STORE    , RT_X  , RT_FIX, RT_FIX, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    SLLI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SL  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRLI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRAI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SRA , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    ADDIW              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLLIW              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SL  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRAIW              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SRA , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRLIW              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    ADDW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SUBW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SUB , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLLW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SL  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRAW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SRA , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRLW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)
  )
  def X_table: Seq[(BitPat, List[BitPat])] = Seq(
    LW                 -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    LH                 -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    LHU                -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    LB                 -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    LBU                -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    SW                 -> List(Y, N, FCOH_STORE    , RT_X  , RT_FIX, RT_FIX, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SH                 -> List(Y, N, FCOH_STORE    , RT_X  , RT_FIX, RT_FIX, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SB                 -> List(Y, N, FCOH_STORE    , RT_X  , RT_FIX, RT_FIX, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    LUI                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_X  , RT_X  , N, IS_U, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    ADDI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ANDI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_AND , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ORI                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_OR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    XORI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_XOR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLTI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLT , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLTIU              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLTU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    SLL                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SL  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ADD                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SUB                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SUB , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLT                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLT , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLTU               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLTU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AND                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_AND , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    OR                 -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_OR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    XOR                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_XOR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRA                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SRA , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRL                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    MUL                -> List(Y, N, fc2oh(FC_MUL) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MUL , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MULH               -> List(Y, N, fc2oh(FC_MUL) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MULH, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MULHU              -> List(Y, N, fc2oh(FC_MUL) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MULHU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MULHSU             -> List(Y, N, fc2oh(FC_MUL) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MULHSU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MULW               -> List(Y, N, fc2oh(FC_MUL) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_MUL , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    DIV                -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_DIV , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    DIVU               -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_DIVU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    REM                -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_REM , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    REMU               -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_REMU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    DIVW               -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_DIV , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    DIVUW              -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_DIVU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    REMW               -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_REM , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    REMUW              -> List(Y, N, fc2oh(FC_DIV) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_REMU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    AUIPC              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_X  , RT_X  , N, IS_U, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X), // use BRU for the PC read
    JAL                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_X  , RT_X  , N, IS_J, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    JALR               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BEQ                -> List(Y, N, fc2oh(FC_ALU) , RT_X  , RT_FIX, RT_FIX, N, IS_B, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SUB , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BNE                -> List(Y, N, fc2oh(FC_ALU) , RT_X  , RT_FIX, RT_FIX, N, IS_B, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SUB , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BGE                -> List(Y, N, fc2oh(FC_ALU) , RT_X  , RT_FIX, RT_FIX, N, IS_B, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLT , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BGEU               -> List(Y, N, fc2oh(FC_ALU) , RT_X  , RT_FIX, RT_FIX, N, IS_B, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLTU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BLT                -> List(Y, N, fc2oh(FC_ALU) , RT_X  , RT_FIX, RT_FIX, N, IS_B, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLT , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BLTU               -> List(Y, N, fc2oh(FC_ALU) , RT_X  , RT_FIX, RT_FIX, N, IS_B, N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_SLTU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    // I-type, the immedia2 holds the CSR regi ster.
    CSRRW              -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.W, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CSRRS              -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.S, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CSRRC              -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.C, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    CSRRWI             -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.W, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CSRRSI             -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.S, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CSRRCI             -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.C, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    SFENCE_VMA          ->List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_FIX, N, IS_N, N, N, N,M_SFENCE , Y, Y, CSR.R, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ECALL              -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    EBREAK             -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SRET               -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MRET               -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    DRET               -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    WFI                -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    FENCE_I            -> List(Y, N, 0.U(FC_SZ.W)  , RT_X  , RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , Y, Y, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    FENCE              -> List(Y, N, 0.U(FC_SZ.W)  , RT_X  , RT_X  , RT_X  , N, IS_N, N, Y, N, M_X     , Y, Y, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X), // TODO PERF make fence higher performance
                                                                                                                             // currently serializes pipeline
    // A-type
    AMOADD_W           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_ADD, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X), // TODO make AMOs higherperformance
    AMOXOR_W           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_XOR, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOSWAP_W          -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_SWAP,Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOAND_W           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_AND, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOOR_W            -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_OR,  Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMIN_W           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MIN, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMINU_W          -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MINU,Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMAX_W           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MAX, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMAXU_W          -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MAXU,Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    AMOADD_D           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_ADD, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOXOR_D           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_XOR, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOSWAP_D          -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_SWAP,Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOAND_D           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_AND, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOOR_D            -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_OR,  Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMIN_D           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MIN, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMINU_D          -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MINU,Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMAX_D           -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MAX, Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    AMOMAXU_D          -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XA_MAXU,Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    LR_W               -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_N, Y, N, N, M_XLR   , Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    LR_D               -> List(Y, N, fc2oh(FC_AGEN), RT_FIX, RT_FIX, RT_X  , N, IS_N, Y, N, N, M_XLR   , Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SC_W               -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XSC   , Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SC_D               -> List(Y, N, FCOH_STORE    , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, Y, Y, M_XSC   , Y, Y, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)
  )
  def F_table: Seq[(BitPat, List[BitPat])] = Seq(
    FLW                -> List(Y, Y, fc2oh(FC_AGEN), RT_FLT, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    FLD                -> List(Y, Y, fc2oh(FC_AGEN), RT_FLT, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    FSW                -> List(Y, Y, FCOH_F2IMEM   , RT_X  , RT_FIX, RT_FLT, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X), // sort of a lie; broken into two micro-ops
    FSD                -> List(Y, Y, FCOH_F2IMEM   , RT_X  , RT_FIX, RT_FLT, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    FCLASS_S           -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,N),
    FCLASS_D           -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,N),

    FMV_W_X            -> List(Y, Y, fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,D,Y,N,N, N,N,N,N),
    FMV_D_X            -> List(Y, Y, fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,N),
    FMV_X_W            -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,S,N,Y,N, N,N,N,N),
    FMV_X_D            -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,N),

    FSGNJ_S            -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,N),
    FSGNJ_D            -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,N),
    FSGNJX_S           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,N),
    FSGNJX_D           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,N),
    FSGNJN_S           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,N),
    FSGNJN_D           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,N),

    // FP to FP
    FCVT_S_D           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,S,N,N,Y, N,N,N,Y),
    FCVT_D_S           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,D,N,N,Y, N,N,N,Y),

    // Int to FP
    FCVT_S_W           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
    FCVT_S_WU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
    FCVT_S_L           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
    FCVT_S_LU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),

    FCVT_D_W           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
    FCVT_D_WU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
    FCVT_D_L           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
    FCVT_D_LU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),

    // FP to Int
    FCVT_W_S           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
    FCVT_WU_S          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
    FCVT_L_S           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
    FCVT_LU_S          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),

    FCVT_W_D           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
    FCVT_WU_D          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
    FCVT_L_D           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
    FCVT_LU_D          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),


    FEQ_S              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,Y,N, N,N,N,Y),
    FLT_S              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,Y,N, N,N,N,Y),
    FLE_S              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,Y,N, N,N,N,Y),

    FEQ_D              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,Y,N, N,N,N,Y),
    FLT_D              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,Y,N, N,N,N,Y),
    FLE_D              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,Y,N, N,N,N,Y),

    FMIN_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,Y),
    FMAX_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,Y),
    FMIN_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,Y),
    FMAX_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,Y),

    FADD_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,Y,S,S,N,N,N, Y,N,N,Y),
    FSUB_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,N, N,Y,S,S,N,N,N, Y,N,N,Y),
    FMUL_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,N,S,S,N,N,N, Y,N,N,Y),
    FADD_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,Y,D,D,N,N,N, Y,N,N,Y),
    FSUB_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,N, N,Y,D,D,N,N,N, Y,N,N,Y),
    FMUL_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,N,D,D,N,N,N, Y,N,N,Y),

    FMADD_S            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
    FMSUB_S            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
    FNMADD_S           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_11  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
    FNMSUB_S           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_10  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
    FMADD_D            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y),
    FMSUB_D            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y),
    FNMADD_D           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_11  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y),
    FNMSUB_D           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_10  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y)
  )
  def FDivSqrt_table: Seq[(BitPat, List[BitPat])] = Seq(
    FDIV_S             -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,X, X,X,S,S,X,X,X, X,Y,N,Y),
    FDIV_D             -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,X, X,X,D,D,X,X,X, X,Y,N,Y),
    FSQRT_S            -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,X, X,X,S,S,X,X,X, X,N,Y,Y),
    FSQRT_D            -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,X, X,X,D,D,X,X,X, X,N,Y,Y),
  )
  def B_table: Seq[(BitPat, List[BitPat])] = Seq(
    SH1ADD             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SH2ADD             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SH3ADD             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SH1ADD_UW          -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SH2ADD_UW          -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SH3ADD_UW          -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ADD_UW             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_F3,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SLLI_UW            -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_SL  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    ANDN               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ANDN, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ORN                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ORN , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    XNOR               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_XNOR, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MAX                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MAX , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MAXU               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MAXU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MIN                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MIN , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    MINU               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_MINU, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ROL                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ROL , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ROR                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ROR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    RORI               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ROR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    CLZ                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CTZ                -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CPOP               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ORC_B              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SEXT_B             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    SEXT_H             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    ZEXT_H             -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    REV8               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    ROLW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ROL , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    RORW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ROR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    RORIW              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_32 , FN_ROR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    CLZW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_32 ,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CTZW               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_32 ,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CPOPW              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_32 ,FN_UNARY, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),

    BCLR               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ANDN, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BCLRI              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_ANDN, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BINV               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_XOR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BINVI              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_XOR , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BSET               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_OR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BSETI              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_OR  , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BEXT               -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_FIX, N, IS_N ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_BEXT, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    BEXTI              -> List(Y, N, fc2oh(FC_ALU) , RT_FIX, RT_FIX, RT_X  , N, IS_I ,N, N, N, M_X     , N, N, CSR.N, DW_XPR, FN_BEXT, X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
  )
  def RoCC_table: Seq[(BitPat, List[BitPat])] = Seq(
  // Note: We use fc2oh(FC_CSR) since CSR instructions cannot co-execute with RoCC instructions
    CUSTOM0            -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM0_RS1        -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM0_RS1_RS2    -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM0_RD         -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM0_RD_RS1     -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM0_RD_RS1_RS2 -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM1            -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM1_RS1        -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM1_RS1_RS2    -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM1_RD         -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM1_RD_RS1     -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM1_RD_RS1_RS2 -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM2            -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM2_RS1        -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM2_RS1_RS2    -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM2_RD         -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM2_RD_RS1     -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM2_RD_RS1_RS2 -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM3            -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM3_RS1        -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM3_RS1_RS2    -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM3_RD         -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM3_RD_RS1     -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
    CUSTOM3_RD_RS1_RS2 -> List(Y, N, fc2oh(FC_CSR) , RT_FIX, RT_FIX, RT_FIX, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)
  )
}
// scalastyle:on



/**
 * Decoded control signals
 */
class CtrlSigs(implicit p: Parameters) extends Bundle
{
  val legal           = Bool()
  val fp_val          = Bool()
  val fu_code         = UInt(FC_SZ.W)
  val dst_type        = UInt(2.W)
  val rs1_type        = UInt(2.W)
  val rs2_type        = UInt(2.W)
  val frs3_en         = Bool()
  val imm_sel         = UInt(IS_N.getWidth.W)
  val uses_ldq        = Bool()
  val uses_stq        = Bool()
  val is_amo          = Bool()
  val mem_cmd         = UInt(freechips.rocketchip.rocket.M_SZ.W)
  val inst_unique     = Bool()
  val flush_on_commit = Bool()
  val csr_cmd         = UInt(freechips.rocketchip.rocket.CSR.SZ.W)
  val fcn_dw          = Bool()
  val fcn_op          = UInt(SZ_ALU_FN.W)
  val fp              = new freechips.rocketchip.tile.FPUCtrlSigs()

  def decode(inst: UInt, table: Iterable[(BitPat, List[BitPat])]) = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(inst, DecodeTables.decode_default, table)
    val sigs = Seq(
      legal, fp_val, fu_code, dst_type, rs1_type,
      rs2_type, frs3_en, imm_sel, uses_ldq, uses_stq, is_amo,
      mem_cmd,
      inst_unique, flush_on_commit, csr_cmd,
      fcn_dw, fcn_op,
      fp.ldst, fp.wen, fp.ren1, fp.ren2, fp.ren3, fp.swap12,
      fp.swap23, fp.typeTagIn, fp.typeTagOut, fp.fromint, fp.toint, fp.fastpipe, fp.fma,
      fp.div, fp.sqrt, fp.wflags
    )
    sigs zip decoder map {case(s,d) => s := d}
    fp.vec := false.B
    this
  }
}





/**
 * IO bundle for the Decode unit
 */
class DecodeUnitIo(implicit p: Parameters) extends BoomBundle
{
  val enq = new Bundle { val uop = Input(new MicroOp()) }
  val deq = new Bundle { val uop = Output(new MicroOp()) }

  // from CSRFile
  val status = Input(new freechips.rocketchip.rocket.MStatus())
  val csr_decode = Flipped(new freechips.rocketchip.rocket.CSRDecodeIO)
  val fcsr_rm = Input(UInt(FPConstants.RM_SZ.W))
  val interrupt = Input(Bool())
  val interrupt_cause = Input(UInt(xLen.W))
}

/**
 * Decode unit that takes in a single instruction and generates a MicroOp.
 */
class DecodeUnit(implicit p: Parameters) extends BoomModule
  with freechips.rocketchip.rocket.constants.MemoryOpConstants
  with freechips.rocketchip.rocket.constants.ScalarOpConstants
{
  val io = IO(new DecodeUnitIo)

  val uop = Wire(new MicroOp())
  uop := io.enq.uop

  val decode_table = (
    DecodeTables.X_table ++
    DecodeTables.F_table ++
    DecodeTables.FDivSqrt_table ++
    DecodeTables.X64_table ++
    DecodeTables.B_table ++
    (if (usingRoCC) DecodeTables.RoCC_table else Nil)
  )

  val inst = uop.inst
  val LDST = inst(RD_MSB,RD_LSB)
  val LRS1 = inst(RS1_MSB,RS1_LSB)
  val LRS2 = inst(RS2_MSB,RS2_LSB)
  val LRS3 = inst(RS3_MSB,RS3_LSB)


  val cs = Wire(new CtrlSigs()).decode(inst, decode_table)

  // Exception Handling
  io.csr_decode.inst := inst
  val csr_en = cs.csr_cmd.isOneOf(CSR.S, CSR.C, CSR.W)
  val csr_ren = cs.csr_cmd.isOneOf(CSR.S, CSR.C) && uop.lrs1 === 0.U
  val system_insn = cs.csr_cmd === CSR.I
  val sfence = inst === SFENCE_VMA

  val cs_legal = cs.legal
//   dontTouch(cs_legal)

  require (fLen >= 64)
  val illegal_rm = inst(14,12).isOneOf(5.U,6.U) || (inst(14,12) === 7.U && io.fcsr_rm >= 5.U)
  val id_illegal_insn = (!cs_legal ||
    (cs.fp_val && (io.csr_decode.fp_illegal || illegal_rm)) ||
    (uop.is_rocc && io.csr_decode.rocc_illegal) ||
    (cs.is_amo && !io.status.isa('a'-'a'))  ||
    (csr_en && (io.csr_decode.read_illegal || !csr_ren && io.csr_decode.write_illegal)) ||
    ((sfence || system_insn) && io.csr_decode.system_illegal))
//     cs.div && !csr.io.status.isa('m'-'a') || TODO check for illegal div instructions

  def checkExceptions(x: Seq[(Bool, UInt)]) =
    (x.map(_._1).reduce(_||_), PriorityMux(x))

  val (xcpt_valid, xcpt_cause) = checkExceptions(List(
    (io.interrupt && !io.enq.uop.is_sfb, io.interrupt_cause),  // Disallow interrupts while we are handling a SFB
    (uop.bp_debug_if,                    (CSR.debugTriggerCause).U),
    (uop.bp_xcpt_if,                     (Causes.breakpoint).U),
    (uop.xcpt_pf_if,                     (Causes.fetch_page_fault).U),
    (uop.xcpt_ae_if,                     (Causes.fetch_access).U),
    (id_illegal_insn,                    (Causes.illegal_instruction).U)))

  uop.exception := xcpt_valid
  uop.exc_cause := xcpt_cause

  //-------------------------------------------------------------

  uop.is_mov     := inst === ADD && LRS1 === 0.U

  uop.iq_type(IQ_UNQ) := Seq(FC_MUL , FC_DIV, FC_CSR, FC_I2F).map { c => cs.fu_code(c) }.reduce(_||_)
  uop.iq_type(IQ_ALU) := Seq(FC_ALU                         ).map { c => cs.fu_code(c) }.reduce(_||_)
  uop.iq_type(IQ_MEM) := Seq(FC_AGEN, FC_DGEN               ).map { c => cs.fu_code(c) }.reduce(_||_)
  uop.iq_type(IQ_FP ) := Seq(FC_FPU , FC_FDV, FC_F2I        ).map { c => cs.fu_code(c) }.reduce(_||_)

  uop.fu_code    := cs.fu_code.asBools

  uop.ldst       := LDST
  uop.lrs1       := LRS1
  uop.lrs2       := LRS2
  uop.lrs3       := LRS3

  uop.dst_rtype  := cs.dst_type
  uop.lrs1_rtype := Mux(cs.rs1_type === RT_FIX && LRS1 === 0.U, RT_ZERO, cs.rs1_type)
  uop.lrs2_rtype := Mux(cs.rs2_type === RT_FIX && LRS2 === 0.U, RT_ZERO, cs.rs2_type)
  uop.frs3_en    := cs.frs3_en

  uop.ldst_is_rs1 := uop.is_sfb_shadow
  // SFB optimization
  when (uop.is_sfb_shadow && cs.rs2_type === RT_X) {
    uop.lrs2_rtype  := Mux(LDST === 0.U, RT_ZERO, RT_FIX)
    uop.lrs2        := LDST
    uop.ldst_is_rs1 := false.B
  } .elsewhen (uop.is_sfb_shadow && uop.is_mov) {
    uop.lrs1        := LDST
    uop.lrs1_rtype  := Mux(LDST === 0.U, RT_ZERO, RT_FIX)
    uop.ldst_is_rs1 := true.B
  }


  uop.fp_val     := cs.fp_val
  uop.fp_ctrl    := cs.fp

  uop.mem_cmd    := cs.mem_cmd
  uop.mem_size   := Mux(cs.mem_cmd.isOneOf(M_SFENCE, M_FLUSH_ALL), Cat(LRS2 =/= 0.U, LRS1 =/= 0.U), inst(13,12))
  uop.mem_signed := !inst(14)
  uop.uses_ldq   := cs.uses_ldq
  uop.uses_stq   := cs.uses_stq
  uop.is_amo     := cs.is_amo
  uop.is_fence   := inst === FENCE
  uop.is_fencei  := inst === FENCE_I
  uop.is_sfence  := inst === SFENCE_VMA
  uop.is_sys_pc2epc := inst === EBREAK || inst === ECALL
  uop.is_eret    := inst === ECALL || inst === EBREAK || inst === SRET || inst === MRET || inst === DRET
  uop.is_unique  := cs.inst_unique
  uop.is_rocc    := inst(6,0).isOneOf("b0001011".U, "b0101011".U, "b1111011".U) && inst(14,12).isOneOf(0.U, 2.U, 3.U, 4.U, 6.U, 7.U)
  uop.flush_on_commit := cs.flush_on_commit || (csr_en && !csr_ren && io.csr_decode.write_flush)


  //-------------------------------------------------------------
  // immediates

  // repackage the immediate, and then pass the fewest number of bits around
  val di24_20 = Mux(cs.imm_sel === IS_B || cs.imm_sel === IS_S, inst(11,7), inst(24,20))
  val imm_packed = Cat(inst(31,25), di24_20, inst(19,12))
  val imm = ImmGen(imm_packed, cs.imm_sel)
  val imm_hi = imm >> (immPregSz-1)
  val imm_lo = imm(immPregSz-1, 0)
  val short_imm = imm_hi === 0.U || ~imm_hi === 0.U || cs.imm_sel === IS_F3

  uop.imm_rename := cs.imm_sel =/= IS_N && cs.imm_sel =/= IS_F3
  uop.imm_packed := imm_packed
  uop.imm_sel    := cs.imm_sel
  when (short_imm) {
    uop.imm_rename := false.B
    uop.imm_sel := IS_SH
    uop.pimm := Mux(cs.imm_sel === IS_F3, inst(14,12), imm_lo)
  }

  uop.fp_rm   := Mux(inst(14,12) === 7.U, io.fcsr_rm, inst(14,12))
  uop.fp_typ  := inst(21,20)

  //-------------------------------------------------------------



  uop.csr_cmd := cs.csr_cmd
  when ((cs.csr_cmd === CSR.S || cs.csr_cmd === CSR.C) && LRS1 === 0.U) {
    uop.csr_cmd := CSR.R
  }

  uop.fcn_dw := cs.fcn_dw
  uop.fcn_op := cs.fcn_op

  uop.op1_sel := OP1_RS1
  when (inst === LUI || inst === CSRRWI || inst === CSRRSI || inst === CSRRCI ||
    inst === WFI || inst === SRET || inst === MRET || inst === DRET) {
    uop.op1_sel := OP1_ZERO
  } .elsewhen (inst === JAL || inst === JALR || inst === AUIPC) {
    uop.op1_sel := OP1_PC
  } .elsewhen (Seq(SH1ADD, SH2ADD, SH3ADD, SH1ADD_UW, SH2ADD_UW, SH3ADD_UW, ADD_UW, SLLI_UW).map(_ === inst).orR) {
    uop.op1_sel := OP1_RS1SHL
  }

  uop.op2_sel := OP2_RS2
  when (cs.is_amo || inst === CSRRW || inst === CSRRS || inst === CSRRC) {
    uop.op2_sel := OP2_ZERO
  } .elsewhen (inst === CSRRWI || inst === CSRRSI || inst === CSRRCI ||
    inst === WFI || inst === SRET || inst === DRET || inst === MRET) {
    uop.op2_sel := OP2_IMMC
  } .elsewhen (inst === JAL || inst === JALR) {
    uop.op2_sel := OP2_NEXT
  } .elsewhen (Seq(BCLR, BCLRI, BINV, BINVI, BSET, BSETI).map(_ === inst).orR) {
    uop.op2_sel := Mux(uop.lrs2_rtype === RT_FIX, OP2_RS2OH, OP2_IMMOH)
  } .elsewhen (cs.imm_sel === IS_U || cs.imm_sel === IS_I || cs.imm_sel === IS_S) {
    uop.op2_sel := OP2_IMM
  }

  uop.br_type := Seq(
    (BEQ  , B_EQ ),
    (BNE  , B_NE ),
    (BGE  , B_GE ),
    (BGEU , B_GEU),
    (BLT  , B_LT ),
    (BLTU , B_LTU),
    (JAL  , B_J  ),
    (JALR , B_JR )
  ) .map { case (c, b) => Mux(inst === c, b, 0.U) } .reduce(_|_)

  io.deq.uop := uop
}

/**
 * Smaller Decode unit for the Frontend to decode different
 * branches.
 * Accepts EXPANDED RVC instructions
  */

class BranchDecodeSignals(implicit p: Parameters) extends BoomBundle
{
  val is_ret   = Bool()
  val is_call  = Bool()
  val target   = UInt(vaddrBitsExtended.W)
  val cfi_type = UInt(CFI_SZ.W)


  // Is this branch a short forwards jump?
  val sfb_offset = Valid(UInt(log2Ceil(icBlockBytes).W))
  // Is this instruction allowed to be inside a sfb?
  val shadowable = Bool()
}

class BranchDecode(implicit p: Parameters) extends BoomModule
{
  val io = IO(new Bundle {
    val inst    = Input(UInt(32.W))
    val pc      = Input(UInt(vaddrBitsExtended.W))

    val out = Output(new BranchDecodeSignals)
  })

  val bpd_csignals =
    freechips.rocketchip.rocket.DecodeLogic(io.inst,
                  List[BitPat](N, N, N, N, X),
////                               is br?
////                               |  is jal?
////                               |  |  is jalr?
////                               |  |  |
////                               |  |  |  shadowable
////                               |  |  |  |  has_rs2
////                               |  |  |  |  |
            Seq[(BitPat, List[BitPat])](
               JAL         -> List(N, Y, N, N, X),
               JALR        -> List(N, N, Y, N, X),
               BEQ         -> List(Y, N, N, N, X),
               BNE         -> List(Y, N, N, N, X),
               BGE         -> List(Y, N, N, N, X),
               BGEU        -> List(Y, N, N, N, X),
               BLT         -> List(Y, N, N, N, X),
               BLTU        -> List(Y, N, N, N, X),

               SLLI        -> List(N, N, N, Y, N),
               SRLI        -> List(N, N, N, Y, N),
               SRAI        -> List(N, N, N, Y, N),

               ADDIW       -> List(N, N, N, Y, N),
               SLLIW       -> List(N, N, N, Y, N),
               SRAIW       -> List(N, N, N, Y, N),
               SRLIW       -> List(N, N, N, Y, N),

               ADDW        -> List(N, N, N, Y, Y),
               SUBW        -> List(N, N, N, Y, Y),
               SLLW        -> List(N, N, N, Y, Y),
               SRAW        -> List(N, N, N, Y, Y),
               SRLW        -> List(N, N, N, Y, Y),

               LUI         -> List(N, N, N, Y, N),

               ADDI        -> List(N, N, N, Y, N),
               ANDI        -> List(N, N, N, Y, N),
               ORI         -> List(N, N, N, Y, N),
               XORI        -> List(N, N, N, Y, N),
               SLTI        -> List(N, N, N, Y, N),
               SLTIU       -> List(N, N, N, Y, N),

               SLL         -> List(N, N, N, Y, Y),
               ADD         -> List(N, N, N, Y, Y),
               SUB         -> List(N, N, N, Y, Y),
               SLT         -> List(N, N, N, Y, Y),
               SLTU        -> List(N, N, N, Y, Y),
               AND         -> List(N, N, N, Y, Y),
               OR          -> List(N, N, N, Y, Y),
               XOR         -> List(N, N, N, Y, Y),
               SRA         -> List(N, N, N, Y, Y),
               SRL         -> List(N, N, N, Y, Y)
            ))

  val cs_is_br = bpd_csignals(0)(0)
  val cs_is_jal = bpd_csignals(1)(0)
  val cs_is_jalr = bpd_csignals(2)(0)
  val cs_is_shadowable = bpd_csignals(3)(0)
  val cs_has_rs2 = bpd_csignals(4)(0)

  io.out.is_call := (cs_is_jal || cs_is_jalr) && GetRd(io.inst) === RA
  io.out.is_ret  := cs_is_jalr && GetRs1(io.inst) === BitPat("b00?01") && GetRd(io.inst) === X0

  io.out.target := Mux(cs_is_br, ComputeBranchTarget(io.pc, io.inst, xLen),
                                 ComputeJALTarget(io.pc, io.inst, xLen))
  io.out.cfi_type :=
    Mux(cs_is_jalr,
      CFI_JALR,
    Mux(cs_is_jal,
      CFI_JAL,
    Mux(cs_is_br,
      CFI_BR,
      CFI_X)))

  val br_offset = Cat(io.inst(7), io.inst(30,25), io.inst(11,8), 0.U(1.W))
  // Is a sfb if it points forwards (offset is positive)
  io.out.sfb_offset.valid := cs_is_br && !io.inst(31) && br_offset =/= 0.U && (br_offset >> log2Ceil(icBlockBytes)) === 0.U
  io.out.sfb_offset.bits  := br_offset
  io.out.shadowable := cs_is_shadowable && (
    !cs_has_rs2 ||
    (GetRs1(io.inst) === GetRd(io.inst)) ||
    (io.inst === ADD && GetRs1(io.inst) === X0)
  )
}

/**
 * Track the current "branch mask", and give out the branch mask to each micro-op in Decode
 * (each micro-op in the machine has a branch mask which says which branches it
 * is being speculated under).
 *
 * @param pl_width pipeline width for the processor
 */
class BranchMaskGenerationLogic(val pl_width: Int)(implicit p: Parameters) extends BoomModule
{
  val io = IO(new Bundle {
    // guess if the uop is a branch (we'll catch this later)
    val is_branch = Input(Vec(pl_width, Bool()))
    // lock in that it's actually a branch and will fire, so we update
    // the branch_masks.
    val will_fire = Input(Vec(pl_width, Bool()))

    // give out tag immediately (needed in rename)
    // mask can come later in the cycle
    val br_tag    = Output(Vec(pl_width, UInt(brTagSz.W)))
    val br_mask   = Output(Vec(pl_width, UInt(maxBrCount.W)))

     // tell decoders the branch mask has filled up, but on the granularity
     // of an individual micro-op (so some micro-ops can go through)
    val is_full   = Output(Vec(pl_width, Bool()))

    val brupdate         = Input(new BrUpdateInfo())
    val flush_pipeline = Input(Bool())

    val debug_branch_mask = Output(UInt(maxBrCount.W))
  })

  val branch_mask = RegInit(0.U(maxBrCount.W))

  //-------------------------------------------------------------
  // Give out the branch tag to each branch micro-op

  var allocate_mask = branch_mask
  val tag_masks = Wire(Vec(pl_width, UInt(maxBrCount.W)))

  for (w <- 0 until pl_width) {
    // TODO this is a loss of performance as we're blocking branches based on potentially fake branches
    io.is_full(w) := (allocate_mask === ~(0.U(maxBrCount.W))) && io.is_branch(w)

    // find br_tag and compute next br_mask
    val new_br_tag = Wire(UInt(brTagSz.W))
    new_br_tag := 0.U
    tag_masks(w) := 0.U

    for (i <- maxBrCount-1 to 0 by -1) {
      when (~allocate_mask(i)) {
        new_br_tag := i.U
        tag_masks(w) := (1.U << i.U)
      }
    }

    io.br_tag(w) := new_br_tag
    allocate_mask = Mux(io.is_branch(w), tag_masks(w) | allocate_mask, allocate_mask)
  }

  //-------------------------------------------------------------
  // Give out the branch mask to each micro-op
  // (kill off the bits that corresponded to branches that aren't going to fire)

  var curr_mask = branch_mask
  for (w <- 0 until pl_width) {
    io.br_mask(w) := GetNewBrMask(io.brupdate, curr_mask)
    curr_mask = Mux(io.will_fire(w), tag_masks(w) | curr_mask, curr_mask)
  }

  //-------------------------------------------------------------
  // Update the current branch_mask

  when (io.flush_pipeline) {
    branch_mask := 0.U
  } .otherwise {
    val mask = Mux(io.brupdate.b2.mispredict,
      io.brupdate.b2.uop.br_mask,
      ~(0.U(maxBrCount.W)))
    branch_mask := GetNewBrMask(io.brupdate, curr_mask) & mask
  }

  io.debug_branch_mask := branch_mask
}
