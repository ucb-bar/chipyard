package shuttle.dmem

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile.{TileKey}

class L1MetadataArrayBank[T <: L1Metadata](onReset: () => T, nSets: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val rstVal = onReset()
  val io = IO(new Bundle {
    val read = Flipped(Decoupled(new L1MetaReadReq))
    val write = Flipped(Decoupled(new L1MetaWriteReq))
    val resp = Vec(nWays, Output(rstVal.cloneType))
  })
  val rst_cnt = RegInit(0.U(log2Up(nSets+1).W))
  val rst = rst_cnt < nSets.U
  val waddr = Mux(rst, rst_cnt, io.write.bits.idx)
  val wdata = Mux(rst, rstVal, io.write.bits.data).asUInt
  val wmask = Mux(rst || (nWays == 1).B, ~(0.U(nWays.W)), io.write.bits.way_en).asBools
  val rmask = Mux(rst || (nWays == 1).B, ~(0.U(nWays.W)), io.read.bits.way_en).asBools
  when (rst) { rst_cnt := rst_cnt + 1.U }

  val metabits = rstVal.getWidth
  val tag_array = SyncReadMem(nSets, Vec(nWays, UInt(metabits.W)))

  val wen = Wire(Bool())
  val ren = Wire(Bool())
  val stall_ctr = RegInit(0.U(2.W))
  val stall_read = stall_ctr === ~(0.U(2.W))
  val force_stall = WireInit(false.B)

  val rbuf_valid = RegInit(false.B)
  val rbuf_idx = Reg(UInt(log2Ceil(nSets).W))
  val rbuf = Reg(io.resp.cloneType)
  val forward_from_rbuf = WireInit(false.B)

  when (io.read.valid && force_stall) {
    stall_ctr := stall_ctr + 1.U
  } .otherwise {
    stall_ctr := 0.U
  }

  when (rst) {
    wen := true.B
    ren := false.B
    io.read.ready := false.B
    io.write.ready := false.B
  } .otherwise {
    when (io.read.valid && !stall_read) {
      when (io.read.bits.idx === rbuf_idx && rbuf_valid) {
        forward_from_rbuf := true.B
        ren := false.B
        wen := io.write.valid
        io.read.ready := true.B
        io.write.ready := true.B
      } .otherwise {
        ren := true.B
        wen := false.B
        io.read.ready := true.B
        io.write.ready := false.B
        force_stall := io.write.valid
      }
    } .otherwise {
      ren := false.B
      wen := io.write.valid
      io.read.ready := false.B
      io.write.ready := true.B
    }
  }
  val s1_read_idx = RegEnable(io.read.bits.idx, io.read.valid)
  when (RegNext(io.read.fire && !forward_from_rbuf) && !(io.write.valid && io.write.bits.idx === s1_read_idx)) {
    rbuf_valid := true.B
    rbuf_idx := s1_read_idx
    rbuf := io.resp
  }
  when (io.write.valid && io.write.bits.idx === rbuf_idx) {
    rbuf_valid := false.B
  }

  when (wen) {
    tag_array.write(waddr, VecInit(Seq.fill(nWays) { wdata }), wmask)
  }
  io.resp := tag_array.read(io.read.bits.idx, ren && !wen).map(_.asTypeOf(rstVal))
  when (RegNext(forward_from_rbuf)) {
    io.resp := RegEnable(rbuf, forward_from_rbuf)
  }
}


class L1MetadataArrayBanked[T <: L1Metadata](onReset: () => T, nBanks: Int, nReadPorts: Int, nWritePorts: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val rstVal = onReset()
  val io = IO(new Bundle {
    val read = Flipped(Vec(nReadPorts, Decoupled(new L1MetaReadReq)))
    val write = Flipped(Vec(nWritePorts, Decoupled(new L1MetaWriteReq)))
    val resp = Vec(nReadPorts, Vec(nWays, Output(rstVal.cloneType)))
  })
  // No banking, duplicate the arrays
  if (nBanks == 0) {
    val arrays = Seq.fill(nReadPorts) { Module(new L1MetadataArrayBank(onReset, nSets)) }
    (io.read zip arrays).map { case (r,a) => a.io.read <> r }
    (io.resp zip arrays).map { case (r,a) => r <> a.io.resp }
    val write_arb = Module(new Arbiter(new L1MetaWriteReq, nWritePorts))
    write_arb.io.in <> io.write
    write_arb.io.out.ready := arrays.map(_.io.write.ready).reduce(_&&_)
    arrays.foreach(_.io.write.bits := write_arb.io.out.bits)
    arrays.zipWithIndex.foreach { case (a,i) =>
      a.io.write.valid := write_arb.io.out.valid && arrays.patch(i, Nil, 1).map(_.io.write.ready).reduce(_&&_)
    }
  } else if (nBanks == 1) {
    val array = Module(new L1MetadataArrayBank(onReset, nSets))
    val arb = Module(new Arbiter(new L1MetaReadReq, nReadPorts))
    arb.io.in <> io.read
    array.io.read <> arb.io.out
    io.resp.foreach(_ <> array.io.resp)
    val write_arb = Module(new Arbiter(new L1MetaWriteReq, nWritePorts))
    write_arb.io.in <> io.write
    array.io.write <> write_arb.io.out
  } else {
    val bankBits = log2Ceil(nBanks)
    val arrays = Seq.fill(nBanks) { Module(new L1MetadataArrayBank(onReset, nSets/nBanks)) }
    val arbs = Seq.fill(nBanks) { Module(new Arbiter(new L1MetaReadReq, nReadPorts)) }
    val write_arbs = Seq.fill(nBanks) { Module(new Arbiter(new L1MetaWriteReq, nWritePorts)) }
    io.read.foreach(_.ready := false.B)
    io.resp.foreach(_ := DontCare)
    for (i <- 0 until nReadPorts) {
      for (b <- 0 until nBanks) {
        arbs(b).io.in(i).valid := io.read(i).valid && io.read(i).bits.idx(bankBits-1,0) === b.U
        arbs(b).io.in(i).bits := io.read(i).bits
        arbs(b).io.in(i).bits.idx := io.read(i).bits.idx >> bankBits
        when (io.read(i).bits.idx(bankBits-1,0) === b.U) { io.read(i).ready := arbs(b).io.in(i).ready }
      }
      io.resp(i) := Mux1H(UIntToOH(RegNext(io.read(i).bits.idx(bankBits-1,0))), arrays.map(_.io.resp))
    }
    (arbs zip arrays).map { case (r,a) => a.io.read <> r.io.out }

    io.write.foreach(_.ready := false.B)
    for (i <- 0 until nWritePorts) {
      for (b <- 0 until nBanks) {
        write_arbs(b).io.in(i).valid := io.write(i).valid && io.write(i).bits.idx(bankBits-1,0) === b.U
        write_arbs(b).io.in(i).bits := io.write(i).bits
        write_arbs(b).io.in(i).bits.idx := io.write(i).bits.idx >> bankBits
        when (io.write(i).bits.idx(bankBits-1,0) === b.U) { io.write(i).ready := write_arbs(b).io.in(i).ready }
      }
    }
    (write_arbs zip arrays).map { case (r,a) => a.io.write <> r.io.out }
  }
}
