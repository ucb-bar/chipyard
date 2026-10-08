// See LICENSE for license details.
package firechip.goldengateimplementations

import chisel3._
import chisel3.util._
import firechip.bridgeinterfaces._

/** One frozen packet, continuously valid from its first beat through its last. */
class IntervalPacketSerializer(packetBits: Int) extends Module {
  require(packetBits >= 512 && packetBits % 512 == 0)
  private val beats = packetBits / 512
  val io = IO(new Bundle {
    val packet = Flipped(Decoupled(UInt(packetBits.W)))
    val beat = Decoupled(UInt(512.W))
    val busy = Output(Bool())
  })
  val packet_data = Reg(UInt(packetBits.W))
  val active = RegInit(false.B)
  val beat_index = RegInit(0.U(math.max(1, log2Ceil(beats)).W))
  io.packet.ready := !active
  io.beat.valid := active
  io.beat.bits := (packet_data >> (beat_index << 9))(511, 0)
  io.busy := active
  when(io.packet.fire) {
    packet_data := io.packet.bits
    beat_index := 0.U
    active := true.B
  }
  when(io.beat.fire) {
    when(beat_index === (beats - 1).U) { active := false.B }
      .otherwise { beat_index := beat_index + 1.U }
  }
}

/** Increments are unsized: width inference sizes them from the target. */
class IntervalTargetCycle(key: IntervalTraceBridgeKey) extends Bundle {
  val target_reset = Bool()
  val trace = new TraceBundle(key.traceWidths)
  val increments = MixedVec(key.events.map(_ => UInt()))
}

/** Match current PMU samples to a producer's delayed retirement observations.
  * The delay advances only on accepted target cycles, never on FPGA cycles.
  * Delaying reset too preserves the final retirement before a target reset.
  */
class IntervalTraceAligner(key: IntervalTraceBridgeKey) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new IntervalTargetCycle(key)))
    val out = Decoupled(new IntervalTargetCycle(key))
  })
  val sample = Wire(Decoupled(new IntervalTargetCycle(key)))
  sample <> io.in
  // HostPort uses a latency-one PipeChannel, which injects a zero token
  // during FPGA reset. It is transport initialization, not a DUT sample.
  val primed = RegInit(false.B)
  sample.valid := io.in.valid && primed
  io.in.ready := !primed || sample.ready
  when(io.in.fire) { primed := true.B }
  if (key.retirementLatency == 0) {
    io.out <> sample
  } else {
    val filled = RegInit(false.B)
    val previous_increments = Reg(chiselTypeOf(sample.bits.increments))
    val previous_reset = Reg(Bool())
    io.out.bits.trace := sample.bits.trace
    io.out.bits.increments := previous_increments
    io.out.bits.target_reset := previous_reset
    io.out.valid := sample.valid && filled
    sample.ready := !filled || io.out.ready
    when(sample.fire) {
      filled := true.B
      previous_increments := sample.bits.increments
      previous_reset := sample.bits.target_reset
    }
  }
}

class IntervalBBState extends Bundle {
  val start_pc = UInt(64.W)
  val last_pc = UInt(64.W)
  val next_pc = UInt(64.W)
  val instructions = UInt(64.W)
  val last_boundary_cycle = UInt(64.W)
  val retired = UInt(64.W)
  // count + carried drift: subtract nominal length once per completed block.
  val progress = UInt(64.W)
  val snapshot_id = UInt(64.W)
  val privilege = UInt(3.W)
  val have_privilege = Bool()
  val partial_start = Bool()
}

/** FPGA-clocked interval engine, independent of the Golden Gate MMIO wrapper.
  * One accepted target cycle produces a bounded batch. No later target cycle
  * enters until every record in that batch has reached the common output FIFO.
  */
class IntervalTraceDatapath(key: IntervalTraceBridgeKey) extends Module {
  import IntervalTraceFormat._
  require(key.traceWidths.causeWidth == 64, "Interval trace initially requires an RV64 trace producer")
  private val lanes = key.traceWidths.retireWidth
  private val bbSlots = 2 * lanes + 1 // discontinuity before + terminator after each lane; final trap
  private val pmuSlots = lanes max 1
  private val packetBits = pmuBeats(key.events.size) * beatBits
  val io = IO(new Bundle {
    val target = Flipped(Decoupled(new IntervalTargetCycle(key)))
    val capture_enable = Input(Bool())
    // TracerV selector values: 0 always, 1 cycle window, 2 PCs, 3 masked instructions.
    // Instruction endpoints pack a 32-bit mask above a 32-bit instruction value.
    val trigger_selector = Input(UInt(32.W))
    val trigger_start = Input(UInt(64.W))
    val trigger_end = Input(UInt(64.W))
    val nominal_interval = Input(UInt(64.W))
    val stream = Decoupled(UInt(beatBits.W))
    val active = Output(Bool())
    val drained = Output(Bool())
    val config_error = Output(Bool())
  })

