package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._

object ReRoCCInstructions {
  // op1 = tracker
  // op2 = sel mask
  // ret = mask of selected
  val acquire = 0.U(7.W)
  // op1 = tracker
  val release = 1.U(7.W)
  // op1 = tracker
  // op2 = opcode
  val assign = 2.U(7.W)
  // ret = number of trackers
  val info = 3.U(7.W)
  // op1 = tracker
  val fence = 4.U(7.W)
  // op1 = vaddr
  val cflush = 5.U(7.W)
}

class InstructionSender(b: ReRoCCBundleParams)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val tag = Input(UInt())
    val send_mstatus = Input(Bool())
    val cmd = Flipped(Decoupled(new RoCCCommand))
    val rr = Decoupled(new ReRoCCMsgBundle(b))
  })
  val s_inst :: s_mstatus0 :: s_mstatus1 :: s_rs1 :: s_rs2 :: Nil = Enum(5)
  val state = RegInit(s_inst)

  io.rr.valid := io.cmd.valid
  io.rr.bits.opcode := ReRoCCProtocolOpcodes.mInst
  io.rr.bits.client_id := 0.U
  io.rr.bits.manager_id := 0.U // set externally
  io.rr.bits.data := MuxLookup(state, 0.U, Seq(
    (s_inst     -> Cat(io.tag, io.send_mstatus, io.cmd.bits.inst.asUInt(31,0))),
    (s_mstatus0 -> io.cmd.bits.status.asUInt),
    (s_mstatus1 -> (io.cmd.bits.status.asUInt >> 64)),
    (s_rs1      -> io.cmd.bits.rs1),
    (s_rs2      -> io.cmd.bits.rs2)))
  io.rr.bits.last := false.B
  io.cmd.ready := io.rr.ready && io.rr.bits.last

  val next_state = WireInit(state)

  when (state === s_inst) {

    next_state := Mux(io.send_mstatus, s_mstatus0,
      Mux(io.cmd.bits.inst.xs1, s_rs1,
        Mux(io.cmd.bits.inst.xs1, s_rs2, s_inst)))
    io.rr.bits.last := !io.send_mstatus && !io.cmd.bits.inst.xs1 && !io.cmd.bits.inst.xs2

  } .elsewhen (state === s_mstatus0) {

    next_state := s_mstatus1

  } .elsewhen (state === s_mstatus1) {

    next_state := Mux(io.cmd.bits.inst.xs1, s_rs1,
      Mux(io.cmd.bits.inst.xs2, s_rs2, s_inst))
    io.rr.bits.last := !io.cmd.bits.inst.xs1 && !io.cmd.bits.inst.xs2

  } .elsewhen (state === s_rs1) {

    next_state := Mux(io.cmd.bits.inst.xs2, s_rs2, s_inst)
    io.rr.bits.last := !io.cmd.bits.inst.xs2

  } .elsewhen (state === s_rs2) {

    next_state := s_inst
    io.rr.bits.last := true.B

  }

  when (io.rr.fire()) {
    state := next_state
  }
}

