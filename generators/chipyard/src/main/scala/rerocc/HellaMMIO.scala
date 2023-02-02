package chipyard.rerocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem._

// Converts HellaCache reqs to MMIO
// WARNING: Does not do translation. Must see physical requests
class HellaMMIO(name: String)(implicit p: Parameters) extends LazyModule()(p) {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq(TLMasterParameters.v1(
      name = name,
      sourceId = IdRange(0, 1),
      requestFifo = true
    )),
    minLatency = 1
  )))
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(Flipped(new HellaCacheIO))

    val (tl, edge) = node.out(0)

    val iomshr = Module(new IOMSHR(0)(edge, p))

    io.req.ready := iomshr.io.req.ready
    when (io.req.valid) { assert(io.req.bits.phys) }

    val s1_valid = RegNext(io.req.fire(), false.B)
    val s1_req = RegEnable(io.req.bits, io.req.valid)

    io.s2_nack := false.B
    io.s2_nack_cause_raw := false.B

    val s2_valid = RegNext(s1_valid && !io.s1_kill, false.B)
    val s2_req = RegEnable(s1_req, s1_valid)
    val s2_data = RegEnable(io.s1_data, s1_valid)

    iomshr.io.req.valid := s2_valid && !io.s2_kill
    iomshr.io.req.bits := s2_req
    io.s2_uncached := true.B
    io.s2_paddr := s2_req.addr

    tl.b.ready := true.B
    tl.c.valid := false.B
    tl.c.bits := DontCare
    tl.e.valid := false.B
    tl.e.bits := DontCare

    tl.a <> iomshr.io.mem_access
    iomshr.io.mem_ack.valid := tl.d.valid
    iomshr.io.mem_ack.bits := tl.d.bits
    tl.d.ready := true.B

    io.resp.valid <> iomshr.io.resp.valid
    io.resp.bits := iomshr.io.resp.bits
    iomshr.io.resp.ready := true.B

    io.replay_next := false.B
    io.s2_xcpt := 0.U.asTypeOf(new HellaCacheExceptions)
    io.s2_gpa := DontCare
    io.s2_gpa_is_pte := DontCare
    require(!io.uncached_resp.isDefined)
    io.ordered := true.B
    io.perf := 0.U.asTypeOf(new HellaCachePerfEvents)
    io.clock_enabled := true.B
  }
}
