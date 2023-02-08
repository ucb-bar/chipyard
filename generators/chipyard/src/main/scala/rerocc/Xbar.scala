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

trait HasReRoCCBusRemapper {
  val edgesIn: Seq[ReRoCCEdgeParams]
  val edgesOut: Seq[ReRoCCEdgeParams]

  lazy val clientOffsets = edgesIn.map(_.cParams.clients.size).scanLeft(0)(_+_)

  def isClient(inEdgeId: Int, outClientId: UInt): Bool = {
    clientOffsets(inEdgeId).U <= outClientId && clientOffsets(inEdgeId+1).U > outClientId
  }
  def remapInToOut(inEdgeId: Int, inClientId: UInt): UInt = clientOffsets(inEdgeId).U +& inClientId
  def remapOutToIn(inEdgeId: Int, outClientId: UInt): UInt = outClientId - clientOffsets(inEdgeId).U
}

class ReRoCCXbar(implicit p: Parameters) extends ReRoCCBus {

  lazy val module = new LazyModuleImp(this) with HasReRoCCBusRemapper {
    val (io_in, edgesIn) = node.in.unzip
    val (io_out, edgesOut) = node.out.unzip
    io_in.foreach(i => dontTouch(i))
    io_out.foreach(o => dontTouch(o))
    val nIn = io_in.size
    val nOut = io_out.size

    io_in.foreach(_.req.ready := false.B)
    val bundles = (edgesIn ++ edgesOut).map(_.bundle)
    val wideBundle = bundles.reduce((l,r) => l.union(r))

    io_out.zipWithIndex.foreach { case (o, oi) => {
      val out_arb = Module(new HellaPeekingArbiter(
        new ReRoCCMsgBundle(wideBundle), nIn,
        (b: ReRoCCMsgBundle) => b.last,
        Some((b: ReRoCCMsgBundle) => true.B)
      ))
      out_arb.io.in.zipWithIndex.foreach { case (i, ii) => {
        val sel = edgesOut(oi).mParams.managers.map(_.managerId.U === io_in(ii).req.bits.manager_id).orR
        i.valid := io_in(ii).req.valid && sel
        i.bits := io_in(ii).req.bits
        i.bits.manager_id := io_in(ii).req.bits.manager_id
        i.bits.client_id := remapInToOut(ii, io_in(ii).req.bits.client_id)
        when (sel) {
          io_in(ii).req.ready := i.ready
        }
      }}
      o.req <> out_arb.io.out
    }}

    io_out.foreach(_.resp.ready := false.B)

    io_in.zipWithIndex.foreach { case (i, ii) => {
      val in_arb = Module(new HellaPeekingArbiter(
        new ReRoCCMsgBundle(wideBundle), nOut,
        (b: ReRoCCMsgBundle) => b.last,
        Some((b: ReRoCCMsgBundle) => true.B)
      ))
      val minClient = clientOffsets(ii)
      val maxClient = clientOffsets(ii+1)
      in_arb.io.in.zipWithIndex.foreach { case (o, oi) => {
        val sel = isClient(ii, io_out(oi).resp.bits.client_id)
        o.valid := io_out(oi).resp.valid && sel
        o.bits := io_out(oi).resp.bits
        o.bits.client_id := remapOutToIn(ii, io_out(oi).resp.bits.client_id)
        o.bits.manager_id := io_out(oi).resp.bits.manager_id
        when (sel) {
          io_out(oi).resp.ready := o.ready
        }
      }}
      i.resp <> in_arb.io.out
    }}
  }
}
