package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._
import sifive.blocks.inclusivecache._

class Translator(implicit val p: Parameters) extends Module with HasNonDiplomaticTileParameters {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(64.W)))
    val ptw = new TLBPTWIO
    val out = Decoupled(UInt(64.W))
    val busy = Output(Bool())
  })
  val inflight = RegInit(false.B)
  val cmd = Module(new Queue(UInt(64.W), 1, pipe=true))
  cmd.io.enq <> io.in
  val q = Module(new Queue(UInt(64.W), 2))

  io.ptw.req.valid := cmd.io.deq.valid && q.io.enq.ready
  io.ptw.req.bits.valid := true.B
  io.ptw.req.bits.bits.addr := cmd.io.deq.bits
  io.ptw.req.bits.bits.vstage1 := false.B
  io.ptw.req.bits.bits.stage2 := false.B

  cmd.io.deq.ready := io.ptw.resp.valid
  when (io.ptw.req.fire()) { inflight := true.B }
  when (io.ptw.resp.valid) { inflight := false.B }

  q.io.enq.valid := io.ptw.resp.valid && !io.ptw.resp.bits.pf
  q.io.enq.bits := Cat(io.ptw.resp.bits.pte.ppn, cmd.io.deq.bits(pgIdxBits-1,0))

  io.out <> q.io.deq
  io.busy := q.io.deq.valid || cmd.io.deq.valid
}

class ReRoCCCacheFlusher(nTrackers: Int = 8)(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    Seq(TLMasterParameters.v1("cacheflusher", IdRange(0, nTrackers))))))

  override lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val cmd = Flipped(Decoupled(new RoCCCommand))
      val ptw = new TLBPTWIO
      val busy = Output(Bool())
    })
    val (tl_out, edge) = node.out(0)
    val paddrBits = tl_out.a.bits.address.getWidth

    val cmd = Module(new Queue(new RoCCCommand, 1, pipe=true))
    val translator = Module(new Translator)
    translator.io.ptw <> io.ptw
    val translated_arb = Module(new Arbiter(UInt(paddrBits.W), 2))
    val vm_enabled = io.ptw.ptbr.mode(io.ptw.ptbr.mode.getWidth-1) && cmd.io.deq.bits.status.dprv <= PRV.S.U

    translated_arb.io.in(1).valid := cmd.io.deq.valid && !vm_enabled
    translated_arb.io.in(1).bits := cmd.io.deq.bits

    cmd.io.deq.ready := Mux(vm_enabled, translator.io.in.ready, translated_arb.io.in(1).ready)

    translator.io.in.valid := vm_enabled && cmd.io.deq.valid
    translator.io.in.bits := cmd.io.deq.bits

    translated_arb.io.in(0) <> translator.io.out

    val hasL2 = p(InclusiveCacheKey) != null
    tl_out.a.valid := false.B
    tl_out.a.bits := DontCare
    translated_arb.io.out.ready := true.B
    tl_out.d.ready := true.B
    if (hasL2) {
      val trackers = RegInit(0.U(nTrackers.W))
      val alloc = PriorityEncoder(~trackers)
      val (legal, put) = edge.Put(
        fromSource = alloc,
        toAddress = (InclusiveCacheParameters.L2ControlAddress + 0x200).U,
        lgSize = log2Ceil(8).U,
        data = translated_arb.io.out.bits,
        corrupt = false.B)
      tl_out.a.valid := translated_arb.io.out.valid && legal
      tl_out.a.bits := put
      translated_arb.io.out.ready := !legal || tl_out.a.ready


      trackers := (trackers | Mux(tl_out.a.fire(), UIntToOH(alloc), 0.U)) & ~(Mux(tl_out.d.valid,
        UIntToOH(tl_out.d.bits.source), 0.U(nTrackers.W)))
    }
    tl_out.b.ready := false.B
    tl_out.c.valid := false.B
    tl_out.c.bits := DontCare
    tl_out.e.valid := false.B
    tl_out.e.bits := DontCare



    io.busy := cmd.io.deq.valid || translator.io.busy
  }
}
