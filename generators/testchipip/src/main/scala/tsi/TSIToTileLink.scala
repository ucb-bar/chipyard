package testchipip.tsi

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import testchipip.serdes._

object TSI {
  val WIDTH = 32 // hardcoded in FESVR
}

class TSIIO extends SerialIO(TSI.WIDTH)

object TSIIO {
  def apply(ser: SerialIO): TSIIO = {
    require(ser.w == TSI.WIDTH)
    val wire = Wire(new TSIIO)
    wire <> ser
    wire
  }
}

class TSIToTileLink(sourceIds: Int = 1)(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "serial", sourceId = IdRange(0, sourceIds))))))

  lazy val module = new TSIToTileLinkModule(this)
}

class TSIToTileLinkModule(outer: TSIToTileLink) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val tsi = new TSIIO
    val state = Output(UInt())
  })

  val (mem, edge) = outer.node.out(0)

  require (edge.manager.minLatency > 0)

  val pAddrBits = edge.bundle.addressBits
  val wordLen = 64
  val nChunksPerWord = wordLen / TSI.WIDTH
  val dataBits = mem.params.dataBits
  val beatBytes = dataBits / 8
  val nChunksPerBeat = dataBits / TSI.WIDTH
  val byteAddrBits = log2Ceil(beatBytes)

  require(nChunksPerWord > 0, s"Serial interface width must be <= $wordLen")

  val cmd = Reg(UInt(TSI.WIDTH.W))
  val addr = Reg(UInt(wordLen.W))
  val len = Reg(UInt(wordLen.W))
  val body = Reg(Vec(nChunksPerBeat, UInt(TSI.WIDTH.W)))
  val bodyValid = Reg(UInt(nChunksPerBeat.W))
  val idx = Reg(UInt(log2Up(nChunksPerBeat).W))

  val (cmd_read :: cmd_write :: Nil) = Enum(2)
  val (s_cmd :: s_addr :: s_len ::
       s_read_req  :: s_read_data :: s_read_body ::
       s_write_body :: s_write_data :: s_write_ack :: Nil) = Enum(9)
  val state = RegInit(s_cmd)
  io.state := state

  io.tsi.in.ready := state.isOneOf(s_cmd, s_addr, s_len, s_write_body)
  io.tsi.out.valid := state === s_read_body
  io.tsi.out.bits := body(idx)

  val beatAddr = addr(pAddrBits - 1, byteAddrBits)
  val nextAddr = Cat(beatAddr + 1.U, 0.U(byteAddrBits.W))

  val wmask = FillInterleaved(TSI.WIDTH/8, bodyValid)
  val addr_size = nextAddr - addr
  val len_size = Cat(len + 1.U, 0.U(log2Ceil(TSI.WIDTH/8).W))
  val raw_size = Mux(len_size < addr_size, len_size, addr_size)
  val rsize = MuxLookup(raw_size, byteAddrBits.U)(
    (0 until log2Ceil(beatBytes)).map(i => ((1 << i).U -> i.U)))

  val pow2size = PopCount(raw_size) === 1.U
  val byteAddr = Mux(pow2size, addr(byteAddrBits - 1, 0), 0.U)

  val put_acquire = edge.Put(
    0.U, beatAddr << byteAddrBits.U, log2Ceil(beatBytes).U,
    body.asUInt, wmask)._2

  val get_acquire = edge.Get(
    0.U, Cat(beatAddr, byteAddr), rsize)._2

  mem.a.valid := state.isOneOf(s_write_data, s_read_req)
  mem.a.bits := Mux(state === s_write_data, put_acquire, get_acquire)
  mem.b.ready := false.B
  mem.c.valid := false.B
  mem.d.ready := state.isOneOf(s_write_ack, s_read_data)
  mem.e.valid := false.B

  def shiftBits(bits: UInt, idx: UInt): UInt =
    if (nChunksPerWord > 1)
      bits << Cat(idx(log2Ceil(nChunksPerWord) - 1, 0), 0.U(log2Up(TSI.WIDTH).W))
    else bits

  def addrToIdx(addr: UInt): UInt =
    if (nChunksPerBeat > 1) addr(byteAddrBits - 1, log2Up(TSI.WIDTH/8)) else 0.U

  when (state === s_cmd && io.tsi.in.valid) {
    cmd := io.tsi.in.bits
    idx := 0.U
    addr := 0.U
    len := 0.U
    state := s_addr
  }

  when (state === s_addr && io.tsi.in.valid) {
    addr := addr | shiftBits(io.tsi.in.bits, idx)
    idx := idx + 1.U
    when (idx === (nChunksPerWord - 1).U) {
      idx := 0.U
      state := s_len
    }
  }

  when (state === s_len && io.tsi.in.valid) {
    len := len | shiftBits(io.tsi.in.bits, idx)
    idx := idx + 1.U
    when (idx === (nChunksPerWord - 1).U) {
      idx := addrToIdx(addr)
      when (cmd === cmd_write) {
        bodyValid := 0.U
        state := s_write_body
      } .elsewhen (cmd === cmd_read) {
        state := s_read_req
      } .otherwise {
        assert(false.B, "Bad TSI command")
      }
    }
  }

  when (state === s_read_req && mem.a.ready) {
    state := s_read_data
  }

  when (state === s_read_data && mem.d.valid) {
    body := mem.d.bits.data.asTypeOf(body)
    idx := addrToIdx(addr)
    addr := nextAddr
    state := s_read_body
  }

  when (state === s_read_body && io.tsi.out.ready) {
    idx := idx + 1.U
    len := len - 1.U
    when (len === 0.U) { state := s_cmd }
    .elsewhen (idx === (nChunksPerBeat - 1).U) { state := s_read_req }
  }

  when (state === s_write_body && io.tsi.in.valid) {
    body(idx) := io.tsi.in.bits
    bodyValid := bodyValid | UIntToOH(idx)
    when (idx === (nChunksPerBeat - 1).U || len === 0.U) {
      state := s_write_data
    } .otherwise {
      idx := idx + 1.U
      len := len - 1.U
    }
  }

  when (state === s_write_data && mem.a.ready) {
    state := s_write_ack
  }

  when (state === s_write_ack && mem.d.valid) {
    when (len === 0.U) {
      state := s_cmd
    } .otherwise {
      addr := nextAddr
      len := len - 1.U
      idx := 0.U
      bodyValid := 0.U
      state := s_write_body
    }
  }
}
