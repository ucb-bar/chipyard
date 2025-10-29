package testchipip.soc

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy.{AddressSet, AddressDecoder, BufferParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.HellaPeekingArbiter

case class TLNetworkBufferParams(
  a: BufferParams,
  b: BufferParams,
  c: BufferParams,
  d: BufferParams,
  e: BufferParams)

object TLNetworkBufferParams {
  def apply(x: BufferParams): TLNetworkBufferParams = apply(x, x)
  def apply(ace: BufferParams, bd: BufferParams): TLNetworkBufferParams =
    apply(ace, bd, ace, bd, ace)

  val default = apply(BufferParams.default)
}

class NetworkBundle[T <: Data](
    nNodes: Int, payloadTyp: T) extends Bundle {
  val netId = UInt(log2Ceil(nNodes).W)
  val payload = payloadTyp.cloneType
  val last = Bool()

}

class NetworkIO[T <: Data](
    nIn: Int, nOut: Int,
    payloadTyp: T, netIdRange: Option[Int] = None)
    extends Bundle {
  val nNodes = netIdRange.getOrElse(nOut)
  def bundleType(dummy: Int = 0) = new NetworkBundle(nNodes, payloadTyp)

  val in = Flipped(Vec(nIn, Decoupled(bundleType())))
  val out = Vec(nOut, Decoupled(bundleType()))

}

abstract class NetworkInterconnect[T <: Data] extends Module {
  val io: NetworkIO[T]
}

trait HasTLNetwork {
  val edgesIn: Seq[TLEdgeIn]
  val edgesOut: Seq[TLEdgeOut]

  lazy val commonBundle = TLBundleParameters.union(
    edgesIn.map(_.bundle) ++ edgesOut.map(_.bundle))

  def filter[T](data: Seq[T], mask: Seq[Boolean]) =
    (data zip mask).filter(_._2).map(_._1)

  lazy val inputIdRanges = TLXbar.mapInputIds(edgesIn.map(_.client))
  lazy val outputIdRanges = TLXbar.mapOutputIds(edgesOut.map(_.manager))
  lazy val reachabilityMatrix = edgesIn.map(
    edgeIn => edgesOut.map(
      edgeOut => edgeIn.client.clients.exists(
        client => edgeOut.manager.managers.exists(
          man => client.visibility.exists(
            caddr => man.address.exists(
              maddr => caddr.overlaps(maddr)))))))
  lazy val canRelease = edgesIn.map(_.client.anySupportProbe)
  lazy val canProbe = edgesOut.map(_.manager.anySupportAcquireB)

  def forwardIds: Seq[UInt]
  def backwardIds: Seq[UInt]
  def networkName: String

  def connectInput(i: Int, in: TLBundle,
      anet: DecoupledIO[NetworkBundle[TLBundleA]],
      bnet: DecoupledIO[NetworkBundle[TLBundleB]],
      cnet: DecoupledIO[NetworkBundle[TLBundleC]],
      dnet: DecoupledIO[NetworkBundle[TLBundleD]],
      enet: DecoupledIO[NetworkBundle[TLBundleE]]) {

    val edgeIn = edgesIn(i)
    val inRange = inputIdRanges(i)
    val reachable = reachabilityMatrix(i)
    val probing = reachable.zip(canProbe).map { case (r, p) => r && p}

    val portAddrs = edgesOut.map(_.manager.managers.flatMap(_.address))
    val routingMask = AddressDecoder(filter(portAddrs, reachable))
    val routeAddrs = portAddrs.map(seq =>
        AddressSet.unify(seq.map(_.widen(~routingMask)).distinct))
    val routeFuncs = routeAddrs.map(seq =>
        (addr: UInt) => seq.map(_.contains(addr)).reduce(_ || _))

    val aMatches = filter(routeFuncs, reachable).map(
      route => route(in.a.bits.address))
    val cMatches = filter(routeFuncs, probing).map(
      route => route(in.c.bits.address))
    val eMatches = filter(outputIdRanges, probing).map(
      range => range.contains(in.e.bits.sink))

    val acquireIds = filter(forwardIds, reachable)
    val releaseIds = filter(forwardIds, probing)

    assert(!in.a.valid || PopCount(aMatches) === 1.U,
      s"$networkName: Multiple or no matching routes for A channel $i")
    assert(!in.c.valid || PopCount(cMatches) === 1.U,
      s"$networkName: Multiple or no matching routes for C channel $i")
    assert(!in.e.valid || PopCount(eMatches) === 1.U,
      s"$networkName: Multiple or no matching routes for E channel $i")

    val connectBCE = canRelease(i)

    wrap(
      net = anet,
      tl = in.a,
      selects = aMatches,
      ids = acquireIds,
      edge = edgeIn,
      sourceStart = inRange.start)

    unwrap(in.b, bnet, inRange.size, connectBCE)

    wrap(
      net = cnet,
      tl = in.c,
      selects = cMatches,
      ids = releaseIds,
      edge = edgeIn,
      sourceStart = inRange.start,
      connect = connectBCE)

    unwrap(in.d, dnet, inRange.size)

    wrap(
      net = enet,
      tl = in.e,
      selects = eMatches,
      ids = releaseIds,
      edge = edgeIn,
      connect = connectBCE)
  }


  def connectOutput(i: Int, out: TLBundle,
      anet: DecoupledIO[NetworkBundle[TLBundleA]],
      bnet: DecoupledIO[NetworkBundle[TLBundleB]],
      cnet: DecoupledIO[NetworkBundle[TLBundleC]],
      dnet: DecoupledIO[NetworkBundle[TLBundleD]],
      enet: DecoupledIO[NetworkBundle[TLBundleE]]) {
    val edgeOut = edgesOut(i)
    val outRange = outputIdRanges(i)
    val reachable = reachabilityMatrix.map(seq => seq(i))
    val probeable = reachable.zip(canRelease).map { case (r, p) => r && p }
    val routeFuncs = inputIdRanges.map(range =>
        (source: UInt) => range.contains(source))

    val bMatches = filter(routeFuncs, probeable).map(
      route => route(out.b.bits.source))
    val dMatches = filter(routeFuncs, reachable).map(
      route => route(out.d.bits.source))

    assert(!out.b.valid || PopCount(bMatches) === 1.U,
      s"TLRingNetwork: Multiple or no matching routes for B channel $i")
    assert(!out.d.valid || PopCount(dMatches) === 1.U,
      s"TLRingNetwork: Multiple or no matching routes for D channel $i")

    val grantIds = filter(backwardIds, reachable)
    val probeIds = filter(backwardIds, probeable)

    val connectBCE = canProbe(i)

    unwrap(out.a, anet)

    wrap(
      net = bnet,
      tl = out.b,
      selects = bMatches,
      ids = probeIds,
      edge = edgeOut,
      connect = connectBCE)

    unwrap(out.c, cnet, connect = connectBCE)

    wrap(
      net = dnet,
      tl = out.d,
      selects = dMatches,
      ids = grantIds,
      edge = edgeOut,
      sinkStart = outRange.start)

    unwrap(out.e, enet, outRange.size, connectBCE)
  }

  def wrap[T <: TLChannel](
      net: DecoupledIO[NetworkBundle[T]], tl: DecoupledIO[T],
      selects: Seq[Bool], ids: Seq[UInt], edge: TLEdge,
      sourceStart: BigInt = -1, sinkStart: BigInt = -1,
      connect: Boolean = true) {
    if (connect) {
      net.valid := tl.valid
      net.bits.netId := Mux1H(selects, ids)
      net.bits.payload := tl.bits
      net.bits.last := edge.last(tl)

      if (sourceStart != -1 || sinkStart != -1) {
        (net.bits.payload, tl.bits) match {
          case (netA: TLBundleA, tlA: TLBundleA) =>
            netA.source := tlA.source | sourceStart.U
          case (netC: TLBundleC, tlC: TLBundleC) =>
            netC.source := tlC.source | sourceStart.U
          case (netD: TLBundleD, tlD: TLBundleD) =>
            netD.sink := tlD.sink | sinkStart.U
          case (_, _) => ()
        }
      }

      tl.ready := net.ready
    } else {
      net.valid := false.B
      net.bits := DontCare
      tl.ready := false.B
    }
  }

  def trim(id: UInt, size: Int) =
    if (size <= 1) 0.U else id(log2Ceil(size)-1, 0)

  def unwrap[T <: TLChannel](
      tl: DecoupledIO[T], net: DecoupledIO[NetworkBundle[T]],
      idSize: Int = 0,
      connect: Boolean = true) {
    if (connect) {
      tl.valid := net.valid
      tl.bits := net.bits.payload
      if (idSize > 0) {
        (tl.bits, net.bits.payload) match {
          case (tlB: TLBundleB, netB: TLBundleB) =>
            tlB.source := trim(netB.source, idSize)
          case (tlD: TLBundleD, netD: TLBundleD) =>
            tlD.source := trim(netD.source, idSize)
          case (tlE: TLBundleE, netE: TLBundleE) =>
            tlE.sink := trim(netE.sink, idSize)
          case (_, _) => ()
        }
      }
      net.ready := tl.ready
    } else {
      tl.valid := false.B
      tl.bits := DontCare
      net.ready := false.B
    }
  }
}

class NetworkXbar[T <: Data](nInputs: Int, nOutputs: Int, payloadTyp: T, rr: Boolean = false)
    extends NetworkInterconnect[T] {
  val io = IO(new NetworkIO(nInputs, nOutputs, payloadTyp))

  val fanout = if (nOutputs > 1) {
    io.in.map { in =>
      val outputs = Seq.fill(nOutputs) { Wire(Decoupled(io.bundleType())) }
      val outReadys = VecInit(outputs.map(_.ready))
      outputs.zipWithIndex.foreach { case (out, id) =>
        out.valid := in.valid && in.bits.netId === id.U
        out.bits := in.bits
      }
      in.ready := outReadys(in.bits.netId)
      outputs
    }
  } else {
    io.in.map(in => Seq(in))
  }

  val arbiters = Seq.fill(nOutputs) {
    Module(new HellaPeekingArbiter(
      io.bundleType(), nInputs, (b: NetworkBundle[T]) => b.last, rr = rr))
  }

  io.out <> arbiters.zipWithIndex.map { case (arb, i) =>
    arb.io.in <> fanout.map(fo => fo(i))
    arb.io.out
  }
}
