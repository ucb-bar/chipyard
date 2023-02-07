package chipyard.rerocc

import chisel3._
import chisel3.util._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._

abstract class ReRoCCBus(implicit p: Parameters) extends LazyModule {
  val node = new NexusNode(ReRoCCImp)(
    clients => ReRoCCClientPortParams(clients.map(_.clients).flatten),
    managers => ReRoCCManagerPortParams(managers.map(_.managers).flatten)
  )
}

class ReRoCCBusRemapper(edgesIn: Seq[ReRoCCEdgeParams], edgesOut: Seq[ReRoCCEdgeParams]) {


}

class ReRoCCXbar(implicit p: Parameters) extends ReRoCCBus {

  lazy val module = new LazyModuleImp(this) {
    val (io_in, edgeIn) = node.in.unzip
    val (io_out, edgeOut) = node.out.unzip
    io_in.foreach(i => dontTouch(i))
    io_out.foreach(o => dontTouch(o))
    val nIn = io_in.size
    val nOut = io_out.size

    io_in.foreach(_.req.ready := false.B)
    val bundles = (edgeIn ++ edgeOut).map(_.bundle)
    val wideBundle = bundles.reduce((l,r) => l.union(r))
    println("xbar")
    println(edgeIn.head)
    println(edgeOut.head)

    io_out.zipWithIndex.foreach { case (o, oi) => {
      val out_arb = Module(new HellaPeekingArbiter(
        new ReRoCCMsgBundle(wideBundle), nIn,
        (b: ReRoCCMsgBundle) => b.last,
        Some((b: ReRoCCMsgBundle) => true.B)
      ))
      var client_offset = 0
      out_arb.io.in.zipWithIndex.foreach { case (i, ii) => {
        val sel = edgeOut(oi).mParams.managers.map(_.managerId.U === io_in(ii).req.bits.manager_id).orR
        i.valid := io_in(ii).req.valid && sel
        i.bits := io_in(ii).req.bits
        i.bits.manager_id := io_in(ii).req.bits.manager_id
        i.bits.client_id := io_in(ii).req.bits.client_id +& client_offset.U
        when (sel) {
          io_in(ii).req.ready := i.ready
        }
        client_offset += edgeIn(ii).cParams.clients.size
      }}
      o.req <> out_arb.io.out
    }}

    io_out.foreach(_.resp.ready := false.B)

    var client_offset = 0
    io_in.zipWithIndex.foreach { case (i, ii) => {
      val in_arb = Module(new HellaPeekingArbiter(
        new ReRoCCMsgBundle(wideBundle), nOut,
        (b: ReRoCCMsgBundle) => b.last,
        Some((b: ReRoCCMsgBundle) => true.B)
      ))
      val minClient = client_offset
      val maxClient = client_offset + edgeIn(ii).cParams.clients.size
      in_arb.io.in.zipWithIndex.foreach { case (o, oi) => {
        val sel = (
          io_out(oi).resp.bits.client_id >= minClient.U &&
            io_out(oi).resp.bits.client_id < maxClient.U
        )
        o.valid := io_out(oi).resp.valid && sel
        o.bits := io_out(oi).resp.bits
        o.bits.client_id := io_out(oi).resp.bits.client_id - minClient.U
        o.bits.manager_id := io_out(oi).resp.bits.manager_id
        when (sel) {
          io_out(oi).resp.ready := o.ready
        }
      }}
      i.resp <> in_arb.io.out
      client_offset += edgeIn(ii).cParams.clients.size
    }}
  }
}
