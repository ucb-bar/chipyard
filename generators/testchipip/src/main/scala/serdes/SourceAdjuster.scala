package testchipip.serdes

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

// This adjusts source IDs by adding/subtracting offsets such that
// each client (up to maxClients) has a max number of inflight requests.
// Note this does not do any ID shrinking, so maxInFlightPerClient must be >+
// each client's ID range
class TLSourceAdjuster(maxClients: Int, maxInFlightPerClient: Int)(implicit p: Parameters) extends LazyModule
{
  require(isPow2(maxInFlightPerClient))
  val node = TLAdapterNode(
    clientFn  = { cp => {
      require(cp.clients.size <= maxClients, s"clients ${cp.clients.map(_.name)} > $maxClients")
      val clients = cp.clients.padTo(maxClients, TLMasterParameters.v1(
        name = "TLSourceAdjusterNullClient"))
      TLMasterPortParameters.v1(
        clients = clients.zipWithIndex.map { case (c, i) => {
          require(c.sourceId.size <= maxInFlightPerClient)
          c.v1copy(sourceId = IdRange(i*maxInFlightPerClient, (i+1)*maxInFlightPerClient))
        }},
        echoFields = cp.echoFields,
        requestFields = cp.requestFields,
        responseKeys = cp.responseKeys)
    }},
    managerFn = {mp => mp}
  )

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    node.in.zip(node.out).foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val idOffsets = edgeIn.client.masters.zipWithIndex.map { case (m,i) => {
        i * maxInFlightPerClient - m.sourceId.start
      }}

      def incrementId(inId: UInt) = {
        val client_oh = edgeIn.client.masters.map(_.sourceId.contains(inId))
        val offset = Mux1H(client_oh, idOffsets.map(_.S))
        (inId.asSInt + offset).asUInt
      }

      def decrementId(outId: UInt) = {
        val client_oh = UIntToOH(outId >> log2Ceil(maxInFlightPerClient))
        val offset = Mux1H(client_oh, idOffsets.map(_.S))
        (outId.asSInt - offset).asUInt
      }


      out.a <> in.a
      out.a.bits.source := incrementId(in.a.bits.source)

      in.b <> out.b
      in.b.bits.source := decrementId(out.b.bits.source)

      out.c <> in.c
      out.c.bits.source := incrementId(in.c.bits.source)

      in.d <> out.d
      in.d.bits.source := decrementId(out.d.bits.source)

      out.e <> in.e
    }
  }
}

object TLSourceAdjuster
{
  def apply(maxClients: Int, maxInFlightPerClient: Int)(implicit p: Parameters): TLNode =
  {
    val adjuster = LazyModule(new TLSourceAdjuster(maxClients, maxInFlightPerClient))
    adjuster.node
  }
}
