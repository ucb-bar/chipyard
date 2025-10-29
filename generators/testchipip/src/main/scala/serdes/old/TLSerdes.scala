package testchipip.serdes.old

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config._
import freechips.rocketchip.util.HellaPeekingArbiter
import freechips.rocketchip.tilelink._


// If hasCorruptDenied is false we revert to earlier TL2 bundles which have an error signal on C and D in the same position as denied in D
class TLMergedBundle(params: TLBundleParameters, hasCorruptDenied: Boolean = true) extends TLBundleBase(params) {
  val chanId = UInt(3.W)
  val opcode = UInt(3.W)
  val param = UInt(Seq(
    TLAtomics.width, TLHints.width,
    TLPermissions.aWidth, TLPermissions.bdWidth, TLPermissions.cWidth).max.W)
  val size = UInt(params.sizeBits.W)
  val source = UInt(params.sourceBits.W)
  val address = UInt(params.addressBits.W)
  val data = UInt(params.dataBits.W)
  val corrupt = if(hasCorruptDenied) Some(Bool()) else None
  // either mask or sink+denied (or sink+error if !hasCorruptDenied)
  val union = UInt(Seq(params.dataBits/8, params.sinkBits + 1).max.W)
  val last = Bool()

  def isA(dummy: Int = 0) = (chanId === TLMergedBundle.TL_CHAN_ID_A)
  def isB(dummy: Int = 0) = (chanId === TLMergedBundle.TL_CHAN_ID_B)
  def isC(dummy: Int = 0) = (chanId === TLMergedBundle.TL_CHAN_ID_C)
  def isD(dummy: Int = 0) = (chanId === TLMergedBundle.TL_CHAN_ID_D)
  def isE(dummy: Int = 0) = (chanId === TLMergedBundle.TL_CHAN_ID_E)

}

object TLMergedBundle {
  val TL_CHAN_ID_A = 0.U
  val TL_CHAN_ID_B = 1.U
  val TL_CHAN_ID_C = 2.U
  val TL_CHAN_ID_D = 3.U
  val TL_CHAN_ID_E = 4.U

  def apply(a: TLBundleA, hasCorruptDenied: Boolean): TLMergedBundle = apply(a, a.params, hasCorruptDenied)

  def apply(a: TLBundleA, params: TLBundleParameters, hasCorruptDenied: Boolean): TLMergedBundle = {
    val merged = Wire(new TLMergedBundle(params, hasCorruptDenied))
    merged.chanId  := TL_CHAN_ID_A
    merged.opcode  := a.opcode
    merged.param   := a.param
    merged.size    := a.size
    merged.source  := a.source
    merged.address := a.address
    merged.data    := a.data
    if(hasCorruptDenied)
      merged.corrupt.get := a.corrupt
    merged.union   := a.mask
    merged.last    := true.B
    merged
  }

  def apply(b: TLBundleB, hasCorruptDenied: Boolean): TLMergedBundle = apply(b, b.params, hasCorruptDenied)

  def apply(b: TLBundleB, params: TLBundleParameters, hasCorruptDenied: Boolean): TLMergedBundle = {
    val merged = Wire(new TLMergedBundle(params, hasCorruptDenied))
    merged.chanId  := TL_CHAN_ID_B
    merged.opcode  := b.opcode
    merged.param   := b.param
    merged.size    := b.size
    merged.source  := b.source
    merged.address := b.address
    merged.data    := b.data
    if(hasCorruptDenied)
      merged.corrupt.get := b.corrupt
    merged.union   := b.mask
    merged.last    := true.B
    merged
  }

  def apply(c: TLBundleC, hasCorruptDenied: Boolean): TLMergedBundle = apply(c, c.params, hasCorruptDenied)

  def apply(c: TLBundleC, params: TLBundleParameters, hasCorruptDenied: Boolean): TLMergedBundle = {
    val merged = Wire(new TLMergedBundle(params, hasCorruptDenied))
    merged.chanId  := TL_CHAN_ID_C
    merged.opcode  := c.opcode
    merged.param   := c.param
    merged.size    := c.size
    merged.source  := c.source
    merged.address := c.address
    merged.data    := c.data
    if(hasCorruptDenied) {
      merged.corrupt.get := c.corrupt
      merged.union   := DontCare
    } else {
      merged.union   := 0.U //error
    }
    merged.last    := true.B
    merged
  }

  def apply(d: TLBundleD, hasCorruptDenied: Boolean): TLMergedBundle = apply(d, d.params, hasCorruptDenied)

