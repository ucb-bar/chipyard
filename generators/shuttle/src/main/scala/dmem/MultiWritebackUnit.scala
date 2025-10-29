package shuttle.dmem

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._

class MultiWritebackUnit(n: Int)(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new WritebackReq(edge.bundle)))
    val occupied = Vec(n, Output(Bool()))
    val meta_read = Decoupled(new L1MetaReadReq)
    val data_req = Decoupled(new L1DataReadReq)
    val data_resp = Input(UInt(encRowBits.W))
    val release = Decoupled(new TLBundleC(edge.bundle))
  })
  val wbs = Seq.fill(n) { Module(new WritebackUnit()) }
  (io.occupied zip wbs).map { case (o, wb) => o := !wb.io.req.ready }
  val head = RegInit(1.U(n.W))
  val tail = RegInit(1.U(n.W))

  io.req.ready := Mux1H(tail, wbs.map(_.io.req.ready))
  wbs.foreach(_.io.req.bits := io.req.bits)
  for (i <- 0 until n) { wbs(i).io.req.valid := io.req.valid && tail(i) }
  when (io.req.fire) { tail := tail << 1 | tail(n-1) }

  io.data_req.valid := Mux1H(head, wbs.map(_.io.data_req.valid))
  io.data_req.bits := Mux1H(head, wbs.map(_.io.data_req.bits))
  for (i <- 0 until n) { wbs(i).io.data_req.ready := head(i) && io.data_req.ready }
  when (Mux1H(head, wbs.map(w => edge.done(w.io.release)))) { head := head << 1 | head(n-1) }

  io.meta_read.valid := io.data_req.valid
  io.meta_read.bits := Mux1H(wbs.map(_.io.data_req.ready), wbs.map(_.io.meta_read.bits))
  wbs.foreach(_.io.meta_read.ready := true.B)
  wbs.foreach(_.io.data_resp := io.data_resp)
  io.release.valid := wbs.map(_.io.release.valid).reduce(_||_)
  io.release.bits := Mux1H(wbs.map(_.io.release.valid), wbs.map(_.io.release.bits))
  wbs.foreach(_.io.release.ready := io.release.ready)
}
