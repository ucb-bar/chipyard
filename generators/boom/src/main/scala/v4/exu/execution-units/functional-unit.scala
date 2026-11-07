//******************************************************************************
// Copyright (c) 2013 - 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Functional Units
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//
// If regfile bypassing is disabled, then the functional unit must do its own
// bypassing in here on the WB stage (i.e., bypassing the io.resp.data)
//
// TODO: explore possibility of conditional IO fields? if a branch unit... how to add extra to IO in subclass?

package boom.v4.exu

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util._
import freechips.rocketchip.tile
import freechips.rocketchip.rocket.{PipelinedMultiplier,BP,BreakpointUnit,Causes,CSR}
import freechips.rocketchip.rocket.ALU._

import boom.v4.common._
import boom.v4.ifu._
import boom.v4.util._



/**
 * Bundle for signals sent to the functional unit
 *
 * @param dataWidth width of the data sent to the functional unit
 */
class FuncUnitReq(val dataWidth: Int)(implicit p: Parameters) extends BoomBundle
  with HasBoomUOP
{
  val numOperands = 3

  val rs1_data = UInt(dataWidth.W)
  val rs2_data = UInt(dataWidth.W)
  val rs3_data = UInt(dataWidth.W) // only used for FMA units
  val ftq_info = Vec(2, new FTQInfo) // Need this-pc and next-pc for JALR
  val pred_data = Bool()
  val imm_data = UInt(xLen.W) // only used for integer ALU and AGen units
}

class BrInfoBundle(implicit p: Parameters) extends BoomBundle
{
  val ldq_idx = UInt((1+ldqAddrSz).W)
  val stq_idx = UInt((1+stqAddrSz).W)
  val rxq_idx = UInt(log2Ceil(numRxqEntries).W)
}

/**
 * Branch resolution information given from the branch unit
 */
class BrResolutionInfo(implicit p: Parameters) extends BoomBundle with HasBoomUOP
{
  val mispredict = Bool()
  val taken      = Bool()                     // which direction did the branch go?
  val cfi_type   = UInt(CFI_SZ.W)

  // Info for recalculating the pc for this branch
  val pc_sel     = UInt(2.W)

  val jalr_target = UInt(vaddrBitsExtended.W)
  val target_offset = SInt(21.W)
}

class BrUpdateInfo(implicit p: Parameters) extends BoomBundle
{
  // On the first cycle we get masks to kill registers
  val b1 = new BrUpdateMasks
  // On the second cycle we get indices to reset pointers
  val b2 = new BrResolutionInfo
}

class BrUpdateMasks(implicit p: Parameters) extends BoomBundle
{
  val resolve_mask = UInt(maxBrCount.W)
  val mispredict_mask = UInt(maxBrCount.W)
}


/**
 * Abstract top level functional unit class that wraps a lower level hand made functional unit
 *
 * @param isPipelined is the functional unit pipelined?
 * @param numStages how many pipeline stages does the functional unit have
 * @param dataWidth width of the data being operated on in the functional unit
 * @param hasBranchUnit does this functional unit have a branch unit?
 */
abstract class FunctionalUnit(
  val dataWidth: Int,
  val isAluUnit: Boolean = false,
  val needsFcsr: Boolean = false)
  (implicit p: Parameters) extends BoomModule
{
  val io = IO(new Bundle {
    val kill   = Input(Bool())

    val req    = Flipped(new DecoupledIO(new FuncUnitReq(dataWidth)))
    val resp   = (new DecoupledIO(new ExeUnitResp(dataWidth)))
    //val fflags = new ValidIO(new FFlagsResp)

    val brupdate = Input(new BrUpdateInfo())

    // only used by the fpu unit
    val fcsr_rm = if (needsFcsr) Input(UInt(tile.FPConstants.RM_SZ.W)) else null

    // only used by branch unit
    val brinfo     = if (isAluUnit) Output(Valid(new BrResolutionInfo)) else null
  })
  io.resp.bits.fflags.valid := false.B
  io.resp.bits.fflags.bits  := DontCare
  io.resp.bits.predicated   := false.B

}

