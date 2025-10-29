package testchipip.serdes

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._

trait TLFieldHelper {
  def getBodyFields(b: TLChannel): Seq[Data] = b match {
    case b: TLBundleA => Seq(b.mask, b.data, b.corrupt)
    case b: TLBundleB => Seq(b.mask, b.data, b.corrupt)
    case b: TLBundleC => Seq(        b.data, b.corrupt)
    case b: TLBundleD => Seq(        b.data, b.corrupt)
    case b: TLBundleE => Seq()
  }
  def getConstFields(b: TLChannel): Seq[Data] = b match {
    case b: TLBundleA => Seq(b.opcode, b.param, b.size, b.source, b.address, b.user, b.echo                  )
    case b: TLBundleB => Seq(b.opcode, b.param, b.size, b.source, b.address                                  )
    case b: TLBundleC => Seq(b.opcode, b.param, b.size, b.source, b.address, b.user, b.echo                  )
    case b: TLBundleD => Seq(b.opcode, b.param, b.size, b.source,            b.user, b.echo, b.sink, b.denied)
    case b: TLBundleE => Seq(                                                                b.sink          )
  }
  def minTLPayloadWidth(b: TLChannel): Int = Seq(getBodyFields(b), getConstFields(b)).map(_.map(_.getWidth).sum).max
  def minTLPayloadWidth(bs: Seq[TLChannel]): Int = bs.map(b => minTLPayloadWidth(b)).max
  def minTLPayloadWidth(b: TLBundle): Int = minTLPayloadWidth(Seq(b.a, b.b, b.c, b.d, b.e).map(_.bits))
}

class TLBeat(val beatWidth: Int) extends Bundle {
  val payload = UInt(beatWidth.W)
  val head = Bool()
  val tail = Bool()
}

abstract class TLChannelToBeat[T <: TLChannel](gen: => T, edge: TLEdge, nameSuffix: Option[String])(implicit val p: Parameters) extends Module with TLFieldHelper {
  override def desiredName = (Seq(this.getClass.getSimpleName) ++ nameSuffix ++ Seq(gen.params.shortName)).mkString("_")
  val beatWidth = minTLPayloadWidth(gen)
  val io = IO(new Bundle {
    val protocol = Flipped(Decoupled(gen))
    val beat = Decoupled(new TLBeat(beatWidth))
  })
  def unique(x: Vector[Boolean]): Bool = (x.filter(x=>x).size <= 1).B

  // convert decoupled to irrevocable
  val q = Module(new Queue(gen, 1, pipe=true, flow=true))
  q.io.enq <> io.protocol
  val protocol = q.io.deq

  val has_body = Wire(Bool())
  val body_fields = getBodyFields(protocol.bits)
  val const_fields = getConstFields(protocol.bits)
  val head = edge.first(protocol.bits, protocol.fire)
  val tail = edge.last(protocol.bits, protocol.fire)

  val body  = Cat( body_fields.filter(_.getWidth > 0).map(_.asUInt))
  val const = Cat(const_fields.filter(_.getWidth > 0).map(_.asUInt))

  val is_body = RegInit(false.B)
  io.beat.valid := protocol.valid
  protocol.ready := io.beat.ready && (is_body || !has_body)

  io.beat.bits.head       := head && !is_body
  io.beat.bits.tail       := tail && (is_body || !has_body)
  io.beat.bits.payload    := Mux(is_body, body, const)

  when (io.beat.fire && io.beat.bits.head) { is_body := true.B }
  when (io.beat.fire && io.beat.bits.tail) { is_body := false.B }
}

abstract class TLChannelFromBeat[T <: TLChannel](gen: => T, nameSuffix: Option[String])(implicit val p: Parameters) extends Module with TLFieldHelper {
  override def desiredName = (Seq(this.getClass.getSimpleName) ++ nameSuffix ++ Seq(gen.params.shortName)).mkString("_")
  val beatWidth = minTLPayloadWidth(gen)
  val io = IO(new Bundle {
    val protocol = Decoupled(gen)
    val beat = Flipped(Decoupled(new TLBeat(beatWidth)))
  })

  // Handle size = 1 gracefully (Chisel3 empty range is broken)
  def trim(id: UInt, size: Int): UInt = if (size <= 1) 0.U else id(log2Ceil(size)-1, 0)

  val protocol = Wire(Decoupled(gen))
  io.protocol <> protocol
  val body_fields = getBodyFields(protocol.bits)
  val const_fields = getConstFields(protocol.bits)

  val is_const = RegInit(true.B)
  val const_reg = Reg(UInt(const_fields.map(_.getWidth).sum.W))
  val const = Mux(io.beat.bits.head, io.beat.bits.payload, const_reg)
  io.beat.ready := (is_const && !io.beat.bits.tail) || protocol.ready
  protocol.valid := (!is_const || io.beat.bits.tail) && io.beat.valid

  def assign(i: UInt, sigs: Seq[Data]) = {
    var t = i
    for (s <- sigs.reverse) {
      s := t.asTypeOf(s.cloneType)
      t = t >> s.getWidth
    }
  }
  assign(const, const_fields)
  assign(io.beat.bits.payload, body_fields)

  when (io.beat.fire && io.beat.bits.head) { is_const := false.B; const_reg := io.beat.bits.payload }
  when (io.beat.fire && io.beat.bits.tail) { is_const := true.B }
}

class TLAToBeat(edgeIn: TLEdge, bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelToBeat(new TLBundleA(bundle), edgeIn, nameSuffix)(p) {
  has_body := edgeIn.hasData(protocol.bits) || (~protocol.bits.mask =/= 0.U)
}

class TLAFromBeat(bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelFromBeat(new TLBundleA(bundle), nameSuffix)(p) {
  when (io.beat.bits.head) { io.protocol.bits.mask := ~(0.U(io.protocol.bits.mask.getWidth.W)) }
}

class TLBToBeat(edgeOut: TLEdge, bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelToBeat(new TLBundleB(bundle), edgeOut, nameSuffix)(p) {
  has_body := edgeOut.hasData(protocol.bits) || (~protocol.bits.mask =/= 0.U)
}

class TLBFromBeat(bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelFromBeat(new TLBundleB(bundle), nameSuffix)(p) {
  when (io.beat.bits.head) { io.protocol.bits.mask := ~(0.U(io.protocol.bits.mask.getWidth.W)) }
}

class TLCToBeat(edgeIn: TLEdge, bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelToBeat(new TLBundleC(bundle), edgeIn, nameSuffix)(p) {
  has_body := edgeIn.hasData(protocol.bits)
}

class TLCFromBeat(bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelFromBeat(new TLBundleC(bundle), nameSuffix)(p)

class TLDToBeat(edgeOut: TLEdge, bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelToBeat(new TLBundleD(bundle), edgeOut, nameSuffix)(p) {
  has_body := edgeOut.hasData(protocol.bits)
}

class TLDFromBeat(bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelFromBeat(new TLBundleD(bundle), nameSuffix)(p)

class TLEToBeat(edgeIn: TLEdge, bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelToBeat(new TLBundleE(bundle), edgeIn, nameSuffix)(p) {
  has_body := edgeIn.hasData(protocol.bits)
}

class TLEFromBeat(bundle: TLBundleParameters, nameSuffix: Option[String])(implicit p: Parameters) extends TLChannelFromBeat(new TLBundleE(bundle), nameSuffix)(p)