  def header(kind: Int, flags: UInt, payloadWords: Int): UInt =
    Cat(payloadWords.U(32.W), flags.pad(16)(15, 0), version.U(8.W), kind.U(8.W))
  def record(kind: Int, flags: UInt, payload: Seq[UInt], width: Int = beatBits): UInt =
    Cat((Seq(header(kind, flags, payload.size)) ++ payload.map(_.pad(64)(63, 0))).reverse).pad(width)
  def bbRecord(s: IntervalBBState, end_pc: UInt, count: UInt, cycle_end: UInt, flags: UInt): UInt =
    record(bb, flags | (s.privilege.pad(16) << privilegeShift) |
      Mux(s.partial_start, partialStart.U(16.W), 0.U(16.W)),
      Seq(s.start_pc, end_pc, count, cycle_end - s.last_boundary_cycle))
  def endsBlock(insn: UInt): Bool = {
    val compressed = insn(1, 0) =/= 3.U
    val c_jump_branch = insn(1, 0) === 1.U &&
      (insn(15, 13) === 5.U || insn(15, 13) === 6.U || insn(15, 13) === 7.U)
    val c_register_jump = insn(1, 0) === 2.U && insn(15, 13) === 4.U &&
      insn(11, 7) =/= 0.U && insn(6, 2) === 0.U
    val branch = insn(6, 0) === "h63".U && insn(14, 12) =/= 2.U && insn(14, 12) =/= 3.U
    val jump = insn(6, 0) === "h6f".U || (insn(6, 0) === "h67".U && insn(14, 12) === 0.U)
    val trap_return = Seq("h00200073", "h10200073", "h30200073", "h7b200073").map(insn === _.U).reduce(_ || _)
    Mux(compressed, c_jump_branch || c_register_jump, branch || jump || trap_return)
  }
  def signExtend(pc: UInt): UInt = pc.asSInt.pad(64).asUInt

  val active = RegInit(false.B)
  val trigger_stop_pending = RegInit(false.B)
  val epoch = RegInit(0.U(64.W))
  val nominal_interval = Reg(UInt(64.W))
  val cycles = RegInit(0.U(64.W))
  val counters = RegInit(VecInit(Seq.fill(key.events.size)(0.U(64.W))))
  val state = RegInit(0.U.asTypeOf(new IntervalBBState))

  val bb_mask = RegInit(0.U(bbSlots.W))
  val bb_records = Reg(Vec(bbSlots, UInt(beatBits.W)))
  val pmu_mask = RegInit(0.U(pmuSlots.W))
  val snapshot_ids = Reg(Vec(pmuSlots, UInt(64.W)))
  val boundary_retired = Reg(Vec(pmuSlots, UInt(64.W)))
  val pmu_flags = Reg(Vec(pmuSlots, UInt(16.W)))
  val frozen_counters = Reg(Vec(key.events.size, UInt(64.W)))
  val frozen_cycle = Reg(UInt(64.W))
  val frozen_retired = Reg(UInt(64.W))
  val start_pending = RegInit(false.B)
  val end_pending = RegInit(false.B)
  val end_flags = Reg(UInt(16.W))

  val serializer = Module(new IntervalPacketSerializer(packetBits))
  val arbiter = Module(new Arbiter(UInt(beatBits.W), 2))
  val output_queue = Module(new Queue(UInt(beatBits.W), 2))
  io.stream <> output_queue.io.deq
  val pmu_index = PriorityEncoder(pmu_mask)
  val bb_index = PriorityEncoder(bb_mask)
  serializer.io.packet.valid := pmu_mask.orR && !start_pending
  serializer.io.packet.bits := record(pmu, pmu_flags(pmu_index),
    Seq(snapshot_ids(pmu_index), boundary_retired(pmu_index), frozen_cycle, frozen_retired) ++ frozen_counters, packetBits)
  when(serializer.io.packet.fire) { pmu_mask := pmu_mask & ~UIntToOH(pmu_index, pmuSlots) }
  arbiter.io.in(0) <> serializer.io.beat
  // Also wait for packet loading, when the serializer is not yet asserting valid.
  arbiter.io.in(1).valid := bb_mask.orR && !pmu_mask.orR && !start_pending
  arbiter.io.in(1).bits := bb_records(bb_index)
  when(arbiter.io.in(1).fire) { bb_mask := bb_mask & ~UIntToOH(bb_index, bbSlots) }

