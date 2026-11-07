package boom.v3.common

import chisel3._
import chisel3.util._

/** Combinational observations only: these signals never control the pipeline. */
class PMUDispatchEvents(width: Int) extends Bundle {
  val dispatched = UInt(log2Ceil(width + 1).W)
  val frontend_starved = Bool()
  val backend_blocked = Bool()
  val redirect_blocked = Bool()
  val rob_blocked = Bool()
  val rename_blocked = Bool()
  val issue_queue_blocked = Bool()
  val ldq_blocked = Bool()
  val stq_blocked = Bool()
  val serialization_blocked = Bool()
}

/** Dispatch, ROB-head and MSHR event predicates, independent of BOOM parameters. */
object PMUEventLogic {
  def dispatch(
    valids: Seq[Bool], fires: Seq[Bool],
    rob_blocked: Seq[Bool], rename_blocked: Seq[Bool], issue_queue_blocked: Seq[Bool],
    ldq_blocked: Seq[Bool], stq_blocked: Seq[Bool], serialization_blocked: Seq[Bool],
    other_backend_blocked: Seq[Bool], redirect: Bool, control_stall: Bool,
    empty_capacity: Bool
  ): PMUDispatchEvents = {
    val width = valids.size
    require(width > 0)
    require(Seq(fires, rob_blocked, rename_blocked, issue_queue_blocked,
      ldq_blocked, stq_blocked, serialization_blocked, other_backend_blocked).forall(_.size == width))
    val waiting = valids.zip(fires).map { case (v, f) => v && !f }
    val first_waiting = PriorityEncoderOH(VecInit(waiting).asUInt).asBools
    val active = !redirect && !control_stall
    def blocked(reasons: Seq[Bool]): Bool =
      active && first_waiting.zip(reasons).map { case (v, r) => v && r }.reduce(_ || _)
    val events = Wire(new PMUDispatchEvents(width))
    events.dispatched := PopCount(fires)
    events.frontend_starved := active && !VecInit(valids).asUInt.orR && empty_capacity
    events.redirect_blocked := redirect && !control_stall
    events.rob_blocked := blocked(rob_blocked)
    events.rename_blocked := blocked(rename_blocked)
    events.issue_queue_blocked := blocked(issue_queue_blocked)
    events.ldq_blocked := blocked(ldq_blocked)
    events.stq_blocked := blocked(stq_blocked)
    events.serialization_blocked := blocked(serialization_blocked)
    events.backend_blocked := events.rob_blocked || events.rename_blocked ||
      events.issue_queue_blocked || events.ldq_blocked || events.stq_blocked || blocked(other_backend_blocked)
    events
  }

  def headLoadWaiting(valids: Seq[Bool], busy: Seq[Bool], loads: Seq[Bool],
    exceptions: Seq[Bool], commit_enabled: Bool): Bool = {
    require(valids.nonEmpty && Seq(busy, loads, exceptions).forall(_.size == valids.size))
    val head = PriorityEncoderOH(VecInit(valids).asUInt).asBools
    commit_enabled && head.indices.map(w =>
      head(w) && busy(w) && loads(w) && !exceptions(w)).reduce(_ || _)
  }

  def lineMissAllocation(accepted: Bool, cacheable: Bool, existing_mshr: Bool,
    tag_match: Bool): Bool = accepted && cacheable && !existing_mshr && !tag_match
}