  def apply(d: TLBundleD, params: TLBundleParameters, hasCorruptDenied: Boolean): TLMergedBundle = {
    val merged = Wire(new TLMergedBundle(params, hasCorruptDenied))
    merged.chanId  := TL_CHAN_ID_D
    merged.opcode  := d.opcode
    merged.param   := d.param
    merged.size    := d.size
    merged.source  := d.source
    merged.address := DontCare
    merged.data    := d.data
    if(hasCorruptDenied) {
      merged.corrupt.get := d.corrupt
      merged.union   := Cat(d.sink, d.denied)
    } else {
      merged.union   := Cat(d.sink, 0.U) //error
    }
    merged.last    := true.B
    merged
  }

  def apply(e: TLBundleE, hasCorruptDenied: Boolean): TLMergedBundle = apply(e, e.params, hasCorruptDenied)

  def apply(e: TLBundleE, params: TLBundleParameters, hasCorruptDenied: Boolean): TLMergedBundle = {
    val merged = Wire(new TLMergedBundle(params, hasCorruptDenied))
    merged.chanId  := TL_CHAN_ID_E
    merged.opcode  := 0.U
    merged.param   := 0.U
    merged.size    := 0.U
    merged.source  := 0.U
    merged.address := 0.U
    merged.data    := 0.U
    if(hasCorruptDenied) {
      merged.corrupt.get := DontCare
      merged.union   := Cat(e.sink, false.B)
    } else {
      merged.union   := Cat(e.sink)
    }
    merged.last    := true.B
    merged
  }

  def apply(chan: DecoupledIO[TLChannel], hasCorruptDenied: Boolean, last: DecoupledIO[TLChannel] => Bool): DecoupledIO[TLMergedBundle] =
    apply(chan, chan.bits.params, hasCorruptDenied, last)

  def apply(chan: DecoupledIO[TLChannel], params: TLBundleParameters, hasCorruptDenied: Boolean, last: DecoupledIO[TLChannel] => Bool): DecoupledIO[TLMergedBundle] = {
    val merged = Wire(Decoupled(new TLMergedBundle(params)))
    merged.valid := chan.valid
    merged.bits := (chan.bits match {
      case (a: TLBundleA) => apply(a, params, hasCorruptDenied)
      case (b: TLBundleB) => apply(b, params, hasCorruptDenied)
      case (c: TLBundleC) => apply(c, params, hasCorruptDenied)
      case (d: TLBundleD) => apply(d, params, hasCorruptDenied)
      case (e: TLBundleE) => apply(e, params, hasCorruptDenied)
    })
    merged.bits.last := last(chan)
    chan.ready := merged.ready
    merged
  }

  def toA(chan: TLMergedBundle, hasCorruptDenied: Boolean): TLBundleA = toA(chan, chan.params, hasCorruptDenied)

  def toA(chan: TLMergedBundle, params: TLBundleParameters, hasCorruptDenied: Boolean): TLBundleA = {
    val a = Wire(new TLBundleA(params))
    a.opcode  := chan.opcode
    a.param   := chan.param
    a.size    := chan.size
    a.source  := chan.source
    a.address := chan.address
    a.data    := chan.data
    if(hasCorruptDenied)
      a.corrupt := chan.corrupt.get
    else
      a.corrupt := false.B
    a.mask    := chan.union
    a
  }

  def toA(chan: DecoupledIO[TLMergedBundle], hasCorruptDenied: Boolean): DecoupledIO[TLBundleA] =
    toA(chan, chan.bits.params, hasCorruptDenied)

  def toA(chan: DecoupledIO[TLMergedBundle], params: TLBundleParameters, hasCorruptDenied: Boolean): DecoupledIO[TLBundleA] = {
    val a = Wire(Decoupled(new TLBundleA(params)))
    a.valid := chan.valid
    a.bits  := apply(a.bits, params, hasCorruptDenied)
    chan.ready := a.ready
    a
  }

  def toB(chan: TLMergedBundle, hasCorruptDenied: Boolean): TLBundleB = toB(chan, chan.params, hasCorruptDenied)

  def toB(chan: TLMergedBundle, params: TLBundleParameters, hasCorruptDenied: Boolean): TLBundleB = {
    val b = Wire(new TLBundleB(params))
    b.opcode  := chan.opcode
    b.param   := chan.param
    b.size    := chan.size
    b.source  := chan.source
    b.address := chan.address
    b.data    := chan.data
    if(hasCorruptDenied)
      b.corrupt := chan.corrupt.get
    else
      b.corrupt := false.B
    b.mask    := chan.union
    b
  }

