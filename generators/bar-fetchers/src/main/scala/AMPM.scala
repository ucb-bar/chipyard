/* ---------------------------------------------------------------------
Access Map Pattern Matching

Adapted from "Access map pattern matching for data cache prefetch"

Yasuo Ishii, Mary Inaba, and Kei Hiraki. 2009. Access map pattern 
matching for data cache prefetch. In Proceedings of the 23rd 
international conference on Supercomputing (ICS '09). Association for 
Computing Machinery, New York, NY, USA, 499–500. 
https://doi.org/10.1145/1542275.1542349
--------------------------------------------------------------------- */

package barf

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{CacheBlockBytes}

case class SingleAMPMPrefetcherParams(
  entries: Int = 64,    //Number of entries per access map
  N: Int = 16,          //Number of cache lines per zone
) extends CanInstantiatePrefetcher {
  def desc() = "Single AMPM Prefetcher"
  def instantiate()(implicit p: Parameters) = Module(new AMPMPrefetcher(this)(p))
}

class AMPMPrefetcher(params: SingleAMPMPrefetcherParams)(implicit p: Parameters) extends AbstractPrefetcher()(p) {
    val entries_bits = log2Ceil(params.entries)
    val line_bits = log2Ceil(params.N)
    val max_k = if (params.N % 2 == 0) { params.N / 2 } else { (params.N - 1) / 2}
    
    //----------------- memory access map table -----------------
    val snoop_tag = io.snoop.bits.block >> (line_bits)
    val snoop_offset = io.snoop.bits.block(line_bits - 1, 0)
    val memory_access_map_table = Reg(Valid(new MemoryAccessMapBank(entries = params.entries, N = params.N)))
    val tag_candidate = Wire(UInt(params.N.W))
    val prefetch_input = Wire(UInt(params.N.W))
    val entry_existence_vec = Wire(Vec(params.entries, Bool()))
    val entry_existence = Wire(Bool())
    val initial_state_vec = Wire(Vec(params.N, Bool())) 
    val head = RegInit(0.U((entries_bits).W))
    val already_accessed = Wire(Bool())

    //Set initial state for new maps
    for (i <- 0 until params.N) {
      initial_state_vec(i) := snoop_offset === i.U
    }

    //Check table for map
    tag_candidate := 0.U
    val map_bank = memory_access_map_table
    for (j <- 0 until params.entries) {
      val map = map_bank.bits.maps(j)
      //found entry -> send to prefetch generator
      when (io.snoop.valid && map.valid && (map.bits.tag === snoop_tag)) {
        tag_candidate := map.bits.states.asUInt
        map.bits.states(snoop_offset) := true.B //set map entry to "accessed"
        entry_existence_vec(j) := true.B
      } .otherwise {
        entry_existence_vec(j) := false.B
      }
    }

    entry_existence := entry_existence_vec.reduce(_ || _)
    //if entry not in table, add new entry
    when (!entry_existence && io.snoop.valid) {
      memory_access_map_table.bits.maps(head).bits.states := initial_state_vec
      memory_access_map_table.bits.maps(head).bits.tag := snoop_tag
      memory_access_map_table.bits.maps(head).valid := true.B
      head := head + 1.U
    }
    
    prefetch_input := tag_candidate
    already_accessed := prefetch_input(snoop_offset)

    //pattern matching
    val forward_map = Wire(UInt((params.N).W))
    val backward_map = Wire(UInt((params.N).W))
    val logic_pos = Wire(Vec(max_k, UInt()))
    val logic_neg = Wire(Vec(max_k, UInt()))
    val and_back_pos = Wire(Vec(max_k, UInt()))
    val and_back_pos_uint = Wire(UInt(max_k.W))
    val and_back_neg = Wire(Vec(max_k, UInt()))
    val and_back_neg_uint = Wire(UInt(max_k.W))
    val delta_uint = Wire(UInt(max_k.W))

    forward_map := Reverse(prefetch_input << (params.N.U - snoop_offset - 1.U))
    backward_map := prefetch_input >> snoop_offset //should be unsigned

    for (k <- 0 until max_k) {
      logic_pos(k) := forward_map(k) & (forward_map(2*k) | forward_map((2*k)+1))
      logic_neg(k) := backward_map(k) & (backward_map(2*k) | backward_map((2*k)+1))
    }
    for (i <- 0 until max_k) {
      and_back_pos(i) := logic_pos(i) & !backward_map(i)
      and_back_neg(i) := logic_neg(i) & !forward_map(i)
    }
    and_back_pos_uint := and_back_pos.asUInt
    and_back_neg_uint := and_back_neg.asUInt
    //Prioritize positive delta
    delta_uint := Mux(and_back_pos_uint =/= 0.U, and_back_pos_uint, and_back_neg_uint)

    //Buffer prefetches using queue
    val pref_out = Reg(Output(Flipped(Decoupled(new prefetch_req))))
    val queue_out = Reg(Input(Flipped(Decoupled(new prefetch_req))))
    val prefetch_active = RegInit(false.B)
    val reset_deq = RegInit(true.B) 
    val prefetch_queue = Module(new Queue(new prefetch_req, entries=8, flow=true))

    prefetch_queue.io.enq <> pref_out
    prefetch_queue.io.deq <> queue_out

    //Add generated prefetch to queue
    when (delta_uint =/= 0.U && io.snoop.valid && !already_accessed) {
      when (and_back_pos_uint =/= 0.U) { 
        //Positive delta was selected
        pref_out.bits.addr := io.snoop.bits.block_address + (PriorityEncoder(delta_uint) << log2Up(io.snoop.bits.blockBytes))
      } .otherwise {
        //Negative delta was selected
        pref_out.bits.addr := io.snoop.bits.block_address - (PriorityEncoder(delta_uint) << log2Up(io.snoop.bits.blockBytes))
      }
      pref_out.bits.write := io.snoop.bits.write
      pref_out.valid := true.B
      prefetch_active := true.B
    } .otherwise {
      pref_out.valid := false.B
    }

    io.request.bits.address := prefetch_queue.io.deq.bits.addr
    io.request.valid := prefetch_active
    io.request.bits.write := prefetch_queue.io.deq.bits.write

    //Dequeue
    when (io.request.fire) {
      prefetch_queue.io.deq.ready := true.B
      reset_deq := true.B
      prefetch_active := false.B
    }

    when (reset_deq) {
      //reset dequeue ready
      prefetch_queue.io.deq.ready := false.B
      reset_deq := false.B
    }
}


//N is num cache lines per zone
class MemoryAccessMap(val N: Int) extends Bundle {
  // false -> init, true -> access
  val states = Vec(N, Bool())
  val tag = UInt() // top bits of mem address
}

class MemoryAccessMapBank(val entries: Int, val N: Int) extends Bundle {
  val maps = Vec(entries, Valid(new MemoryAccessMap(N)))
}

class prefetch_req extends Bundle {
  val addr = UInt()
  val write = Bool()
}
