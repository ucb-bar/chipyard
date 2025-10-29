package testchipip.soc

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{SystemBus, SystemBusParams, HasTileLinkLocations}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{HellaPeekingArbiter, BooleanToAugmentedBoolean, Location}

abstract class RingInternalIO[T <: Data](bundleType: NetworkBundle[T]) extends Bundle {
  val int_in = Flipped(Decoupled(bundleType))
  val int_out = Decoupled(bundleType)
}

class RingInputNodeIO[T <: Data](bundleType: NetworkBundle[T])
    extends RingInternalIO(bundleType) {
  val ext_in = Flipped(Decoupled(bundleType))

}

class RingOutputNodeIO[T <: Data](bundleType: NetworkBundle[T])
    extends RingInternalIO(bundleType) {
  val ext_out = Decoupled(bundleType)

}

class RingInputNode[T <: Data](
    nNodes: Int, payloadTyp: T, buffer: BufferParams) extends Module {
  val bundleType = new NetworkBundle(nNodes, payloadTyp)
  val io = IO(new RingInputNodeIO(bundleType))

  val extArb = Module(new HellaPeekingArbiter(
    bundleType, 2, (b: NetworkBundle[T]) => b.last))
  extArb.io.in <> Seq(io.ext_in, io.int_in)
  io.int_out <> buffer(extArb.io.out)
}

class RingOutputNode[T <: Data](
    id: Int, nNodes: Int, payloadTyp: T, buffer: BufferParams) extends Module {
  val bundleType = new NetworkBundle(nNodes, payloadTyp)
  val io = IO(new RingOutputNodeIO(bundleType))

  val intIn = buffer(io.int_in)

  val intInMatch = intIn.bits.netId === id.U
  io.ext_out.valid := intIn.valid && intInMatch
  io.ext_out.bits := intIn.bits
  io.int_out.valid := intIn.valid && !intInMatch
  io.int_out.bits := intIn.bits
  intIn.ready := Mux(intInMatch, io.ext_out.ready, io.int_out.ready)
}

class NetworkRing[T <: Data](
    nIn: Int, nOut: Int, payloadTyp: T,
    buffer: BufferParams = BufferParams.default,
    inputFirst: Boolean = true,
    inputMap: Option[Seq[Int]] = None,
    outputMap: Option[Seq[Int]] = None)
    extends NetworkInterconnect[T] {
  val nNodes = nIn + nOut
  val io = IO(new NetworkIO(nIn, nOut, payloadTyp, Some(nNodes)))

  def mappingOK(mapping: Seq[Int], n: Int): Boolean = {
    mapping.size == n &&
    mapping.sorted == Seq.tabulate(n)(i => i)
  }

  require(inputMap.isEmpty || mappingOK(inputMap.get, nIn),
    s"Bad input mapping. Must be permutation of [0,$nIn).")
  require(outputMap.isEmpty || mappingOK(outputMap.get, nOut),
    s"Bad output mapping. Must be permutation of [0,$nOut).")

  val inNodes = Seq.fill(nIn) {
    Module(new RingInputNode(nNodes, payloadTyp, buffer))
  }

  val outIdStart = if (inputFirst) nIn else 0
  val outNodes = Seq.tabulate(nOut) { i =>
    Module(new RingOutputNode(i + outIdStart, nNodes, payloadTyp, buffer))
  }

  val inReordered = inputMap.map(
    imap => imap.map(i => inNodes(i))).getOrElse(inNodes)
  val outReordered = outputMap.map(
    omap => omap.map(i => outNodes(i))).getOrElse(outNodes)

  val combNodes = inReordered.map(_.io) ++ outReordered.map(_.io)

  combNodes.init.zip(combNodes.tail).foreach {
    case (left, right) => right.int_in <> left.int_out
  }

  combNodes.head.int_in <> combNodes.last.int_out

  io.out <> outNodes.map(_.io.ext_out)
  inNodes.zip(io.in).foreach { case (node, in) => node.io.ext_in <> in }
}

/**
 * @buffer - Buffer configurations for each channel. Default is two entry queue for every channel
 * @inputMap - Optionally specify the positions of inputs on the ring.
 *             If defined, the seq must be the same size as the number of inputs.
 *             The index of the seq corresponds to a position on the ring,
 *             and the value corresponds to the index of the input assigned to it.
 *             Default value is None, meaning no reordering
 * @outputMap - Optionally specify the positions of outputs on the ring.
 *              Format same as inputMap. Default is None, meaning no reordering.
 */