/**
 * Functional unit that wraps RocketChips ALU
 *
 * @param isBranchUnit is this a branch unit?
 * @param numStages how many pipeline stages does the functional unit have
 * @param dataWidth width of the data being operated on in the functional unit
 */
class ALUUnit(dataWidth: Int)(implicit p: Parameters)
  extends FunctionalUnit(
    isAluUnit = true,
    dataWidth = dataWidth)
  with boom.v4.ifu.HasBoomFrontendParameters
  with freechips.rocketchip.rocket.constants.ScalarOpConstants
{
  io.req.ready := true.B
  val uop = io.req.bits.uop

  // immediate generation
  val imm_xprlen = io.req.bits.imm_data //ImmGen(uop.imm_packed, uop.imm_sel)

  // operand 1 select

  // Get the uop PC for jumps
  val block_pc = AlignPCToBoundary(io.req.bits.ftq_info(0).pc, icBlockBytes)
  val uop_pc = (block_pc | uop.pc_lob) - Mux(uop.edge_inst, 2.U, 0.U)
  val op1_shamt = Mux(uop.fcn_op === FN_ADD, io.req.bits.uop.pimm(2,1), 0.U)
  val op1_shl = Mux(uop.fcn_dw === DW_32, // shaddw
    io.req.bits.rs1_data(31,0), io.req.bits.rs1_data) << op1_shamt

  val op1_data = MuxLookup(uop.op1_sel, 0.U)(Seq(
    OP1_RS1    -> io.req.bits.rs1_data,
    OP1_PC     -> Sext(uop_pc, xLen),
    OP1_RS1SHL  -> op1_shl
  ))

  // operand 2 select
  val op2_oh = UIntToOH(Mux(uop.op2_sel(0), // rs1
    io.req.bits.rs2_data, imm_xprlen)(log2Ceil(xLen)-1,0))
  val op2_data = MuxLookup(uop.op2_sel, 0.U)(Seq(
    OP2_IMM  -> Sext(imm_xprlen, xLen),
    OP2_IMMC -> io.req.bits.uop.prs1(4,0),
    OP2_RS2  -> io.req.bits.rs2_data,
    OP2_NEXT -> Mux(uop.is_rvc, 2.U, 4.U),
    OP2_RS2OH -> op2_oh,
    OP2_IMMOH -> op2_oh
  ))

  val alu = Module(new freechips.rocketchip.rocket.ALU())

  alu.io.in1 := op1_data.asUInt
  alu.io.in2 := op2_data.asUInt
  alu.io.fn  := uop.fcn_op
  alu.io.dw  := Mux(uop.op1_sel === OP1_RS1SHL, DW_64, uop.fcn_dw)


  val rs1 = io.req.bits.rs1_data
  val rs2 = io.req.bits.rs2_data
  val br_eq  = (rs1 === rs2)
  val br_ltu = (rs1.asUInt < rs2.asUInt)
  val br_lt  = (~(rs1(xLen-1) ^ rs2(xLen-1)) & br_ltu |
                rs1(xLen-1) & ~rs2(xLen-1)).asBool

  val pc_sel = MuxLookup(uop.br_type, PC_PLUS4)(
                 Seq(   B_N   -> PC_PLUS4,
                        B_NE  -> Mux(!br_eq,  PC_BRJMP, PC_PLUS4),
                        B_EQ  -> Mux( br_eq,  PC_BRJMP, PC_PLUS4),
                        B_GE  -> Mux(!br_lt,  PC_BRJMP, PC_PLUS4),
                        B_GEU -> Mux(!br_ltu, PC_BRJMP, PC_PLUS4),
                        B_LT  -> Mux( br_lt,  PC_BRJMP, PC_PLUS4),
                        B_LTU -> Mux( br_ltu, PC_BRJMP, PC_PLUS4),
                        B_J   -> PC_BRJMP,
                        B_JR  -> PC_JALR
                        ))

  val is_taken = io.req.valid &&
                   (uop.br_type =/= B_N) &&
                   (pc_sel =/= PC_PLUS4)


  // Branch/Jump Target Calculation
  // For jumps we read the FTQ, and can calculate the target
  // For branches we emit the offset for the core to redirect if necessary
  val target_offset = imm_xprlen(20,0).asSInt

  def encodeVirtualAddress(a0: UInt, ea: UInt) = if (vaddrBitsExtended == vaddrBits) {
    ea
  } else {
    // Efficient means to compress 64-bit VA into vaddrBits+1 bits.
    // (VA is bad if VA(vaddrBits) != VA(vaddrBits-1)).
    val a = a0.asSInt >> vaddrBits
    val msb = Mux(a === 0.S || a === -1.S, ea(vaddrBits), !ea(vaddrBits-1))
    Cat(msb, ea(vaddrBits-1,0))
  }



  // "mispredict" means that a branch has been resolved and it must be killed
  val mispredict = WireInit(false.B)

  val is_br          = io.req.valid && uop.is_br && !uop.is_sfb
  val is_jal         = io.req.valid && uop.is_jal
  val is_jalr        = io.req.valid && uop.is_jalr

  val jalr_target_base = io.req.bits.rs1_data.asSInt
  val jalr_target_xlen = Wire(UInt(xLen.W))
  jalr_target_xlen := (jalr_target_base + target_offset).asUInt
  val jalr_target = (encodeVirtualAddress(jalr_target_xlen, jalr_target_xlen).asSInt & -2.S).asUInt

  val cfi_idx = ((uop.pc_lob ^ Mux(io.req.bits.ftq_info(0).entry.start_bank === 1.U, 1.U << log2Ceil(bankBytes), 0.U)))(log2Ceil(fetchWidth),1)


  when (is_br || is_jalr) {
    when (pc_sel === PC_PLUS4) {
      mispredict := uop.taken
    }
    when (pc_sel === PC_BRJMP) {
      mispredict := !uop.taken
    }
    when (pc_sel === PC_JALR) {
      mispredict := (!io.req.bits.ftq_info(1).valid ||
                     (io.req.bits.ftq_info(1).pc =/= jalr_target) ||
                     !io.req.bits.ftq_info(0).entry.cfi_idx.valid ||
                     (io.req.bits.ftq_info(0).entry.cfi_idx.bits =/= cfi_idx))
    }
  }

  val brinfo = Wire(Valid(new BrResolutionInfo))

  // note: jal doesn't allocate a branch-mask, so don't clear a br-mask bit
  brinfo.valid          := is_br || is_jalr
  brinfo.bits.mispredict     := mispredict
  brinfo.bits.uop            := uop
  brinfo.bits.cfi_type       := Mux(is_jalr, CFI_JALR,
                                Mux(is_br  , CFI_BR, CFI_X))
  brinfo.bits.taken          := is_taken
  brinfo.bits.pc_sel         := pc_sel

  brinfo.bits.jalr_target    := DontCare



  brinfo.bits.jalr_target := jalr_target




  brinfo.bits.target_offset := target_offset

  io.brinfo := brinfo




// Response
// TODO add clock gate on resp bits from functional units
//   io.resp.bits.data := RegEnable(alu.io.out, io.req.valid)
//   val reg_data = Reg(outType = Bits(width = xLen))
//   reg_data := alu.io.out
//   io.resp.bits.data := reg_data
  val alu_out = Mux(io.req.bits.uop.is_sfb_shadow && io.req.bits.pred_data,
      Mux(io.req.bits.uop.ldst_is_rs1, io.req.bits.rs1_data, io.req.bits.rs2_data),
      Mux(io.req.bits.uop.is_mov, io.req.bits.rs2_data, alu.io.out))
  io.resp.valid := io.req.valid
  io.resp.bits.uop := io.req.bits.uop
  io.resp.bits.data := Mux(io.req.bits.uop.is_sfb_br, pc_sel === PC_BRJMP, alu_out)
  io.resp.bits.predicated := io.req.bits.uop.is_sfb_shadow && io.req.bits.pred_data
  assert(io.resp.ready)


}



