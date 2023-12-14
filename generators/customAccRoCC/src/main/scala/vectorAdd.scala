package customAccRoCC

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._

object consts {
  val len = 64
}

class vectorAdd()(implicit p: Parameters) extends Module {

  val io = IO(new Bundle {
    val cmd = Flipped(Decoupled(new RoCCCommand))
    val resp = Decoupled(new RoCCResponse)
    val busy = Output(Bool())
  })
  
  // The parts of the command are as follows
  // inst - the parts of the instruction itself
  //   opcode
  //   rd - destination register number
  //   rs1 - first source register number
  //   rs2 - second source register number
  //   funct
  //   xd - is the destination register being used?
  //   xs1 - is the first source register being used?
  //   xs2 - is the second source register being used?
  // rs1 - the value of source register 1
  // rs2 - the value of source register 2

  /* Instantiating Wires and Regs */
  val in1_vec_wire = WireInit(VecInit(Seq.fill(8) {0.U(8.W)})) 
  val in2_vec_wire = WireInit(VecInit(Seq.fill(8) {0.U(8.W)})) 
  val sum_vec_wire = /* YOUR CODE HERE */ 
  
  val cmd_bits_reg = RegInit(0.U.asTypeOf(new RoCCCommand))
  val state_reg = RegInit(0.U(1.W))

  /* in ready */
  io.cmd.ready := ~state_reg
  /* busy */
  io.busy := state_reg
  /* out valid */  
  io.resp.valid := state_reg

  when (state_reg === 0.U) {
    when (io.cmd.fire) {
      state_reg := ~state_reg
      cmd_bits_reg := io.cmd.bits
    }    
  }.otherwise {
    when (io.resp.fire) {
      state_reg := ~state_reg
    }    
  }
    
  io.resp.bits.rd := /* YOUR CODE HERE */ 
  io.resp.bits.data := sum_vec_wire.asTypeOf(UInt(consts.len.W))

  /* Set up inputs */
  in1_vec_wire := cmd_bits_reg.rs1.asTypeOf(in1_vec_wire)
  in2_vec_wire := /* YOUR CODE HERE */
 
  /* Performing sum */
  for (i <- 0 until 8) {
		/* YOUR CODE HERE */
  }

	/* printf for debugging in sim */
	// printf("sum_vec_wire = %x \n", sum_vec_wire.asUInt) // example C-style printf
}