  val data_pending = bb_mask.orR || pmu_mask.orR || serializer.io.busy
  val emit_end = end_pending && !data_pending
  output_queue.io.enq.valid := start_pending || arbiter.io.out.valid || emit_end
  output_queue.io.enq.bits := Mux(start_pending,
    record(epochStart, 0.U, Seq(epoch, nominal_interval, key.events.size.U(64.W), catalogId(key).U(64.W))),
    Mux(emit_end, record(epochEnd, end_flags, Seq(epoch, state.retired, cycles, state.snapshot_id)), arbiter.io.out.bits))
  arbiter.io.out.ready := output_queue.io.enq.ready && !start_pending && !emit_end
  when(output_queue.io.enq.fire && start_pending) { start_pending := false.B }
  when(output_queue.io.enq.fire && emit_end) { end_pending := false.B }

  val batch_idle = !data_pending && !start_pending && !end_pending
  // Counts aligned target observations from FPGA initialization, including reset
  // tokens and idle cycles. Host stalls do not advance it; target reset does not
  // restart the window. Saturation prevents a wrapped window from rearming.
  val trigger_cycle = RegInit(0.U(64.W))
  when(io.target.fire && !trigger_cycle.andR) { trigger_cycle := trigger_cycle + 1.U }
  val window_exhausted = RegInit(false.B)
  when(io.target.fire && io.trigger_selector === 1.U && trigger_cycle >= io.trigger_end) {
    window_exhausted := true.B
  }
  val cycle_window = !window_exhausted && trigger_cycle >= io.trigger_start && trigger_cycle <= io.trigger_end
  def matches(t: TracedInstruction, endpoint: UInt): Bool = {
    val pc_match = signExtend(t.iaddr) === endpoint
    val insn_match = ((t.insn ^ endpoint(31, 0)) & endpoint(63, 32)) === 0.U
    t.valid && Mux(io.trigger_selector === 2.U, pc_match, insn_match)
  }
  val start_matches = io.target.bits.trace.retiredinsns.map(t => matches(t, io.trigger_start))
  val end_matches = io.target.bits.trace.retiredinsns.map(t => matches(t, io.trigger_end))
  val start_match = start_matches.reduce(_ || _)
  // Include complete endpoint cycles: PMU increments cannot be split by lane.
  // Last match in retirement order wins; end wins if both match the same slot.
  val end_match = start_matches.zip(end_matches).foldLeft(false.B) {
    case (stop, (start, end)) => Mux(start || end, end, stop)
  }
  val start_condition = io.trigger_selector === 0.U ||
    (io.trigger_selector === 1.U && cycle_window) ||
    ((io.trigger_selector === 2.U || io.trigger_selector === 3.U) && start_match)
  val stop_after_cycle = Mux(io.trigger_selector === 1.U,
    trigger_cycle >= io.trigger_end, io.trigger_selector >= 2.U && end_match)
  val want_start = !active && io.capture_enable && start_condition &&
    io.target.valid && !io.target.bits.target_reset
  val want_stop = active && (!io.capture_enable || trigger_stop_pending)
  io.target.ready := batch_idle && !want_start && !want_stop
  io.active := active
  io.drained := !active && batch_idle && !output_queue.io.deq.valid
  io.config_error := !active && io.capture_enable && (io.nominal_interval === 0.U ||
    io.trigger_selector > 3.U || (io.trigger_selector === 1.U && io.trigger_end < io.trigger_start))

  when(batch_idle && want_start && !io.config_error) {
    active := true.B
    trigger_stop_pending := false.B
    epoch := epoch + 1.U
    nominal_interval := io.nominal_interval
    cycles := 0.U
    counters.foreach(_ := 0.U)
    state := 0.U.asTypeOf(state)
    state.partial_start := true.B
    start_pending := true.B
    pmu_mask := 1.U
    pmu_flags(0) := baseline.U
    snapshot_ids(0) := 0.U
    boundary_retired(0) := 0.U
    frozen_cycle := 0.U
    frozen_retired := 0.U
    frozen_counters.foreach(_ := 0.U)
  }

  val next_cycle = cycles + 1.U
  val next_counters = VecInit(counters.zip(io.target.bits.increments).map { case (value, increment) => value + increment })
  val prefix = Wire(Vec(lanes + 1, new IntervalBBState))
  prefix(0) := state
  val new_bb_valid = WireInit(VecInit(Seq.fill(bbSlots)(false.B)))
  val new_bb_records = Wire(Vec(bbSlots, UInt(beatBits.W)))
  new_bb_records.foreach(_ := 0.U)
  val new_pmu_valid = WireInit(VecInit(Seq.fill(pmuSlots)(false.B)))
  val new_snapshot_ids = Wire(Vec(pmuSlots, UInt(64.W)))
  val new_boundary_retired = Wire(Vec(pmuSlots, UInt(64.W)))
  new_snapshot_ids.foreach(_ := 0.U)
  new_boundary_retired.foreach(_ := 0.U)

