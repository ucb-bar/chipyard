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

/// WITH CACHES

class EE290RoCCAccelWithCacheBlackBox(implicit val p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    // Define the same interface as your RoCC interface
    val clock = Input(Clock())
    val reset = Input(Reset())
    val cmd_valid = Input(Bool())
    val cmd_ready = Output(Bool())
    val rs1 = Input(UInt(64.W))
    val rs2 = Input(UInt(64.W))
    val rd = Input(UInt(5.W))
    val funct = Input(UInt(7.W))
    val resp_valid = Output(Bool())
    val resp_ready = Input(Bool())
    val resp_rd = Output(UInt(5.W))
    val resp_data = Output(UInt(64.W))
    val busy = Output(Bool())

    // Cache interface
    // TODO req
    val mem_req_valid = Output(Bool())
    val mem_req_ready = Input(Bool())
    val mem_req_addr = Output(UInt(64.W))
    val mem_req_tag = Output(UInt(64.W))
    val mem_req_wen = Output(Bool())

    val mem_wdata = Output(UInt(64.W))
    val s2_nack = Input(Bool())
    // TODO resp
    val mem_resp_valid = Input(Bool())
    val mem_resp_replay = Input(Bool())
    val mem_resp_has_data = Input(Bool())
    val mem_resp_data_raw = Input(UInt(64.W))
  })

  // Specify the path to the Verilog file relative to the project's source directory
  addResource("/vsrc/EE290RoCCAccelWithCacheBlackBox.v")
}

class EE290RoCCAccelWithCacheBlackBoxWrapper(opcodes: OpcodeSet) (implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new EE290RoCCAccelWithCacheBlackBoxWrapperModule(this)
}

class EE290RoCCAccelWithCacheBlackBoxWrapperModule(outer: EE290RoCCAccelWithCacheBlackBoxWrapper)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val blackbox = Module(new EE290RoCCAccelWithCacheBlackBox)

  // Connect the blackbox IO to the RoCC IO, adapting as necessary
  blackbox.io.clock := clock
  blackbox.io.reset := reset
  blackbox.io.cmd_valid := io.cmd.valid
  blackbox.io.rs1 := io.cmd.bits.rs1
  blackbox.io.rs2 := io.cmd.bits.rs2
  blackbox.io.rd := io.cmd.bits.inst.rd
  blackbox.io.funct := io.cmd.bits.inst.funct
  blackbox.io.resp_ready := io.resp.ready

  io.cmd.ready := blackbox.io.cmd_ready
  io.resp.valid := blackbox.io.resp_valid
  io.resp.bits.rd := blackbox.io.resp_rd
  io.resp.bits.data := blackbox.io.resp_data
  io.busy := blackbox.io.busy
  io.interrupt := false.B


  // Cache
  // TODO req
  io.mem.req.valid := blackbox.io.mem_req_valid
  blackbox.io.mem_req_ready := io.mem.req.ready
  io.mem.req.bits.addr := blackbox.io.mem_req_addr
  // io.mem.req.bits.idx := 0.U // TODO SIZE APPROPRIATELY
  // io.mem.req.bits.tag := 0.U // TODO SIZE APPROPRIATELY 
  io.mem.req.bits.tag := blackbox.io.mem_req_tag
  when (blackbox.io.mem_req_wen) {
    io.mem.req.bits.cmd := M_XWR
  } .otherwise {
    io.mem.req.bits.cmd := M_XRD
  }
  io.mem.req.bits.size := 3.U // TODO SIZE APPROPRIATELY
  io.mem.req.bits.signed := false.B
  io.mem.req.bits.dprv := io.cmd.bits.status.dprv
  io.mem.req.bits.dv := io.cmd.bits.status.dv


  io.mem.s1_kill := false.B
  io.mem.s1_data.data := blackbox.io.mem_wdata // TODO SIZE APPROPRIATELY (what is the size of the data)?
  blackbox.io.s2_nack := io.mem.s2_nack
  // skip s2_nack_cause_raw
  io.mem.s2_kill := false.B
  // ignore uncached and paddr signals
  // TODO resp
  // ignore data_word_bypass, store_data
  blackbox.io.mem_resp_valid := io.mem.resp.valid
  blackbox.io.mem_resp_replay := io.mem.resp.bits.replay
  blackbox.io.mem_resp_has_data := io.mem.resp.bits.has_data
  blackbox.io.mem_resp_data_raw := io.mem.resp.bits.data_raw


  // blackbox.io.replay_next = io.mem.replay_next // ignore replay_next
  // ignore s2_xcpt and s2_gpa and s2_gpa_is_pte
  // ignore uncached_resp
  // ignore ordered and perf
  io.mem.keep_clock_enabled := true.B
  // ignore clock_enabed
}

class WithEE290RoCCAccelWithCacheBlackBox extends Config ((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val ee290 = LazyModule.apply(new EE290RoCCAccelWithCacheBlackBoxWrapper(OpcodeSet.custom1)(p))
      ee290
    }
  )
})