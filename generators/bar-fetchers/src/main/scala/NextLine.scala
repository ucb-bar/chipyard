package barf

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{CacheBlockBytes}

case class SingleNextLinePrefetcherParams(
  ahead: Int = 4,
  waitForHit: Boolean = false,
  handleVA: Boolean = false
) extends CanInstantiatePrefetcher {
  def desc() = "Single Next-Line Prefetcher"
  def instantiate()(implicit p: Parameters) = Module(new SingleNextLinePrefetcher(this)(p))
}

class SingleNextLinePrefetcher(params: SingleNextLinePrefetcherParams)(implicit p: Parameters) extends AbstractPrefetcher()(p) {
  // Assume 4KB pages
  val lowerBits = 12 - log2Ceil(p(CacheBlockBytes))
  val s_idle :: s_wait :: s_active :: s_done :: Nil = Enum(4)
  val state = RegInit(s_idle)
  val write = Reg(Bool())
  val block_upper = Reg(UInt())
  val block_lower = Reg(UInt(lowerBits.W))
  val prefetch = Reg(UInt(lowerBits.W))
  val wrap = RegInit(false.B)
  val wrap_block_upper = block_upper + 1.U
  val delta = Mux(wrap, Cat(1.U(1.W), prefetch) - block_lower, prefetch - block_lower)

  val addr_hit = if (params.handleVA) {
    (io.snoop.bits.block >= Cat(block_upper, block_lower)) &&
    (io.snoop.bits.block <= Cat(Mux(wrap, wrap_block_upper, block_upper), prefetch))
  } else {
    (block_upper === io.snoop.bits.block >> lowerBits) &&
    (io.snoop.bits.block(lowerBits-1,0) >= block_lower) &&
    (io.snoop.bits.block(lowerBits-1,0) <= prefetch)
  }
  io.hit := state =/= s_idle && addr_hit

  val snoop_next_block = (io.snoop.bits.block(lowerBits-1,0) + 1.U)(lowerBits-1,0)

  when ((state === s_idle || (state === s_wait && !io.hit)) && io.snoop.valid) {
    when (~io.snoop.bits.block(lowerBits-1,0) =/= 0.U || params.handleVA.B) {
      state := (if (params.waitForHit) s_wait else s_active)
    }
    block_upper := io.snoop.bits.block >> lowerBits
    block_lower := io.snoop.bits.block
    prefetch := snoop_next_block
    when (params.handleVA.B && !io.hit) {
      wrap := snoop_next_block === 0.U
    }
    write := io.snoop.bits.write
  }

  io.request.valid := state === s_active
  io.request.bits.write := write
  io.request.bits.address := Cat(Mux(wrap, wrap_block_upper, block_upper), prefetch) << log2Up(io.request.bits.blockBytes)


  when (io.request.fire) {
    prefetch := prefetch + 1.U
    when (prefetch === ~(0.U(lowerBits.W))) {
      if (params.handleVA) {
        state := Mux(delta >= params.ahead.U, s_wait, s_active)
        prefetch := 0.U
        wrap := true.B
      } else {
        state := s_done
        prefetch := prefetch
      }
    } .elsewhen (delta >= params.ahead.U) {
      state := s_wait
    } .otherwise {
      state := s_active
    }
  }
  when (state === s_done && block_lower === prefetch) {
    state := s_idle
  }

  when (io.hit && io.snoop.valid) {
    when (state =/= s_done && io.snoop.bits.block =/= Cat(block_upper, block_lower)) {
      state := s_active
    }
    write := io.snoop.bits.write
    block_lower := io.snoop.bits.block
    block_upper := io.snoop.bits.block >> lowerBits
    when (io.snoop.bits.block(lowerBits-1,0) === prefetch) {
      prefetch := prefetch + 1.U
    }
    when (wrap && ((io.snoop.bits.block >> lowerBits) =/= block_upper)) {
      wrap := false.B
    }
  }
}


case class MultiNextLinePrefetcherParams(
  singles: Seq[SingleNextLinePrefetcherParams] = Seq.fill(4) { SingleNextLinePrefetcherParams() },
  handleVA: Boolean = false
) extends CanInstantiatePrefetcher {
  def desc() = "Multi Next-Line Prefetcher"
  def instantiate()(implicit p: Parameters) = Module(new MultiNextLinePrefetcher(this)(p))
}

class MultiNextLinePrefetcher(params: MultiNextLinePrefetcherParams)(implicit p: Parameters) extends AbstractPrefetcher()(p) {

  val singles = params.singles.map(_.copy(waitForHit=true, handleVA=params.handleVA).instantiate())
  val any_hit = singles.map(_.io.hit).reduce(_||_)
  singles.foreach(_.io.snoop.valid := false.B)
  singles.foreach(_.io.snoop.bits := io.snoop.bits)

  val replacer = ReplacementPolicy.fromString("lru", singles.size)
  when (io.snoop.valid) {
    for (s <- 0 until singles.size) {
      when (singles(s).io.hit || (!any_hit && replacer.way === s.U)) {
        singles(s).io.snoop.valid := true.B
        replacer.miss
      }
      when (singles(s).io.hit) {
        replacer.access(s.U)
      }
    }
  }
  val arb = Module(new RRArbiter(new Prefetch, singles.size))
  arb.io.in <> singles.map(_.io.request)
  io.request <> arb.io.out
}
