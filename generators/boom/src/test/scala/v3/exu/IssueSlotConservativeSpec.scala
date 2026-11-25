// [boom-ustt] Module tests for the conservative stand-in issue gate (#6).

package boom.v3.exu

import chisel3._
import chisel3.util.Valid
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec

import chisel3.experimental.UnlocatableSourceInfo
import org.chipsalliance.cde.config.{Config, Parameters}
import org.chipsalliance.diplomacy.ValName
import org.chipsalliance.diplomacy.nodes.Edges
import freechips.rocketchip.diplomacy.{AddressSet, TransferSizes}
import freechips.rocketchip.subsystem.{InSubsystem, TilesLocated}
import freechips.rocketchip.tile.{TileKey, TileVisibilityNodeKey}
import freechips.rocketchip.tilelink._

import boom.v3.common._

object ConservativeSlotParams
{
  /** A visibility node with one resolved 32-bit edge; only its bundle widths (paddrBits) are read. */
  def visibilityNode(p: Parameters): TLEphemeralNode = {
    val edge = new TLEdgeOut(
      TLMasterPortParameters.v1(Seq(TLMasterParameters.v1("test"))),
      TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(Seq(AddressSet(0, 0xffffffffL)), supportsGet = TransferSizes(1, 64))), beatBytes = 8),
      p, UnlocatableSourceInfo)
    new TLEphemeralNode()(ValName("test_visibility")) {
      override lazy val edges = Edges(Nil, Seq(edge))
    }
  }

  /** Small BOOM core parameters with the given switch, as BoomTile would see them. */
  def apply(conservative: Boolean): Parameters = {
    val base: Parameters = new Config(
      (if (conservative) new WithConservativeSTT else Parameters.empty) ++
      new WithNSmallBooms(1) ++
      new freechips.rocketchip.system.BaseConfig)
    val tile = base(TilesLocated(InSubsystem)).collect { case b: BoomTileAttachParams => b.tileParams }.head
    base.alterMap(Map(TileKey -> tile, TileVisibilityNodeKey -> visibilityNode(base)))
  }
}

/**
 * One IssueSlot with only the ports these tests drive; the rest are tied idle. b2's target_offset has
 * no width of its own (the core sizes it), so it cannot be a top-level port.
 */
class IssueSlotHarness(implicit p: Parameters) extends Module
{
  val slot = Module(new IssueSlot(numWakeupPorts = 1))
  val io = IO(new Bundle {
    val in_uop     = Input(Valid(new MicroOp))
    val grant      = Input(Bool())
    val stt_block  = Input(Bool())
    val b1         = Input(chiselTypeOf(slot.io.brupdate.b1))
    val valid      = Output(Bool())
    val request    = Output(Bool())
    val request_hp = Output(Bool())
  })
  slot.io.in_uop      := io.in_uop
  slot.io.grant       := io.grant
  slot.io.stt_block.get := io.stt_block
  slot.io.kill        := false.B
  slot.io.clear       := false.B
  slot.io.ldspec_miss := false.B
  slot.io.brupdate    := DontCare
  slot.io.brupdate.b1 := io.b1
  slot.io.brupdate.b2.mispredict    := false.B
  slot.io.brupdate.b2.target_offset := 0.S(16.W)
  slot.io.wakeup_ports.foreach(_.valid := false.B)
  slot.io.wakeup_ports.foreach(_.bits := DontCare)
  slot.io.pred_wakeup_port.valid := false.B
  slot.io.pred_wakeup_port.bits  := DontCare
  slot.io.spec_ld_wakeup.foreach(_.valid := false.B)
  slot.io.spec_ld_wakeup.foreach(_.bits := DontCare)
  io.valid      := slot.io.valid
  io.request    := slot.io.request
  io.request_hp := slot.io.request_hp
}

class IssueSlotConservativeSpec extends AnyFunSpec with IssueUnitConstants
{
  implicit val p: Parameters = ConservativeSlotParams(conservative = true)

  def dut = new IssueSlotHarness

  def idle(c: IssueSlotHarness): Unit = {
    c.io.in_uop.valid.poke(false.B)
    c.io.grant.poke(false.B)
    c.io.stt_block.poke(false.B)
    c.io.b1.resolve_mask.poke(0.U)
    c.io.b1.mispredict_mask.poke(0.U)
  }

  def reset(c: IssueSlotHarness): Unit = {
    idle(c)
    c.reset.poke(true.B)
    c.clock.step()
    c.reset.poke(false.B)
  }

