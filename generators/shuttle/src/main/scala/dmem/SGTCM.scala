package shuttle.dmem

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.bundlebridge._
import org.chipsalliance.diplomacy.lazymodule._

import freechips.rocketchip.diplomacy.{AddressSet, RegionType, TransferSizes}
import freechips.rocketchip.resources.{Device, DeviceRegName, DiplomaticSRAM, HasJustOneSeqMem}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

import shuttle.common.{TCMParams}


case class ShuttleSGTCMParams(
  base: BigInt,
  size: BigInt,
  banks: Int) extends TCMParams

class SGTCM(
  address: AddressSet,
  beatBytes: Int = 4,
  val devName: Option[String] = None,
  val devOverride: Option[Device with DeviceRegName] = None
)(implicit p: Parameters) extends DiplomaticSRAM(address, beatBytes, devName, None, devOverride) {
  require (beatBytes >= 1 && isPow2(beatBytes))
  require (address.contiguous)

  val node = TLManagerNode(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address            = List(address),
      resources          = resources,
      regionType         = RegionType.IDEMPOTENT,
      executable         = false,
      supportsGet        = TransferSizes(1, beatBytes),
      supportsPutPartial = TransferSizes(1, beatBytes),
      supportsPutFull    = TransferSizes(1, beatBytes),
      supportsArithmetic = TransferSizes.none,
      supportsLogical    = TransferSizes.none,
      fifoId             = Some(0)).v2copy(name=devName)), // requests are handled in order
    beatBytes  = beatBytes,
    minLatency = 1))) // no bypass needed for this device

  val sgnode = TLManagerNode(Seq.tabulate(beatBytes) { i => TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address            = List(AddressSet(address.base + i, address.mask - (beatBytes - 1))),
      resources          = resources,
      regionType         = RegionType.IDEMPOTENT,
      executable         = false,
      supportsGet        = TransferSizes(1, 1),
      supportsPutPartial = TransferSizes(1, 1),
      supportsPutFull    = TransferSizes(1, 1),
      supportsArithmetic = TransferSizes.none,
      supportsLogical    = TransferSizes.none,
      fifoId             = Some(0)).v2copy(name=devName)), // requests are handled in order
    beatBytes = 1,
    minLatency = 1)
  })

  private val outer = this

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {

    val (in, edge) = node.in(0)
    val (sgin, sgedge) = sgnode.in.unzip

    val indexBits = (outer.address.mask & ~(beatBytes-1)).bitCount
    val mem = Seq.fill(beatBytes) { SyncReadMem(
      BigInt(1) << indexBits,
      UInt(8.W))
    }

    // R stage registers from A
    val r_full      = RegInit(false.B)
    val r_sg_full   = RegInit(VecInit.fill(beatBytes)(false.B))
    val r_size      = Reg(UInt(edge.bundle.sizeBits.W))
    val r_source    = Reg(UInt(edge.bundle.sourceBits.W))
    val r_sg_source = Reg(Vec(beatBytes, UInt(sgedge.map(_.bundle.sourceBits).max.W)))
    val r_read      = Reg(Bool())
    val r_sg_read   = Reg(Vec(beatBytes, Bool()))
    val r_raw_data  = Wire(Vec(beatBytes, UInt(8.W)))

    in.d.bits.opcode  := Mux(r_read, TLMessages.AccessAckData, TLMessages.AccessAck)
    in.d.bits.param   := 0.U
    in.d.bits.size    := r_size
    in.d.bits.source  := r_source
    in.d.bits.sink    := 0.U
    in.d.bits.denied  := false.B
    in.d.bits.data    := r_raw_data.asUInt
    in.d.bits.corrupt := false.B

    in.d.valid := r_full
    in.a.ready := (!r_full || in.d.ready) && (!r_sg_full.orR || sgin.map(_.d.ready).andR)
    when (in.d.ready) { r_full := false.B }


    for (i <- 0 until beatBytes) {
      sgin(i).d.bits.opcode := Mux(r_sg_read(i), TLMessages.AccessAckData, TLMessages.AccessAck)
      sgin(i).d.bits.param  := 0.U
      sgin(i).d.bits.size   := 0.U
      sgin(i).d.bits.source := r_sg_source(i)
      sgin(i).d.bits.sink   := 0.U
      sgin(i).d.bits.denied := false.B
      sgin(i).d.bits.data   := r_raw_data(i)
      sgin(i).d.bits.corrupt := false.B

      sgin(i).d.valid       := r_sg_full(i)
      sgin(i).a.ready := !in.a.valid && (!r_full || in.d.ready) && (!r_sg_full(i) || sgin(i).d.ready)
      when (sgin(i).d.ready) { r_sg_full(i) := false.B }
    }

    when (in.a.fire) {
      r_full     := true.B
      r_size     := in.a.bits.size
      r_source   := in.a.bits.source
      r_read     := in.a.bits.opcode === TLMessages.Get
    }
    for (i <- 0 until beatBytes) {
      when (sgin(i).a.fire) {
        r_sg_full(i)   := true.B
        r_sg_source(i) := sgin(i).a.bits.source
        r_sg_read(i)   := sgin(i).a.bits.opcode === TLMessages.Get
      }
    }

    // SRAM arbitration
    for (i <- 0 until beatBytes) {
      val read = Mux(in.a.valid, in.a.bits.opcode, sgin(i).a.bits.opcode) === TLMessages.Get
      val index = Mux(in.a.valid, in.a.bits.address, sgin(i).a.bits.address) >> log2Ceil(beatBytes)
      val data = Mux(in.a.valid, in.a.bits.data(8*(i+1)-1, 8*i), sgin(i).a.bits.data)

      val wen = ((in.a.fire && in.a.bits.mask(i)) || sgin(i).a.fire) && !read
      val ren = !wen && (in.a.fire || sgin(i).a.fire)

      r_raw_data(i) := mem(i).read(index, ren) holdUnless RegNext(ren)
      when (wen) { mem(i).write(index, data) }
    }

    // Tie off unused channels
    in.b.valid := false.B
    in.c.ready := true.B
    in.e.ready := true.B
    sgin.foreach(_.b.valid := false.B)
    sgin.foreach(_.c.ready := true.B)
    sgin.foreach(_.e.ready := true.B)
  }
}