/**
 * Functional unit to wrap lower level FPU
 *
 * Currently, bypassing is unsupported!
 * All FP instructions are padded out to the max latency unit for easy
 * write-port scheduling.
 */
class FPUUnit(implicit p: Parameters)
  extends FunctionalUnit(
  //numBypassStages = 0,
    dataWidth = 65,
    needsFcsr = true)
{
  io.req.ready := true.B
  val numStages = p(tile.TileKey).core.fpu.get.dfmaLatency

  val pipe = Module(new BranchKillablePipeline(new FuncUnitReq(dataWidth), numStages))
  pipe.io.req := io.req
  pipe.io.flush := io.kill
  pipe.io.brupdate := io.brupdate
  val fpu = Module(new FPU())
  fpu.io.req.valid         := io.req.valid
  fpu.io.req.bits.uop      := io.req.bits.uop
  fpu.io.req.bits.rs1_data := io.req.bits.rs1_data
  fpu.io.req.bits.rs2_data := io.req.bits.rs2_data
  fpu.io.req.bits.rs3_data := io.req.bits.rs3_data
  fpu.io.req.bits.fcsr_rm  := io.fcsr_rm

  io.resp.valid        := pipe.io.resp(numStages-1).valid
  io.resp.bits.uop     := pipe.io.resp(numStages-1).bits.uop
  io.resp.bits.data    := fpu.io.resp.bits.data
  io.resp.bits.fflags.valid := io.resp.valid
  io.resp.bits.fflags.bits  := fpu.io.resp.bits.fflags.bits
}

