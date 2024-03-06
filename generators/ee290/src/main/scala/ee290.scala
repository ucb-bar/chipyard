// //see LICENSE for license
// Dima Nikiforov
package ee290

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._

class EE290RoCCAccel(opcodes: OpcodeSet) (implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new EE290RoCCAccelModule(this)
}

class EE290RoCCAccelModule(outer: EE290RoCCAccel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val sIdle :: sProcessing :: sResponding :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val cmd = Queue(io.cmd)
  val doAdd = true.B // Assuming we're always doing add for simplicity

  // Intermediate registers to hold operation information
  val opRs1 = Reg(UInt(64.W))
  val opRs2 = Reg(UInt(64.W))
  val opRd = Reg(UInt(5.W))
  val result = Reg(UInt(64.W))

  switch(state) {
    is(sIdle) {
      when(cmd.fire && doAdd) {
        opRs1 := cmd.bits.rs1
        opRs2 := cmd.bits.rs2
        opRd := cmd.bits.inst.rd
        state := sProcessing
      }
    }
    is(sProcessing) {
      result := opRs1 + opRs2
      state := sResponding
    }
    is(sResponding) {
      when(io.resp.ready) {
        state := sIdle
      }
    }
  }

  // Control signals
  cmd.ready := state === sIdle
  io.resp.valid := state === sResponding
  io.resp.bits.rd := opRd
  io.resp.bits.data := result
  io.busy := state =/= sIdle
  io.interrupt := false.B
}


class WithEE290RoCCAccel extends Config ((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val ee290 = LazyModule.apply(new EE290RoCCAccel(OpcodeSet.custom0)(p))
      ee290
    }
  )
})

class EE290RoCCAccelBlackBox(implicit val p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    // Define the same interface as your RoCC interface
    val clock = Input(Clock())
    val reset = Input(Reset())
    val cmd_valid = Input(Bool())
    val cmd_ready = Output(Bool())
    val rs1 = Input(UInt(64.W))
    val rs2 = Input(UInt(64.W))
    val rd = Input(UInt(5.W))
    val resp_valid = Output(Bool())
    val resp_ready = Input(Bool())
    val resp_rd = Output(UInt(5.W))
    val resp_data = Output(UInt(64.W))
  })

  // Specify the path to the Verilog file relative to the project's source directory
  addResource("/vsrc/EE290RoCCAccelBlackBox.v")
}

class EE290RoCCAccelBlackBoxWrapper(opcodes: OpcodeSet) (implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new EE290RoCCAccelBlackBoxWrapperModule(this)
}

class EE290RoCCAccelBlackBoxWrapperModule(outer: EE290RoCCAccelBlackBoxWrapper)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val blackbox = Module(new EE290RoCCAccelBlackBox)

  // Connect the blackbox IO to the RoCC IO, adapting as necessary
  blackbox.io.clock := clock
  blackbox.io.reset := reset
  blackbox.io.cmd_valid := io.cmd.valid
  blackbox.io.rs1 := io.cmd.bits.rs1
  blackbox.io.rs2 := io.cmd.bits.rs2
  blackbox.io.rd := io.cmd.bits.inst.rd
  blackbox.io.resp_ready := io.resp.ready

  io.cmd.ready := blackbox.io.cmd_ready
  io.resp.valid := blackbox.io.resp_valid
  io.resp.bits.rd := blackbox.io.resp_rd
  io.resp.bits.data := blackbox.io.resp_data
  io.busy := io.cmd.valid || io.resp.valid // Simplified busy signal for demonstration
  io.interrupt := false.B
}

class WithEE290RoCCAccelBlackBox extends Config ((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val ee290 = LazyModule.apply(new EE290RoCCAccelBlackBoxWrapper(OpcodeSet.custom0)(p))
      ee290
    }
  )
})
