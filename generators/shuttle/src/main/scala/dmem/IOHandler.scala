package shuttle.dmem

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._


class IOHandler(id: Int)(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ShuttleDMemReq))
    val resp = Decoupled(new ShuttleDMemResp)
    val mem_access = Decoupled(new TLBundleA(edge.bundle))
    val mem_ack = Flipped(Valid(new TLBundleD(edge.bundle)))
    val replay_next = Output(Bool())
    val store_pending = Output(Bool())
  })

  def beatOffset(addr: UInt) = addr.extract(beatOffBits - 1, wordOffBits)

  def wordFromBeat(addr: UInt, dat: UInt) = {
    val shift = Cat(beatOffset(addr), 0.U((wordOffBits + log2Up(wordBytes)).W))
    (dat >> shift)(wordBits - 1, 0)
  }

  val req = Reg(new ShuttleDMemReq)
  val grant_word = Reg(UInt(wordBits.W))

  val s_idle :: s_mem_access :: s_mem_ack :: s_resp :: Nil = Enum(4)
  val state = RegInit(s_idle)
  io.req.ready := (state === s_idle)

  val loadgen = new LoadGen(req.size, req.signed, req.addr, grant_word, false.B, wordBytes)
 
  val a_source = id.U
  val a_address = req.addr
  val a_size = req.size
  val a_data = Fill(beatWords, req.data)

  val get     = edge.Get(a_source, a_address, a_size)._2
  val put     = edge.Put(a_source, a_address, a_size, a_data)._2
  val atomics = if (edge.manager.anySupportLogical) {
    MuxLookup(req.cmd, (0.U).asTypeOf(new TLBundleA(edge.bundle)))(Array(
      M_XA_SWAP -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.SWAP)._2,
      M_XA_XOR  -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.XOR) ._2,
      M_XA_OR   -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.OR)  ._2,
      M_XA_AND  -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.AND) ._2,
      M_XA_ADD  -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.ADD)._2,
      M_XA_MIN  -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MIN)._2,
      M_XA_MAX  -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MAX)._2,
      M_XA_MINU -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MINU)._2,
      M_XA_MAXU -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MAXU)._2))
  } else {
    // If no managers support atomics, assert fail if processor asks for them
    assert(state === s_idle || !isAMO(req.cmd))
    (0.U).asTypeOf(new TLBundleA(edge.bundle))
  }
  assert(state === s_idle || req.cmd =/= M_XSC)

  io.mem_access.valid := (state === s_mem_access)
  io.mem_access.bits := Mux(isAMO(req.cmd), atomics, Mux(isRead(req.cmd), get, put))

  io.replay_next := (state === s_mem_ack) || io.resp.valid && !io.resp.ready
  io.resp.valid := (state === s_resp)
  io.resp.bits.tag := req.tag
  io.resp.bits.has_data := isRead(req.cmd)
  io.resp.bits.data := loadgen.data
  io.resp.bits.size := req.size
  io.store_pending := state =/= s_idle && isWrite(req.cmd)

  when (io.req.fire) {
    req := io.req.bits
    state := s_mem_access
  }

  when (io.mem_access.fire) {
    state := s_mem_ack
  }

  when (state === s_mem_ack && io.mem_ack.valid) {
    state := s_resp
    when (isRead(req.cmd)) {
      grant_word := wordFromBeat(req.addr, io.mem_ack.bits.data)
    }
  }

  when (io.resp.fire) {
    state := s_idle
  }
}