  /** Write one operand-ready uop into the empty slot. */
  def insert(c: IssueSlotHarness, brMask: Int, transmitter: Boolean, isLoad: Boolean = false, isBr: Boolean = false,
             isStore: Boolean = false, dataBusy: Boolean = false): Unit = {
    val u = c.io.in_uop.bits
    u.uopc.poke(if (isStore) uopSTA else if (isLoad) uopLD else if (isBr) uopBEQ else uopADD)
    u.iw_state.poke(if (isStore) s_valid_2 else s_valid_1)
    u.br_mask.poke(brMask.U)
    u.is_transmitter.get.poke(transmitter.B)
    u.uses_ldq.poke(isLoad.B)
    u.uses_stq.poke(isStore.B)
    u.is_br.poke(isBr.B)
    u.is_jal.poke(false.B)
    u.is_jalr.poke(false.B)
    u.prs1.poke(1.U)
    u.prs2.poke(2.U)
    u.prs3.poke(3.U)
    u.ppred.poke(0.U)
    u.lrs1_rtype.poke(RT_FIX)
    u.lrs2_rtype.poke(if (isStore) RT_FIX else RT_X)
    u.prs1_busy.poke(false.B)
    u.prs2_busy.poke(dataBusy.B)
    u.prs3_busy.poke(false.B)
    u.ppred_busy.poke(false.B)
    u.iw_p1_poisoned.poke(false.B)
    u.iw_p2_poisoned.poke(false.B)
    c.io.in_uop.valid.poke(true.B)
    c.clock.step()
    c.io.in_uop.valid.poke(false.B)
  }

  def expectRequest(c: IssueSlotHarness, req: Boolean, msg: String): Unit = {
    c.io.valid.expect(true.B, msg)
    c.io.request.expect(req.B, msg)
    if (!req) c.io.request_hp.expect(false.B, msg)
  }

  describe("IssueSlot with conservativeSTT") {
    it("holds a ready load under an unresolved branch and releases it in the resolving cycle") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0x4, transmitter = true, isLoad = true)
        expectRequest(c, false, "load under branch 2")
        c.clock.step()
        expectRequest(c, false, "load still under branch 2")
        c.io.b1.resolve_mask.poke(0x4.U)
        expectRequest(c, true, "branch 2 resolves this cycle")
        c.clock.step()
        c.io.b1.resolve_mask.poke(0.U)
        expectRequest(c, true, "branch 2 resolved")
      }
    }

    it("waits for every pending branch, not only one") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0x6, transmitter = true, isLoad = true)
        c.io.b1.resolve_mask.poke(0x2.U)
        expectRequest(c, false, "branch 1 resolves, branch 2 pending")
        c.clock.step()
        c.io.b1.resolve_mask.poke(0.U)
        expectRequest(c, false, "branch 2 pending")
        c.io.b1.resolve_mask.poke(0x4.U)
        expectRequest(c, true, "branch 2 resolves")
      }
    }

    it("issues a ready load with no older branch") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0, transmitter = true, isLoad = true)
        expectRequest(c, true, "load, br_mask = 0")
      }
    }

    it("does not delay a non-transmitter under a branch") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0x1, transmitter = false)
        expectRequest(c, true, "ALU op under branch 0")
      }
    }

    it("blocks a transmitter with no older branch while stt_block is high") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0, transmitter = true, isLoad = true)
        c.io.stt_block.poke(true.B)
        expectRequest(c, false, "stt_block")
        c.io.stt_block.poke(false.B)
        expectRequest(c, true, "stt_block released")
      }
    }

    it("lets the oldest branch issue (br_mask = 0) with request_hp, and holds a younger one") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0, transmitter = true, isBr = true)
        expectRequest(c, true, "oldest branch")
        c.io.request_hp.expect(true.B, "oldest branch is high priority")
      }
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0x1, transmitter = true, isBr = true)
        expectRequest(c, false, "younger branch")
      }
    }

    it("holds both halves of a split store under a branch") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0x8, transmitter = true, isStore = true)
        expectRequest(c, false, "STA+STD under branch 3")
        c.io.b1.resolve_mask.poke(0x8.U)
        expectRequest(c, true, "branch 3 resolves")
      }
      // Address ready, data not: only the address half could fire, and it must wait too.
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0x8, transmitter = true, isStore = true, dataBusy = true)
        expectRequest(c, false, "STA half under branch 3")
      }
    }

    it("leaves a granted transmitter's ordinary issue path unchanged once safe") {
      simulate(dut) { c =>
        reset(c)
        insert(c, brMask = 0, transmitter = true, isLoad = true)
        expectRequest(c, true, "safe load")
        c.io.grant.poke(true.B)
        c.clock.step()
        c.io.grant.poke(false.B)
        c.io.valid.expect(false.B, "slot vacated after grant")
      }
    }
  }
}

class IssueSlotSwitchOffSpec extends AnyFunSpec
{
  it("IssueSlot has no stt_block port with every switch off") {
    implicit val p: Parameters = ConservativeSlotParams(conservative = false)
    var hasPort = true
    circt.stage.ChiselStage.emitCHIRRTL(new Module {
      val slot = Module(new IssueSlot(numWakeupPorts = 1))
      hasPort = slot.io.stt_block.isDefined
      slot.io := DontCare
    })
    assert(!hasPort)
  }
}