class ReRoCCSingleOpcodeClient(implicit p: Parameters) extends LazyModule {
  val reRoCCNode = ReRoCCClientNode()
  override lazy val module = new LazyModuleImp(this) {
    val (rerocc, edge) = reRoCCNode.out(0)
    val io = IO(new Bundle {
      val cmd = Flipped(Decoupled(new RoCCCommand))
      val ptbr = Input(new PTBR)
      val resp = Decoupled(new RoCCResponse)
      val fencing = Output(Bool())
    })

    val cmd = Queue(io.cmd)

    val s_idle :: s_acquiring :: s_acq_wait :: s_busy :: s_releasing :: s_rel_wait :: s_unbusying :: s_unbusy_wait :: Nil = Enum(8)

    val maxIBufEntries = edge.mParams.ibufEntries.max
    require(maxIBufEntries > 1)
    val instCtrSz = log2Ceil(maxIBufEntries)

    val state        = RegInit(s_idle)
    val fencing      = RegInit(false.B)
    // tracks downstream instruction reordering resources
    val inst_ctr     = Reg(UInt(instCtrSz.W))
    val max_ctr      = Reg(UInt(instCtrSz.W))
    val inst_ret_ctr = Reg(UInt(instCtrSz.W))
    val status       = Reg(new MStatus)
    val ptbr         = Reg(new PTBR)
    val manager      = Reg(UInt(edge.mParams.nManagers.W))
    val acq_rd       = Reg(UInt(5.W))
    val inflight_wbs = RegInit(0.U(instCtrSz.W))
    val incr_inflight_wbs = WireInit(false.B)
    val decr_inflight_wbs = WireInit(false.B)
    inflight_wbs := (inflight_wbs + incr_inflight_wbs) - decr_inflight_wbs
    val inst_empty = (inst_ret_ctr + 1.U === inst_ctr) || (inst_ctr === 0.U && inst_ret_ctr === max_ctr)

    io.fencing := fencing
    // handles instructions that match what we've acquired
    val inst_q = Module(new Queue(new RoCCCommand, 1, pipe=true))

    // 0 -> acquire
    // 1 -> inst
    // 2 -> release
    // 3 -> unbusy
    val req_arb = Module(new HellaPeekingArbiter(
      new ReRoCCMsgBundle(edge.bundle), 4,
      (b: ReRoCCMsgBundle) => b.last,
      Some((b: ReRoCCMsgBundle) => true.B)))
    rerocc.req <> req_arb.io.out

    // 0 -> acquire-fast-deny
    // 1 -> acquire
    // 2 -> writeback
    val resp_arb = Module(new Arbiter(new RoCCResponse, 3))
    resp_arb.io.in.foreach(_.valid := false.B)
    io.resp <> Queue(resp_arb.io.out)

    cmd.ready := false.B
    when (cmd.valid) {
      // cfg instructions are opcode0
      when (OpcodeSet.custom0.matches(cmd.bits.inst.opcode)) {
        val mask = cmd.bits.rs2
        when (cmd.bits.inst.funct === ReRoCCInstructions.acquire) {
          when (state === s_idle && mask(edge.mParams.nManagers-1,0) =/= 0.U) {
            cmd.ready := true.B
            state     := s_acquiring
            manager   := mask
            status    := cmd.bits.status
            ptbr      := io.ptbr
            acq_rd    := cmd.bits.inst.rd
          } .otherwise {
            cmd.ready := resp_arb.io.in(0).ready
            resp_arb.io.in(0).valid := true.B
          }
        } .elsewhen (cmd.bits.inst.funct === ReRoCCInstructions.release) {
          cmd.ready := inflight_wbs === 0.U && inst_q.io.count === 0.U && inst_empty && state === s_busy
          when (cmd.ready) { state := s_releasing }
        } .elsewhen (cmd.bits.inst.funct === ReRoCCInstructions.fence) {
          cmd.ready := inflight_wbs === 0.U && inst_q.io.count === 0.U && inst_empty && state === s_busy
          fencing := true.B
          when (cmd.ready) { state := s_unbusying }
        } .otherwise {
          assert(false.B)
        }
      } .otherwise {
        cmd.ready := inst_q.io.enq.ready && state === s_busy
      }
    }

    // instructions which match our managed opcode
    inst_q.io.enq.valid := cmd.valid && !OpcodeSet.custom0.matches(cmd.bits.inst.opcode) && state === s_busy
    inst_q.io.enq.bits  := cmd.bits
    when (inst_q.io.enq.fire() && cmd.bits.inst.xd) { incr_inflight_wbs := true.B }

    // fast acquire-denys
    resp_arb.io.in(0).bits.rd := cmd.bits.inst.rd
    resp_arb.io.in(0).bits.data := 0.U

    // try acquiring a rerocc tile
    val acquire_beats = RegInit(0.U(2.W))
    req_arb.io.in(0).valid := state === s_acquiring
    req_arb.io.in(0).bits.opcode := ReRoCCProtocolOpcodes.mAcquire
    req_arb.io.in(0).bits.client_id := 0.U
    req_arb.io.in(0).bits.manager_id := PriorityEncoder(manager)
    req_arb.io.in(0).bits.data := MuxLookup(acquire_beats, 0.U, Seq(
      (0.U -> status.asUInt),
      (1.U -> (status.asUInt >> 64)),
      (2.U -> ptbr.asUInt)))
    req_arb.io.in(0).bits.last := acquire_beats === 2.U
    when (req_arb.io.in(0).fire()) {
      acquire_beats := Mux(acquire_beats === 2.U, 0.U, acquire_beats + 1.U)
      when (acquire_beats === 2.U) {
        manager := manager & ~PriorityEncoderOH(manager)
        state := s_acq_wait
      }
    }

    // send instructions to rerocc tile
    val inst_sender = Module(new InstructionSender(edge.bundle))
    req_arb.io.in(1) <> inst_sender.io.rr
    req_arb.io.in(1).bits.manager_id := OHToUInt(manager)
    val tag_free = inst_ctr =/= inst_ret_ctr
    inst_sender.io.cmd.valid := inst_q.io.deq.valid && tag_free
    inst_q.io.deq.ready := inst_sender.io.cmd.ready && tag_free
    inst_sender.io.cmd.bits := inst_q.io.deq.bits
    inst_sender.io.tag := inst_ctr
    inst_sender.io.send_mstatus := inst_q.io.deq.bits.status.asUInt =/= status.asUInt
    when (inst_q.io.deq.fire()) {
      status := inst_q.io.deq.bits.status
      inst_ctr := Mux(inst_ctr === max_ctr, 0.U, inst_ctr + 1.U)
    }

    // Release rerocc tile
    req_arb.io.in(2).valid := state === s_releasing
    req_arb.io.in(2).bits.opcode := ReRoCCProtocolOpcodes.mRelease
    req_arb.io.in(2).bits.client_id := 0.U
    req_arb.io.in(2).bits.manager_id := OHToUInt(manager)
    req_arb.io.in(2).bits.data := 0.U
    req_arb.io.in(2).bits.last := true.B
    when (req_arb.io.in(2).fire()) { state := s_rel_wait }

    // unbusy rerocc tile
    req_arb.io.in(3).valid := state === s_unbusying
    req_arb.io.in(3).bits.opcode := ReRoCCProtocolOpcodes.mUnbusy
    req_arb.io.in(3).bits.client_id := 0.U
    req_arb.io.in(3).bits.manager_id := OHToUInt(manager)
    req_arb.io.in(3).bits.data := 0.U
    req_arb.io.in(3).bits.last := true.B
    when (req_arb.io.in(3).fire()) { state := s_unbusy_wait }


    // ReRoCC responses
    rerocc.resp.ready := false.B
    resp_arb.io.in(1).bits.rd := acq_rd
    resp_arb.io.in(1).bits.data := 0.U
    val wbdata = Reg(UInt(64.W))
    resp_arb.io.in(2).bits.data := wbdata
    resp_arb.io.in(2).bits.rd := rerocc.resp.bits.data

    when (rerocc.resp.valid) {
      when (rerocc.resp.bits.opcode === ReRoCCProtocolOpcodes.sAcqResp) {
        assert(state === s_acq_wait)
        when (rerocc.resp.bits.data(0)) { // ack
          rerocc.resp.ready := resp_arb.io.in(1).ready
          resp_arb.io.in(1).valid := true.B
          resp_arb.io.in(1).bits.data := UIntToOH(rerocc.resp.bits.manager_id)
          when (resp_arb.io.in(1).ready) {
            state := s_busy
            max_ctr := (rerocc.resp.bits.data >> 1)(instCtrSz-1,0) - 1.U
            inst_ctr := 0.U
            inst_ret_ctr := (rerocc.resp.bits.data >> 1)(instCtrSz-1,0) - 1.U
            manager := UIntToOH(rerocc.resp.bits.manager_id)
          }
        } .otherwise { // nack
          when (manager === 0.U) {
            rerocc.resp.ready := resp_arb.io.in(1).ready
            resp_arb.io.in(1).valid := true.B
            when (resp_arb.io.in(1).ready) { state := s_idle }
          } .otherwise {
            rerocc.resp.ready := true.B
            state := s_acquiring
          }
        }
      } .elsewhen (rerocc.resp.bits.opcode === ReRoCCProtocolOpcodes.sInstAck) {
        rerocc.resp.ready := true.B
        inst_ret_ctr := Mux(inst_ret_ctr === max_ctr, 0.U, inst_ret_ctr + 1.U)
      } .elsewhen (rerocc.resp.bits.opcode === ReRoCCProtocolOpcodes.sWrite) {
        rerocc.resp.ready := resp_arb.io.in(2).ready || !rerocc.resp.bits.last
        when (!rerocc.resp.bits.last) {
          wbdata := rerocc.resp.bits.data
          decr_inflight_wbs := true.B
        }
        resp_arb.io.in(2).valid := rerocc.resp.valid && rerocc.resp.bits.last
      } .elsewhen (rerocc.resp.bits.opcode === ReRoCCProtocolOpcodes.sRelResp) {
        rerocc.resp.ready := true.B
        state := s_idle
      } .elsewhen (rerocc.resp.bits.opcode === ReRoCCProtocolOpcodes.sUnbusyAck) {
        rerocc.resp.ready := true.B
        fencing := false.B
        state := s_busy
      } .otherwise {
        assert(false.B)
      }
    }
  }
}


