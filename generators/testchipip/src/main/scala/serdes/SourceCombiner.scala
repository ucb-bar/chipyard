package testchipip.serdes

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

// This combines all the non-caching clients into a single client, effectively
// a more focused sourceShrinker. maxInFlight is the max in-flight requests for this
// new client
class TLSourceCombiner(maxInFlight: Int)(implicit p: Parameters) extends LazyModule
{
  val node = TLAdapterNode(
    clientFn  = { cp => {
      val uncacheClients = cp.clients.filter(_.supports.probe.none)
      val cacheClients = cp.clients.filter(!_.supports.probe.none)
      val sourceOffset = 1 << log2Ceil(cp.endSourceId)
      val squashed = TLMasterParameters.v1(
        name = "TLSourceCombiner",
        sourceId = IdRange(sourceOffset, sourceOffset + maxInFlight),
        requestFifo = uncacheClients.exists(_.requestFifo))
      TLMasterPortParameters.v1(
        clients = cacheClients :+ squashed,
        echoFields = cp.echoFields,
        requestFields = cp.requestFields,
        responseKeys = cp.responseKeys)
    }},
    managerFn = {mp => mp}
  )
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    node.in.zip(node.out).foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val uncacheClients = edgeIn.client.clients.filter(_.supports.probe.none)
      val sourceOffset = 1 << log2Ceil(edgeIn.client.endSourceId)

      // State tracking
      val sourceIdMap = Mem(maxInFlight, UInt(edgeIn.bundle.sourceBits.W))
      val allocated = RegInit(0.U(maxInFlight.W))
      val nextFreeOH = ~(leftOR(~allocated) << 1) & ~allocated
      val nextFree = OHToUInt(nextFreeOH)
      val full = allocated.andR

      val a_first = edgeIn.first(in.a)
      val d_last  = edgeIn.last(in.d)
      val a_shrink = uncacheClients.map(_.sourceId.contains(in.a.bits.source)).reduce(_||_)

      val block = a_first && full && a_shrink
      in.a.ready := out.a.ready && !block
      out.a.valid := in.a.valid && !block
      out.a.bits := in.a.bits
      out.a.bits.source := Mux(a_shrink, (nextFree holdUnless a_first) + sourceOffset.U, in.a.bits.source)

      val d_shrunk = out.d.bits.source >= sourceOffset.U

      in.d <> out.d
      in.d.bits.source := Mux(d_shrunk, sourceIdMap(out.d.bits.source - sourceOffset.U), out.d.bits.source)

      when (a_first && in.a.fire && a_shrink) {
        sourceIdMap(nextFree) := in.a.bits.source
      }

      val alloc = a_first && in.a.fire && a_shrink
      val free = d_last && in.d.fire && d_shrunk
      val alloc_id = Mux(alloc, nextFreeOH, 0.U)
      val free_id = Mux(free, UIntToOH(out.d.bits.source - sourceOffset.U), 0.U)
      allocated := (allocated | alloc_id) & ~free_id

      // b/c should only be from caching clients, so nothing needs to be done
      in.b <> out.b
      out.c <> in.c
      out.e <> in.e
      dontTouch(in.b)
      println("comb")
      println(edgeIn.bundle.hasBCE)
      println(edgeOut.bundle.hasBCE)
    }
  }
}

object TLSourceCombiner
{
  def apply(maxClients: Int)(implicit p: Parameters): TLNode =
  {
    val combiner = LazyModule(new TLSourceCombiner(maxClients))
    combiner.node
  }
}