/**
 * Int to FP conversion functional unit
 *
 * @param latency the amount of stages to delay by
 */
class IntToFPUnit(latency: Int)(implicit p: Parameters)
  extends FunctionalUnit(
  //numBypassStages = 0,
    dataWidth = 65,
    needsFcsr = true)
  with tile.HasFPUParameters
{
  val io_req = io.req.bits

  io.req.ready := true.B
  val pipe = Module(new BranchKillablePipeline(new FuncUnitReq(dataWidth), latency))
  pipe.io.req := io.req
  pipe.io.flush := io.kill
  pipe.io.brupdate := io.brupdate
  val fp_ctrl = io_req.uop.fp_ctrl
  val fp_rm = Mux(io_req.uop.fp_rm === 7.U, io.fcsr_rm, io_req.uop.fp_rm)

  val req = Wire(new tile.FPInput)
  val tag = fp_ctrl.typeTagIn

  req.viewAsSupertype(new tile.FPUCtrlSigs) <> fp_ctrl

  req.rm := fp_rm
  req.in1 := unbox(io_req.rs1_data, tag, None)
  req.in2 := unbox(io_req.rs2_data, tag, None)
  req.in3 := DontCare
  req.typ := io_req.uop.fp_typ
  req.fmt := DontCare // FIXME: this may not be the right thing to do here
  req.fmaCmd := DontCare

  assert (!(io.req.valid && fp_ctrl.fromint && req.in1(xLen).asBool),
    "[func] IntToFP integer input has 65th high-order bit set!")

  assert (!(io.req.valid && !fp_ctrl.fromint),
    "[func] Only support fromInt micro-ops.")

  val ifpu = Module(new tile.IntToFP(intToFpLatency))
  ifpu.io.in.valid := io.req.valid
  ifpu.io.in.bits := req
  ifpu.io.in.bits.in1 := io_req.rs1_data
  val out_double = Pipe(io.req.valid, fp_ctrl.typeTagOut === D, intToFpLatency).bits

  io.resp.valid        := pipe.io.resp(latency-1).valid
  io.resp.bits.uop     := pipe.io.resp(latency-1).bits.uop
  io.resp.bits.data    := box(ifpu.io.out.bits.data, out_double)
  io.resp.bits.fflags.valid     := io.resp.valid
  io.resp.bits.fflags.bits      := ifpu.io.out.bits.exc
}