class TLRingNetwork(
    buffer: TLNetworkBufferParams = TLNetworkBufferParams.default,
    inputMap: Option[Seq[Int]] = None,
    outputMap: Option[Seq[Int]] = None)
    (implicit p: Parameters) extends LazyModule {

  val node = TLNexusNode(
    clientFn  = { seq =>
      seq(0).v1copy(
        minLatency = seq.map(_.minLatency).min,
        clients = (TLXbar.mapInputIds(seq) zip seq) flatMap { case (range, port) =>
          port.clients map { client => client.v1copy(
            sourceId = client.sourceId.shift(range.start)
          )}
        })
    },
    managerFn = { seq =>
      val fifoIdFactory = TLXbar.relabeler()
      seq(0).v1copy(
        minLatency = seq.map(_.minLatency).min,
        endSinkId = TLXbar.mapOutputIds(seq).map(_.end).max,
        managers = seq.flatMap { port =>
          require (port.beatBytes == seq(0).beatBytes,
            s"Ring data widths don't match: ${port.managers.map(_.name)} has ${port.beatBytes}B vs ${seq(0).managers.map(_.name)} has ${seq(0).beatBytes}B")
          val fifoIdMapper = fifoIdFactory()
          port.managers map { manager => manager.v1copy(
            fifoId = manager.fifoId.map(fifoIdMapper(_))
          )}
        })
    })

  lazy val module = new LazyModuleImp(this) with HasTLNetwork {
    val (io_in, edgesIn) = node.in.unzip
    val (io_out, edgesOut) = node.out.unzip
    val nIn = edgesIn.size
    val nOut = edgesOut.size
    val nNodes = nIn + nOut
    val idBits = log2Ceil(nNodes)

    val forwardIds = (0 until nOut).map(id => (id + nIn).U(idBits.W))
    val backwardIds = (0 until nIn).map(_.U(idBits.W))
    val networkName = "TLRingNetwork"

    if (nIn > 1 || nOut > 1) {
      val aRing = Module(new NetworkRing(
        nIn, nOut, new TLBundleA(commonBundle),
        buffer.a, true, inputMap, outputMap))

      val bRing = Module(new NetworkRing(
        nOut, nIn, new TLBundleB(commonBundle),
        buffer.b, false, outputMap, inputMap))

      val cRing = Module(new NetworkRing(
        nIn, nOut, new TLBundleC(commonBundle),
        buffer.c, true, inputMap, outputMap))

      val dRing = Module(new NetworkRing(
        nOut, nIn, new TLBundleD(commonBundle),
        buffer.d, false, outputMap, inputMap))

      val eRing = Module(new NetworkRing(
        nIn, nOut, new TLBundleE(commonBundle),
        buffer.e, true, inputMap, outputMap))

      io_in.zipWithIndex.foreach { case (in, i) =>
        connectInput(i, in,
          aRing.io.in(i),
          bRing.io.out(i),
          cRing.io.in(i),
          dRing.io.out(i),
          eRing.io.in(i))
      }

      io_out.zipWithIndex.foreach { case (out, i) =>
        connectOutput(i, out,
          aRing.io.out(i),
          bRing.io.in(i),
          cRing.io.out(i),
          dRing.io.in(i),
          eRing.io.out(i))
      }
    } else {
      io_out.head <> io_in.head
    }
  }
}

case class RingSystemBusParams(
    params: SystemBusParams,
    buffer: TLNetworkBufferParams)
  extends TLBusWrapperInstantiationLike {

  def instantiate(context: HasTileLinkLocations, loc: Location[TLBusWrapper])(implicit p: Parameters): SystemBus = {
    val ring = LazyModule(new RingSystemBus(params, buffer))
    ring.suggestName(loc.name)
    context.tlBusWrapperLocationMap += (loc -> ring)
    ring
  }
}

class RingSystemBus(params: SystemBusParams, buffer: TLNetworkBufferParams)
    (implicit p: Parameters) extends SystemBus(params) {
  val system_bus_ring = LazyModule(new TLRingNetwork(buffer))

  override val inwardNode: TLInwardNode = system_bus_ring.node
  override val outwardNode: TLOutwardNode = system_bus_ring.node
  override def busView: TLEdge = system_bus_ring.node.edges.in.head
}