class ReRoCCClient(nTrackers: Int = 16)(implicit p: Parameters) extends LazyRoCC(OpcodeSet.all, 2) {
  val reRoCCXbar = LazyModule(new ReRoCCXbar())
  val subclients = Seq.fill(nTrackers) { LazyModule(new ReRoCCSingleOpcodeClient) }

  subclients.foreach { s => reRoCCXbar.node := s.reRoCCNode }
  val reRoCCNode = reRoCCXbar.node
  val cflush = LazyModule(new ReRoCCCacheFlusher)

  override val tlNode = cflush.node

  override lazy val module = new LazyRoCCModuleImp(this) {
    val resp_arb = Module(new Arbiter(new RoCCResponse, 1+nTrackers))
    val opcode_trackers = Reg(Vec(3, UInt(log2Ceil(nTrackers).W)))

    val fencing = RegInit(false.B)
    val fencing_mask = Reg(UInt(nTrackers.W))
    io.busy := fencing

    when (fencing) {
      val any_busy = (0 until nTrackers).map { i => subclients(i).module.io.fencing && fencing_mask(i) }.orR
      when (!any_busy && !cflush.module.io.busy) { fencing := false.B }
    }

    cflush.module.io.ptw <> io.ptw(1)

    when (io.cmd.fire()
      && OpcodeSet.custom0.matches(io.cmd.bits.inst.opcode)
      && io.cmd.bits.inst.funct === ReRoCCInstructions.fence) {
      fencing := true.B
      fencing_mask := UIntToOH(io.cmd.bits.rs1(log2Ceil(nTrackers)-1,0))
    }

    val cmd = Queue(io.cmd)
    io.resp <> Queue(resp_arb.io.out)

    resp_arb.io.in(nTrackers).valid := false.B
    resp_arb.io.in(nTrackers).bits := DontCare
    cmd.ready := false.B
    val funct = cmd.bits.inst.funct
    val opcode = cmd.bits.inst.opcode
    val custom0 = OpcodeSet.custom0.matches(opcode)
    val cmd_acquire = funct === ReRoCCInstructions.acquire
    val cmd_release = funct === ReRoCCInstructions.release
    val cmd_assign  = funct === ReRoCCInstructions.assign
    val cmd_info    = funct === ReRoCCInstructions.info
    val cmd_fence   = funct === ReRoCCInstructions.fence
    val cmd_cflush  = funct === ReRoCCInstructions.cflush
    val cmd_tracker_sel = WireInit(0.U(nTrackers.W))

    cmd.ready := Mux1H(cmd_tracker_sel, subclients.map(_.module.io.cmd.ready))

    for (i <- 0 until nTrackers) {
      val subclient = subclients(i).module
      resp_arb.io.in(i) <> subclient.io.resp
      subclient.io.ptbr := io.ptw(0).ptbr
      subclient.io.cmd.valid := cmd_tracker_sel(i) && cmd.valid
      subclient.io.cmd.bits := cmd.bits
    }

    cflush.module.io.cmd.valid := false.B
    cflush.module.io.cmd.bits := cmd.bits
    when (custom0) {
      val tracker = cmd.bits.rs1
      when (cmd_assign) {
        when (cmd.fire()) { opcode_trackers(cmd.bits.rs2 - 1.U) := tracker }
        cmd.ready := true.B
      } .elsewhen (cmd_info) {
        cmd.ready := resp_arb.io.in(nTrackers).ready
        resp_arb.io.in(nTrackers).valid := cmd.valid
        resp_arb.io.in(nTrackers).bits.data := nTrackers.U
        resp_arb.io.in(nTrackers).bits.rd := cmd.bits.inst.rd
      } .elsewhen (cmd_acquire || cmd_release) {
        assert(!cmd.valid || tracker < nTrackers.U)
        cmd_tracker_sel := UIntToOH(tracker(log2Ceil(nTrackers)-1,0))
      } .elsewhen (cmd_fence) {
        cmd_tracker_sel := UIntToOH(tracker(log2Ceil(nTrackers)-1,0))
      } .elsewhen (cmd_cflush) {
        cmd.ready := cflush.module.io.cmd.ready
        cflush.module.io.cmd.valid := cmd.valid
      } .otherwise {
        assert(!cmd.valid)
      }
    } .otherwise {
      val opcodes = Seq(OpcodeSet.custom1, OpcodeSet.custom2, OpcodeSet.custom3)
      for (i <- 0 until 3) {
        when (opcodes(i).matches(opcode)) { cmd_tracker_sel := UIntToOH(opcode_trackers(i)) }
      }
    }
  }

}