  def toB(chan: DecoupledIO[TLMergedBundle], hasCorruptDenied: Boolean): DecoupledIO[TLBundleB] =
    toB(chan, chan.bits.params, hasCorruptDenied)

  def toB(chan: DecoupledIO[TLMergedBundle], params: TLBundleParameters, hasCorruptDenied: Boolean): DecoupledIO[TLBundleB] = {
    val b = Wire(Decoupled(new TLBundleB(params)))
    b.valid := chan.valid
    b.bits  := apply(b.bits, hasCorruptDenied)
    chan.ready := b.ready
    b
  }

  def toC(chan: TLMergedBundle, hasCorruptDenied: Boolean): TLBundleC = toC(chan, chan.params, hasCorruptDenied)

  def toC(chan: TLMergedBundle, params: TLBundleParameters, hasCorruptDenied: Boolean): TLBundleC = {
    val c = Wire(new TLBundleC(params))
    c.opcode  := chan.opcode
    c.param   := chan.param
    c.size    := chan.size
    c.source  := chan.source
    c.address := chan.address
    c.data    := chan.data
    if(hasCorruptDenied)
      c.corrupt := chan.corrupt.get
    else
      c.corrupt := false.B
    c
  }

  def toC(chan: DecoupledIO[TLMergedBundle], hasCorruptDenied: Boolean): DecoupledIO[TLBundleC] =
    toC(chan, chan.bits.params, hasCorruptDenied)

  def toC(chan: DecoupledIO[TLMergedBundle], params: TLBundleParameters, hasCorruptDenied: Boolean): DecoupledIO[TLBundleC] = {
    val c = Wire(Decoupled(new TLBundleC(params)))
    c.valid := chan.valid
    c.bits  := apply(c.bits, hasCorruptDenied)
    chan.ready := c.ready
    c
  }

  def toD(chan: TLMergedBundle, hasCorruptDenied: Boolean): TLBundleD = toD(chan, chan.params, hasCorruptDenied)

  def toD(chan: TLMergedBundle, params: TLBundleParameters, hasCorruptDenied: Boolean): TLBundleD = {
    val d = Wire(new TLBundleD(params))
    d.opcode  := chan.opcode
    d.param   := chan.param
    d.size    := chan.size
    d.source  := chan.source
    d.data    := chan.data
    if(hasCorruptDenied) {
      d.corrupt := chan.corrupt.get
      d.sink    := chan.union >> 1.U
      d.denied  := chan.union(0)
    } else {
      d.corrupt := false.B
      d.sink    := chan.union >> 1.U // error
      d.denied  := false.B
    }
    d
  }

  def toD(chan: DecoupledIO[TLMergedBundle], hasCorruptDenied: Boolean): DecoupledIO[TLBundleD] =
    toD(chan, chan.bits.params, hasCorruptDenied)

  def toD(chan: DecoupledIO[TLMergedBundle], params: TLBundleParameters, hasCorruptDenied: Boolean): DecoupledIO[TLBundleD] = {
    val d = Wire(Decoupled(new TLBundleD(params)))
    d.valid := chan.valid
    d.bits  := apply(d.bits, hasCorruptDenied)
    chan.ready := d.ready
    d
  }

  def toE(chan: TLMergedBundle, hasCorruptDenied: Boolean): TLBundleE = toE(chan, chan.params, hasCorruptDenied)

  def toE(chan: TLMergedBundle, params: TLBundleParameters, hasCorruptDenied: Boolean): TLBundleE = {
    val e = Wire(new TLBundleE(params))
    if(hasCorruptDenied)
      e.sink := chan.union >> 1.U
    else
      e.sink := chan.union
    e
  }

  def toE(chan: DecoupledIO[TLMergedBundle], hasCorruptDenied: Boolean): DecoupledIO[TLBundleE] =
    toE(chan, chan.bits.params, hasCorruptDenied)

  def toE(chan: DecoupledIO[TLMergedBundle], params: TLBundleParameters, hasCorruptDenied: Boolean): DecoupledIO[TLBundleE] = {
    val e = Wire(Decoupled(new TLBundleE(params)))
    e.valid := chan.valid
    e.bits  := apply(e.bits, hasCorruptDenied)
    chan.ready := e.ready
    e
  }
}

