package shuttle.dmem

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._

class DataArrayBank(bankId: Int, nBanks: Int, singlePorted: Boolean)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val read = Flipped(Decoupled(new L1DataReadReq))
    val write = Flipped(Decoupled(new L1DataWriteReq))
    val resp = Vec(nWays, Output(UInt(encRowBits.W)))
  })
  def bankIdx(addr: UInt): UInt = {
    if (nBanks == 1) 0.U(1.W) else (addr >> log2Ceil(rowWords * wordBytes))(log2Ceil(nBanks)-1,0)
  }

  val bankSize = nSets * refillCycles / nBanks
  val bankBits = log2Ceil(nBanks)
  val bankOffBits = log2Ceil(rowWords) + log2Ceil(wordBytes)
  val bidxBits = log2Ceil(bankSize)
  val bidxOffBits = bankOffBits + bankBits

  val array = DescribedSRAM(
    name = s"array_$bankId",
    desc = "Banked HellaCache Array",
    size = bankSize,
    data = Vec(nWays * rowWords, UInt(encDataBits.W))
  )

  val wen = io.write.valid
  val ren = io.read.valid

  io.write.ready := true.B
  io.read.ready := true.B
  if (singlePorted) {
    io.read.ready := !wen
    io.resp := array.read(io.read.bits.addr >> bidxOffBits, ren && !wen).asTypeOf(Vec(nWays, UInt(encRowBits.W)))
  } else {
    io.resp := array.read(io.read.bits.addr >> bidxOffBits, ren).asTypeOf(Vec(nWays, UInt(encRowBits.W)))
  }
  when (wen) {
    val data = Wire(Vec(nWays, UInt(encRowBits.W)))
    data.foreach(_ := io.write.bits.data.asUInt)
    val wmask =  Cat((0 until nWays).map { w => Mux(io.write.bits.way_en(w), io.write.bits.wmask, 0.U(rowWords.W)) }.reverse).asBools
    array.write(io.write.bits.addr >> bidxOffBits, data.asTypeOf(Vec(nWays*rowWords, UInt(encDataBits.W))), wmask)
  }


  // val wbuf = Module(new Queue(new L1DataWriteReq, 1, pipe=true, flow=true))
  // wbuf.io.enq <> io.write

  // val wen = WireInit(wbuf.io.deq.valid)
  // val ren = io.read.valid

  // wbuf.io.deq.ready := true.B
  // io.read.ready := true.B

  // if (singlePorted) {
  //   io.read.ready := !wen
  //   io.resp := array.read(io.read.bits.addr >> bidxOffBits, ren && !wen).asTypeOf(Vec(nWays, UInt(encRowBits.W)))
  //   when (wbuf.io.count =/= 0.U) {
  //     when (io.read.bits.addr >> bidxOffBits === wbuf.io.deq.bits.addr >> bidxOffBits) {
  //       io.read.ready := false.B
  //     } .elsewhen (!io.write.valid && io.read.valid) {
  //       wen := false.B
  //       wbuf.io.deq.ready := false.B
  //     }
  //   } .elsewhen (ren) {
  //     wen := false.B
  //     wbuf.io.deq.ready := false.B
  //   }
  // } else {
  //   io.resp := array.read(io.read.bits.addr >> bidxOffBits, ren).asTypeOf(Vec(nWays, UInt(encRowBits.W)))
  // }

  // when (wen) {
  //   val data = Wire(Vec(nWays, UInt(encRowBits.W)))
  //   data.foreach(_ := wbuf.io.deq.bits.data.asUInt)
  //   val wmask = Cat((0 until nWays).map { w => Mux(wbuf.io.deq.bits.way_en(w), wbuf.io.deq.bits.wmask, 0.U(rowWords.W)) }.reverse).asBools
  //   array.write(wbuf.io.deq.bits.addr >> bidxOffBits, data.asTypeOf(Vec(nWays*rowWords, UInt(encDataBits.W))), wmask)
  // }
}
