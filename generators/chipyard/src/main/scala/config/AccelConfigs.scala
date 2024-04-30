// // See LICENSE.Berkeley for license details.
// // See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import chisel3._
import chisel3.util._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.InOrderArbiter

class AdderExample(opcodes: OpcodeSet, val n: Int = 4)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new AdderExampleModuleImp(this)
  override def shouldBeInlined = false
}

class AdderExampleModuleImp(outer: AdderExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  // dummy accel example designed to reply immediately with no memory io
  val cmd = Queue(io.cmd)

  val funct = cmd.bits.inst.funct
  val num1 = cmd.bits.rs1
  val num2 = cmd.bits.rs2
  val doEcho = funct === 0.U
  val doWrite = funct === 1.U

  // datapath
  val wdata = Mux(doWrite, num1 + num2, num1)

  cmd.ready := true.B
  io.resp.valid := cmd.valid
  io.resp.bits.rd := cmd.bits.inst.rd
  io.resp.bits.data := wdata

  io.busy := true.B
  io.interrupt := false.B
  io.mem.req.valid := false.B
}