  for (i <- 0 until lanes) {
    val slot = io.target.bits.trace.retiredinsns(i)
    val prior = prefix(i)
    val current = WireDefault(prior)
    val pc = signExtend(slot.iaddr)
    val privilege_changed = prior.have_privilege && prior.privilege =/= slot.priv
    val pc_changed = prior.instructions =/= 0.U && prior.next_pc =/= pc
    val split = slot.valid && prior.instructions =/= 0.U && (privilege_changed || pc_changed)
    when(split) {
      new_bb_valid(2 * i) := true.B
      new_bb_records(2 * i) := bbRecord(prior, prior.last_pc, prior.instructions, next_cycle,
        partialEnd.U | Mux(privilege_changed, privilegeChange.U, discontinuity.U))
      current.instructions := 0.U
      current.last_boundary_cycle := next_cycle
      current.partial_start := true.B
    }
    val retired_state = WireDefault(current)
    when(slot.valid) {
      when(current.instructions === 0.U) { retired_state.start_pc := pc }
      retired_state.privilege := slot.priv
      retired_state.have_privilege := true.B
      retired_state.partial_start := current.partial_start || privilege_changed
      retired_state.last_pc := pc
      retired_state.next_pc := pc + Mux(slot.insn(1, 0) === 3.U, 4.U, 2.U)
      retired_state.instructions := current.instructions + 1.U
      retired_state.retired := current.retired + 1.U
      retired_state.progress := current.progress + 1.U
    }
    prefix(i + 1) := retired_state
    when(slot.valid && endsBlock(slot.insn)) {
      val closes_interval = retired_state.progress >= nominal_interval
      new_bb_valid(2 * i + 1) := true.B
      new_bb_records(2 * i + 1) := bbRecord(retired_state, pc, retired_state.instructions, next_cycle,
        Mux(closes_interval, terminateInterval.U, 0.U))
      prefix(i + 1).instructions := 0.U
      prefix(i + 1).last_boundary_cycle := next_cycle
      prefix(i + 1).partial_start := false.B
      when(closes_interval) {
        prefix(i + 1).progress := retired_state.progress - nominal_interval
        prefix(i + 1).snapshot_id := retired_state.snapshot_id + 1.U
        new_pmu_valid(i) := true.B
        new_snapshot_ids(i) := retired_state.snapshot_id + 1.U
        new_boundary_retired(i) := retired_state.retired
      }
    }
  }

  val trap_observed = io.target.bits.trace.retiredinsns.map(t => t.exception || t.interrupt).reduce(_ || _)
  val final_state = WireDefault(prefix(lanes))
  // BOOM's trap flags describe the cycle, not a retiring slot. Retire the whole
  // group first; flush a partial block and mark the next start as discontinuous.
  when(trap_observed) {
    when(prefix(lanes).instructions =/= 0.U) {
      new_bb_valid(bbSlots - 1) := true.B
      new_bb_records(bbSlots - 1) := bbRecord(prefix(lanes), prefix(lanes).last_pc,
        prefix(lanes).instructions, next_cycle, (partialEnd | trap).U)
      final_state.last_boundary_cycle := next_cycle
    }
    final_state.instructions := 0.U
    final_state.partial_start := true.B
  }

  when(io.target.fire && active && !io.target.bits.target_reset) {
    when(stop_after_cycle) { trigger_stop_pending := true.B }
    cycles := next_cycle
    counters := next_counters
    state := final_state
    bb_mask := new_bb_valid.asUInt
    bb_records := new_bb_records
    pmu_mask := new_pmu_valid.asUInt
    snapshot_ids := new_snapshot_ids
    boundary_retired := new_boundary_retired
    pmu_flags.foreach(_ := 0.U)
    frozen_counters := next_counters
    frozen_cycle := next_cycle
    frozen_retired := final_state.retired
  }

  val reset_stop = io.target.fire && active && io.target.bits.target_reset
  when((batch_idle && want_stop) || reset_stop) {
    active := false.B
    trigger_stop_pending := false.B
    // Stop is an FPGA-side operation: it can flush a tail while target clocks
    // are paused. The reset token itself contributes neither events nor time.
    val reason = Mux(reset_stop, targetReset.U(16.W), 0.U(16.W))
    end_pending := true.B
    end_flags := reason
    pmu_mask := 1.U
    pmu_flags(0) := finalSnapshot.U | reason
    snapshot_ids(0) := state.snapshot_id + 1.U
    boundary_retired(0) := state.retired
    frozen_counters := counters
    frozen_cycle := cycles
    frozen_retired := state.retired
    state.snapshot_id := state.snapshot_id + 1.U
    when(state.instructions =/= 0.U) {
      bb_mask := 1.U
      bb_records(0) := bbRecord(state, state.last_pc, state.instructions, cycles, partialEnd.U | reason)
    }
  }
}
