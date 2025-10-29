package barf

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

case class TLPrefetcherParams(
  prefetchIds: Int = 4,
  prefetcher: String => Option[CanInstantiatePrefetcher] = _ => None
)

case object TLPrefetcherKey extends Field[TLPrefetcherParams](TLPrefetcherParams())

class TLPrefetcher(implicit p: Parameters) extends LazyModule {
  val params = p(TLPrefetcherKey)

  def mapInputIds(masters: Seq[TLMasterParameters]) = TLXbar.assignRanges(masters.map(_.sourceId.size + params.prefetchIds))

  val node = TLAdapterNode(
    clientFn = { cp =>
      cp.v1copy(clients = (mapInputIds(cp.clients) zip cp.clients).map { case (range, c) => c.v1copy(
        sourceId = range
      )})
    },
    managerFn = { mp => mp }
  )

  // Handle size = 1 gracefully (Chisel3 empty range is broken)
  def trim(id: UInt, size: Int): UInt = if (size <= 1) 0.U else id(log2Ceil(size)-1, 0)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val nClients = edgeOut.master.clients.size
      val outIdMap = edgeOut.master.clients.map(_.sourceId)
      val inIdMap = edgeIn.master.clients.map(_.sourceId)

      val snoop = Wire(Valid(new Snoop))
      val snoop_client = Wire(UInt(log2Ceil(nClients).W))

      // Implement prefetchers per client.
      val prefetchers = edgeOut.master.clients.zipWithIndex.map { case (c,i) =>
        val pParams = params.prefetcher(c.name).getOrElse(NullPrefetcherParams())
        println(s"Prefetcher for ${c.name}: ${pParams.desc}")
        val prefetcher = pParams.instantiate()
        prefetcher.io.snoop.valid := snoop.valid && snoop_client === i.U
        prefetcher.io.snoop.bits := snoop.bits
        prefetcher
      }

      val out_arb = Module(new RRArbiter(new Prefetch, nClients))
      out_arb.io.in <> prefetchers.map(_.io.request)

      val tracker = RegInit(0.U(params.prefetchIds.W))
      val next_tracker = PriorityEncoder(~tracker)
      val tracker_free = !tracker(next_tracker)

      def inIdAdjuster(source: UInt) = Mux1H((inIdMap zip outIdMap).map { case (i,o) =>
        i.contains(source) -> (o.start.U | (source - i.start.U))
      })
      def outIdAdjuster(source: UInt) = Mux1H((inIdMap zip outIdMap).map { case (i,o) =>
        o.contains(source) -> (trim(source - o.start.U, i.size) + i.start.U)
      })
      def outIdToPrefetchId(source: UInt) = Mux1H((inIdMap zip outIdMap).map { case (i,o) =>
        o.contains(source) -> trim(source - (o.start + i.size).U, params.prefetchIds)
      })
      def prefetchIdToOutId(source: UInt, client: UInt) = Mux1H((inIdMap zip outIdMap).zipWithIndex.map { case ((i,o),id) =>
        (id.U === client) -> ((o.start + i.size).U +& source)
      })
      def inIdToClientId(source: UInt) = Mux1H(inIdMap.zipWithIndex.map { case (i,id) =>
        i.contains(source) -> id.U
      })

      out <> in
      out.a.bits.source := inIdAdjuster(in.a.bits.source)
      in.b.bits.source := outIdAdjuster(out.b.bits.source)
      out.c.bits.source := inIdAdjuster(in.c.bits.source)
      val d_is_prefetch = out.d.bits.opcode === TLMessages.HintAck
      in.d.valid := out.d.valid && !d_is_prefetch
      when (d_is_prefetch) { out.d.ready := true.B }
      in.d.bits.source := outIdAdjuster(out.d.bits.source)

      tracker := (tracker
        ^ ((out.d.valid && d_is_prefetch) << outIdToPrefetchId(out.d.bits.source))
        ^ ((out.a.fire && !in.a.valid) << next_tracker)
      )
      snoop.valid := in.a.fire && edgeIn.manager.supportsAcquireBFast(in.a.bits.address, log2Ceil(p(CacheBlockBytes)).U)
      snoop.bits.address := in.a.bits.address
      val acq = in.a.bits.opcode.isOneOf(TLMessages.AcquireBlock, TLMessages.AcquirePerm)
      val toT = in.a.bits.param.isOneOf(TLPermissions.NtoT, TLPermissions.BtoT)
      val put = edgeIn.hasData(in.a.bits)
      snoop.bits.write := put || (acq && toT)
      snoop_client := inIdToClientId(in.a.bits.source)

      val legal_address = edgeOut.manager.findSafe(out_arb.io.out.bits.block_address).reduce(_||_)
      val (legal, hint) = edgeOut.Hint(
        prefetchIdToOutId(next_tracker, out_arb.io.chosen),
        out_arb.io.out.bits.block_address,
        log2Up(p(CacheBlockBytes)).U,
        Mux(out_arb.io.out.bits.write, TLHints.PREFETCH_WRITE, TLHints.PREFETCH_READ)
      )
      out_arb.io.out.ready := false.B
      when (!in.a.valid) {
        out.a.valid := out_arb.io.out.valid && tracker_free && legal && legal_address
        out.a.bits := hint
        out_arb.io.out.ready := out.a.ready
      }
      when (!legal || !legal_address) {
        out_arb.io.out.ready := true.B
      }
    }
  }
}

object TLPrefetcher {
  def apply()(implicit p: Parameters) = {
    val prefetcher = LazyModule(new TLPrefetcher)
    prefetcher.node
  }
}

case class TilePrefetchingMasterPortParams(tileId: Int, base: HierarchicalElementPortParamsLike) extends HierarchicalElementPortParamsLike {
  val where = base.where
  def injectNode(context: Attachable)(implicit p: Parameters): TLNode = {
    TLPrefetcher() :*=* base.injectNode(context)(p)
  }
}
