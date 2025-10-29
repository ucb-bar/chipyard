package testchipip.serdes

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._

// This converts non-caching clients to a caching one in the diplomatic graph
// Since the client would never attempt to Acquire a block, an probes received
// can be responded to with NToN
class TLProbeBlocker(blockBytes: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLAdapterNode(
    clientFn  = { case cp =>
      cp.v1copy(clients = cp.clients.map { c => c.v1copy(
        supportsProbe = TransferSizes(1, blockBytes)
      )})
    },
    managerFn = { mp => mp }
  )
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    node.in.zip(node.out).foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val uncacheClients = edgeIn.client.clients.filter(_.supports.probe.none)

      def uncachedSource(id: UInt) = uncacheClients.map(_.sourceId.contains(id)).orR

      val probe_valid = RegInit(false.B)
      val probe_bits = Reg(new TLBundleB(edgeOut.bundle))

      out.a <> in.a
      in.b <> out.b
      when (out.b.valid && uncachedSource(in.b.bits.source)) {
        in.b.valid := false.B
        out.b.ready := !probe_valid
        when (!probe_valid) {
          probe_valid := true.B
          probe_bits := out.b.bits
        }
      }

      out.c <> in.c
      when (probe_valid && !in.c.valid) {
        out.c.valid := probe_valid
        out.c.bits := edgeOut.ProbeAck(probe_bits, TLPermissions.NtoN)
        when (out.c.ready) {
          probe_valid := false.B
        }
      }

      in.d <> out.d
      out.e <> in.e
    }
  }
}

object TLProbeBlocker
{
  def apply(blockBytes: Int)(implicit p: Parameters): TLNode =
  {
    val blocker = LazyModule(new TLProbeBlocker(blockBytes))
    blocker.node
  }
}

