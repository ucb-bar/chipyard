package testchipip.serdes

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._

object TLSerdesser {
  // This should be the standard bundle type for TLSerdesser
  val STANDARD_TLBUNDLE_PARAMS = TLBundleParameters(
    addressBits=64, dataBits=64,
    sourceBits=8, sinkBits=8, sizeBits=8,
    echoFields=Nil, requestFields=Nil, responseFields=Nil,
    hasBCE=true)
}

class SerdesDebugIO extends Bundle {
  val ser_busy = Output(Bool())
  val des_busy = Output(Bool())
}

class TLSerdesser(
  val flitWidth: Int,
  clientPortParams: Option[TLMasterPortParameters],
  managerPortParams: Option[TLSlavePortParameters],
  val bundleParams: TLBundleParameters = TLSerdesser.STANDARD_TLBUNDLE_PARAMS,
  nameSuffix: Option[String] = None
)
  (implicit p: Parameters) extends LazyModule {
  require (clientPortParams.isDefined || managerPortParams.isDefined)
  val clientNode = clientPortParams.map { c => TLClientNode(Seq(c)) }
  val managerNode = managerPortParams.map { m => TLManagerNode(Seq(m)) }

  override lazy val desiredName = (Seq("TLSerdesser") ++ nameSuffix).mkString("_")

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val ser = Vec(5, new DecoupledFlitIO(flitWidth))
      val debug = new SerdesDebugIO
    })

    val client_tl = clientNode.map(_.out(0)._1).getOrElse(WireInit(0.U.asTypeOf(new TLBundle(bundleParams))))
    val client_edge = clientNode.map(_.out(0)._2)
    val manager_tl = managerNode.map(_.in(0)._1).getOrElse(WireInit(0.U.asTypeOf(new TLBundle(bundleParams))))
    val manager_edge = managerNode.map(_.in(0)._2)

    val clientParams = client_edge.map(_.bundle).getOrElse(bundleParams)
    val managerParams = manager_edge.map(_.bundle).getOrElse(bundleParams)
    val mergedParams = clientParams.union(managerParams).union(bundleParams)
    require(mergedParams.echoFields.isEmpty, "TLSerdesser does not support TileLink with echo fields")
    require(mergedParams.requestFields.isEmpty, "TLSerdesser does not support TileLink with request fields")
    require(mergedParams.responseFields.isEmpty, "TLSerdesser does not support TileLink with response fields")
    require(mergedParams == bundleParams, s"TLSerdesser is misconfigured, the combined inwards/outwards parameters cannot be serialized using the provided bundle params\n$mergedParams > $bundleParams")

    val out_channels = Seq(
      (manager_tl.e, manager_edge.map(e => Module(new TLEToBeat(e, mergedParams, nameSuffix)))),
      (client_tl.d,  client_edge.map (e => Module(new TLDToBeat(e, mergedParams, nameSuffix)))),
      (manager_tl.c, manager_edge.map(e => Module(new TLCToBeat(e, mergedParams, nameSuffix)))),
      (client_tl.b,  client_edge.map (e => Module(new TLBToBeat(e, mergedParams, nameSuffix)))),
      (manager_tl.a, manager_edge.map(e => Module(new TLAToBeat(e, mergedParams, nameSuffix))))
    )
    io.ser.map(_.out.valid := false.B)
    io.ser.map(_.out.bits := DontCare)
    val out_sers = out_channels.zipWithIndex.map { case ((c,b),i) => b.map { b =>
      b.io.protocol <> c
      val ser = Module(new GenericSerializer(b.io.beat.bits.cloneType, flitWidth)).suggestName(s"ser_$i")
      ser.io.in <> b.io.beat
      io.ser(i).out <> ser.io.out
      ser
    }}.flatten

    io.debug.ser_busy := out_sers.map(_.io.busy).orR

    val in_channels = Seq(
      (client_tl.e,  Module(new TLEFromBeat(mergedParams, nameSuffix))),
      (manager_tl.d, Module(new TLDFromBeat(mergedParams, nameSuffix))),
      (client_tl.c,  Module(new TLCFromBeat(mergedParams, nameSuffix))),
      (manager_tl.b, Module(new TLBFromBeat(mergedParams, nameSuffix))),
      (client_tl.a,  Module(new TLAFromBeat(mergedParams, nameSuffix)))
    )
    val in_desers = in_channels.zipWithIndex.map { case ((c,b),i) =>
      c <> b.io.protocol
      val des = Module(new GenericDeserializer(b.io.beat.bits.cloneType, flitWidth)).suggestName(s"des_$i")
      des.io.in <> io.ser(i).in
      b.io.beat <> des.io.out
      des
    }
    io.debug.des_busy := in_desers.map(_.io.busy).orR
  }
}