class TLSerdes(w: Int, params: Seq[TLManagerParameters], beatBytes: Int = 8, hasCorruptDenied: Boolean = true)
    (implicit p: Parameters) extends LazyModule {

  val node = TLManagerNode(params.map(
    manager =>
      TLSlavePortParameters.v1(
        managers = Seq(manager),
        beatBytes = beatBytes)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val nChannels = params.size
    val io = IO(new Bundle {
      val ser = Vec(nChannels, new SerialIO(w))
    })

    val mergeTypes = new Array[TLMergedBundle](nChannels)

    node.in.zip(io.ser).zipWithIndex.foreach { case (((tl, edge), ser), i) =>
      val mergeType = new TLMergedBundle(tl.params, hasCorruptDenied)

      val outChannels = Seq(tl.e, tl.c, tl.a).map(TLMergedBundle(_, hasCorruptDenied, c => edge.last(c)))
      val outArb = Module(new HellaPeekingArbiter(
        mergeType, outChannels.size, (b: TLMergedBundle) => b.last))
      val outSer = Module(new GenericSerializer(mergeType, w))
      outArb.io.in <> outChannels
      outSer.io.in <> outArb.io.out
      ser.out <> outSer.io.out

      val inDes = Module(new GenericDeserializer(mergeType, w))
      inDes.io.in <> ser.in
      tl.b.valid := inDes.io.out.valid && inDes.io.out.bits.isB()
      tl.b.bits := TLMergedBundle.toB(inDes.io.out.bits, hasCorruptDenied)
      tl.d.valid := inDes.io.out.valid && inDes.io.out.bits.isD()
      tl.d.bits := TLMergedBundle.toD(inDes.io.out.bits, hasCorruptDenied)
      inDes.io.out.ready := MuxLookup(inDes.io.out.bits.chanId, false.B)(Seq(
        TLMergedBundle.TL_CHAN_ID_B -> tl.b.ready,
        TLMergedBundle.TL_CHAN_ID_D -> tl.d.ready))

      mergeTypes(i) = mergeType
    }
  }
}

class TLDesser(w: Int, params: Seq[TLClientParameters], hasCorruptDenied: Boolean = true)
    (implicit p: Parameters) extends LazyModule {

  val node = TLClientNode(params.map(client =>
      TLMasterPortParameters.v1(Seq(client))))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val nChannels = params.size
    val io = IO(new Bundle {
      val ser = Vec(nChannels, new SerialIO(w))
    })

    val mergeTypes = new Array[TLMergedBundle](nChannels)

    node.out.zip(io.ser).zipWithIndex.foreach { case (((tl, edge), ser), i) =>
      val mergeType = new TLMergedBundle(tl.params, hasCorruptDenied)

      val outChannels = Seq(tl.d, tl.b).map(TLMergedBundle(_, hasCorruptDenied, c => edge.last(c)))
      val outArb = Module(new HellaPeekingArbiter(
        mergeType, outChannels.size, (b: TLMergedBundle) => b.last))
      val outSer = Module(new GenericSerializer(mergeType, w))
      outArb.io.in <> outChannels
      outSer.io.in <> outArb.io.out
      ser.out <> outSer.io.out

      val inDes = Module(new GenericDeserializer(mergeType, w))
      inDes.io.in <> ser.in
      tl.a.valid := inDes.io.out.valid && inDes.io.out.bits.isA()
      tl.a.bits := TLMergedBundle.toA(inDes.io.out.bits, hasCorruptDenied)
      tl.c.valid := inDes.io.out.valid && inDes.io.out.bits.isC()
      tl.c.bits := TLMergedBundle.toC(inDes.io.out.bits, hasCorruptDenied)
      tl.e.valid := inDes.io.out.valid && inDes.io.out.bits.isE()
      tl.e.bits := TLMergedBundle.toE(inDes.io.out.bits, hasCorruptDenied)
      inDes.io.out.ready := MuxLookup(inDes.io.out.bits.chanId, false.B)(Seq(
        TLMergedBundle.TL_CHAN_ID_A -> tl.a.ready,
        TLMergedBundle.TL_CHAN_ID_C -> tl.c.ready,
        TLMergedBundle.TL_CHAN_ID_E -> tl.e.ready))

      mergeTypes(i) = mergeType
    }
  }
}

object TLSerdesser {
  // This should be the standard bundle type for TLSerdesser
  val STANDARD_TLBUNDLE_PARAMS = TLBundleParameters(
    addressBits=64, dataBits=64,
    sourceBits=8, sinkBits=8, sizeBits=8,
    echoFields=Nil, requestFields=Nil, responseFields=Nil,
    hasBCE=false)
}