/**
 * Divide functional unit.
 *
 * @param dataWidth data to be passed into the functional unit
 */
class DivUnit(dataWidth: Int)(implicit p: Parameters)
  extends FunctionalUnit(dataWidth = dataWidth)
{

  // We don't use the iterative multiply functionality here.
  // Instead we use the PipelinedMultiplier
  val div = Module(new freechips.rocketchip.rocket.MulDiv(mulDivParams, width = dataWidth))

  val req = Reg(Valid(new MicroOp()))

  when (io.req.fire) {
    req.valid := !IsKilledByBranch(io.brupdate, io.kill, io.req.bits)
    req.bits  := UpdateBrMask(io.brupdate, io.req.bits.uop)
  } .otherwise {
    req.valid := !IsKilledByBranch(io.brupdate, io.kill, req.bits) && req.valid
    req.bits  := UpdateBrMask(io.brupdate, req.bits)
  }
  when (reset.asBool) {
    req.valid := false.B
  }

  // request
  div.io.req.valid    := io.req.valid && !IsKilledByBranch(io.brupdate, io.kill, io.req.bits)
  div.io.req.bits.dw  := io.req.bits.uop.fcn_dw
  div.io.req.bits.fn  := io.req.bits.uop.fcn_op
  div.io.req.bits.in1 := io.req.bits.rs1_data
  div.io.req.bits.in2 := io.req.bits.rs2_data
  div.io.req.bits.tag := DontCare
  io.req.ready        := div.io.req.ready && !req.valid

  // handle pipeline kills and branch misspeculations
  div.io.kill         := (req.valid && IsKilledByBranch(io.brupdate, io.kill, req.bits))

  // response
  io.resp.valid       := div.io.resp.valid && req.valid
  div.io.resp.ready   := io.resp.ready
  io.resp.valid       := div.io.resp.valid && req.valid
  io.resp.bits.data   := div.io.resp.bits.data
  io.resp.bits.uop    := req.bits
  when (io.resp.fire) {
    req.valid := false.B
  }
}

/**
 * Pipelined multiplier functional unit that wraps around the RocketChip pipelined multiplier
 *
 * @param numStages number of pipeline stages
 * @param dataWidth size of the data being passed into the functional unit
 */
class PipelinedMulUnit(numStages: Int, dataWidth: Int)(implicit p: Parameters)
  extends FunctionalUnit(dataWidth = dataWidth)
{
  io.req.ready := true.B
  val imul = Module(new PipelinedMultiplier(xLen, numStages))
  val pipe = Module(new BranchKillablePipeline(new FuncUnitReq(dataWidth), numStages))
  // request
  imul.io.req.valid    := io.req.valid
  imul.io.req.bits.fn  := io.req.bits.uop.fcn_op
  imul.io.req.bits.dw  := io.req.bits.uop.fcn_dw
  imul.io.req.bits.in1 := io.req.bits.rs1_data
  imul.io.req.bits.in2 := io.req.bits.rs2_data
  imul.io.req.bits.tag := DontCare

  pipe.io.req          := io.req
  pipe.io.flush        := io.kill
  pipe.io.brupdate     := io.brupdate
  // response
  io.resp.valid        := pipe.io.resp(numStages-1).valid
  io.resp.bits.uop     := pipe.io.resp(numStages-1).bits.uop
  io.resp.bits.data    := imul.io.resp.bits.data
  io.resp.bits.predicated := false.B
}