class TLSerdesser(
  val w: Int,
  clientPortParams: Option[TLMasterPortParameters],
  managerPortParams: Option[TLSlavePortParameters],
  val bundleParams: TLBundleParameters = TLSerdesser.STANDARD_TLBUNDLE_PARAMS,
  hasCorruptDenied: Boolean = true)
  (implicit p: Parameters) extends LazyModule {
  require (clientPortParams.isDefined || managerPortParams.isDefined)
  val clientNode = clientPortParams.map { c => TLClientNode(Seq(c)) }
  val managerNode = managerPortParams.map { m => TLManagerNode(Seq(m)) }

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val ser = new SerialIO(w)
      val debug = new SerdesDebugIO
    })

    val client_tl = clientNode.map(_.out(0)._1).getOrElse(0.U.asTypeOf(new TLBundle(bundleParams)))
    val client_edge = clientNode.map(_.out(0)._2)
    val manager_tl = managerNode.map(_.in(0)._1).getOrElse(0.U.asTypeOf(new TLBundle(bundleParams)))
    val manager_edge = managerNode.map(_.in(0)._2)

    val clientParams = client_edge.map(_.bundle).getOrElse(bundleParams)
    val managerParams = manager_edge.map(_.bundle).getOrElse(bundleParams)
    val mergedParams = clientParams.union(managerParams).union(bundleParams)
    require(mergedParams.echoFields.isEmpty, "TLSerdesser does not support TileLink with echo fields")
    require(mergedParams.requestFields.isEmpty, "TLSerdesser does not support TileLink with request fields")
    require(mergedParams.responseFields.isEmpty, "TLSerdesser does not support TileLink with response fields")
    require(mergedParams == bundleParams, s"TLSerdesser is misconfigured, the combined inwards/outwards parameters cannot be serialized using the provided bundle params\n$mergedParams > $bundleParams")

    val mergeType = new TLMergedBundle(mergedParams, hasCorruptDenied)

    val outChannels = Seq(
      manager_tl.e, client_tl.d, manager_tl.c, client_tl.b, manager_tl.a)
    val outArb = Module(new HellaPeekingArbiter(
      mergeType, outChannels.size, (b: TLMergedBundle) => b.last))
    val outSer = Module(new GenericSerializer(mergeType, w))
    outArb.io.in <> outChannels.map(o => TLMergedBundle(o, mergedParams, hasCorruptDenied, c => client_edge.map(_.last(c)).getOrElse(false.B)))
    outSer.io.in <> outArb.io.out
    io.ser.out <> outSer.io.out
    io.debug.ser_busy := outSer.io.busy

    val inDes = Module(new GenericDeserializer(mergeType, w))
    inDes.io.in <> io.ser.in
    io.debug.des_busy := inDes.io.busy
    client_tl.a.valid := inDes.io.out.valid && inDes.io.out.bits.isA()
    client_tl.a.bits := TLMergedBundle.toA(inDes.io.out.bits, clientParams, hasCorruptDenied)
    manager_tl.b.valid := inDes.io.out.valid && inDes.io.out.bits.isB()
    manager_tl.b.bits := TLMergedBundle.toB(inDes.io.out.bits, managerParams, hasCorruptDenied)
    client_tl.c.valid := inDes.io.out.valid && inDes.io.out.bits.isC()
    client_tl.c.bits := TLMergedBundle.toC(inDes.io.out.bits, clientParams, hasCorruptDenied)
    manager_tl.d.valid := inDes.io.out.valid && inDes.io.out.bits.isD()
    manager_tl.d.bits := TLMergedBundle.toD(inDes.io.out.bits, managerParams, hasCorruptDenied)
    client_tl.e.valid := inDes.io.out.valid && inDes.io.out.bits.isE()
    client_tl.e.bits := TLMergedBundle.toE(inDes.io.out.bits, clientParams, hasCorruptDenied)
    inDes.io.out.ready := MuxLookup(inDes.io.out.bits.chanId, false.B)(Seq(
      TLMergedBundle.TL_CHAN_ID_A -> client_tl.a.ready,
      TLMergedBundle.TL_CHAN_ID_B -> manager_tl.b.ready,
      TLMergedBundle.TL_CHAN_ID_C -> client_tl.c.ready,
      TLMergedBundle.TL_CHAN_ID_D -> manager_tl.d.ready,
      TLMergedBundle.TL_CHAN_ID_E -> client_tl.e.ready))
  }
}
