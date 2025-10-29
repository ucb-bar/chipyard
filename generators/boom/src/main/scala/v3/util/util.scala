//******************************************************************************
// Copyright (c) 2015 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Utility Functions
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.util

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util.{Str}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tile.{TileKey}

import boom.v3.common.{MicroOp}
import boom.v3.exu.{BrUpdateInfo}

/**
 * Object to XOR fold a input register of fullLength into a compressedLength.
 */
object Fold
{
  def apply(input: UInt, compressedLength: Int, fullLength: Int): UInt = {
    val clen = compressedLength
    val hlen = fullLength
    if (hlen <= clen) {
      input
    } else {
      var res = 0.U(clen.W)
      var remaining = input.asUInt
      for (i <- 0 to hlen-1 by clen) {
        val len = if (i + clen > hlen ) (hlen - i) else clen
        require(len > 0)
        res = res(clen-1,0) ^ remaining(len-1,0)
        remaining = remaining >> len.U
      }
      res
    }
  }
}

/**
 * Object to check if MicroOp was killed due to a branch mispredict.
 * Uses "Fast" branch masks
 */
object IsKilledByBranch
{
  def apply(brupdate: BrUpdateInfo, uop: MicroOp): Bool = {
    return maskMatch(brupdate.b1.mispredict_mask, uop.br_mask)
  }

  def apply(brupdate: BrUpdateInfo, uop_mask: UInt): Bool = {
    return maskMatch(brupdate.b1.mispredict_mask, uop_mask)
  }
}

/**
 * Object to return new MicroOp with a new BR mask given a MicroOp mask
 * and old BR mask.
 */
object GetNewUopAndBrMask
{
  def apply(uop: MicroOp, brupdate: BrUpdateInfo)
    (implicit p: Parameters): MicroOp = {
    val newuop = WireInit(uop)
    newuop.br_mask := uop.br_mask & ~brupdate.b1.resolve_mask
    newuop
  }
}

/**
 * Object to return a BR mask given a MicroOp mask and old BR mask.
 */
object GetNewBrMask
{
   def apply(brupdate: BrUpdateInfo, uop: MicroOp): UInt = {
     return uop.br_mask & ~brupdate.b1.resolve_mask
   }

   def apply(brupdate: BrUpdateInfo, br_mask: UInt): UInt = {
     return br_mask & ~brupdate.b1.resolve_mask
   }
}

object UpdateBrMask
{
  def apply(brupdate: BrUpdateInfo, uop: MicroOp): MicroOp = {
    val out = WireInit(uop)
    out.br_mask := GetNewBrMask(brupdate, uop)
    out
  }
  def apply[T <: boom.v3.common.HasBoomUOP](brupdate: BrUpdateInfo, bundle: T): T = {
    val out = WireInit(bundle)
    out.uop.br_mask := GetNewBrMask(brupdate, bundle.uop.br_mask)
    out
  }
  def apply[T <: boom.v3.common.HasBoomUOP](brupdate: BrUpdateInfo, bundle: Valid[T]): Valid[T] = {
    val out = WireInit(bundle)
    out.bits.uop.br_mask := GetNewBrMask(brupdate, bundle.bits.uop.br_mask)
    out.valid := bundle.valid && !IsKilledByBranch(brupdate, bundle.bits.uop.br_mask)
    out
  }
}

/**
 * Object to check if at least 1 bit matches in two masks
 */
object maskMatch
{
  def apply(msk1: UInt, msk2: UInt): Bool = (msk1 & msk2) =/= 0.U
}

/**
 * Object to clear one bit in a mask given an index
 */
object clearMaskBit
{
  def apply(msk: UInt, idx: UInt): UInt = (msk & ~(1.U << idx))(msk.getWidth-1, 0)
}

/**
 * Object to shift a register over by one bit and concat a new one
 */
object PerformShiftRegister
{
  def apply(reg_val: UInt, new_bit: Bool): UInt = {
    reg_val := Cat(reg_val(reg_val.getWidth-1, 0).asUInt, new_bit.asUInt).asUInt
    reg_val
  }
}

/**
 * Object to shift a register over by one bit, wrapping the top bit around to the bottom
 * (XOR'ed with a new-bit), and evicting a bit at index HLEN.
 * This is used to simulate a longer HLEN-width shift register that is folded
 * down to a compressed CLEN.
 */
object PerformCircularShiftRegister
{
  def apply(csr: UInt, new_bit: Bool, evict_bit: Bool, hlen: Int, clen: Int): UInt = {
    val carry = csr(clen-1)
    val newval = Cat(csr, new_bit ^ carry) ^ (evict_bit << (hlen % clen).U)
    newval
  }
}

/**
 * Object to increment an input value, wrapping it if
 * necessary.
 */
object WrapAdd
{
  // "n" is the number of increments, so we wrap at n-1.
  def apply(value: UInt, amt: UInt, n: Int): UInt = {
    if (isPow2(n)) {
      (value + amt)(log2Ceil(n)-1,0)
    } else {
      val sum = Cat(0.U(1.W), value) + Cat(0.U(1.W), amt)
      Mux(sum >= n.U,
          sum - n.U,
          sum)
    }
  }
}

/**
 * Object to decrement an input value, wrapping it if
 * necessary.
 */
object WrapSub
{
  // "n" is the number of increments, so we wrap to n-1.
  def apply(value: UInt, amt: Int, n: Int): UInt = {
    if (isPow2(n)) {
       (value - amt.U)(log2Ceil(n)-1,0)
    } else {
      val v = Cat(0.U(1.W), value)
      val b = Cat(0.U(1.W), amt.U)
      Mux(value >= amt.U,
          value - amt.U,
          n.U - amt.U + value)
    }
  }
}

/**
 * Object to increment an input value, wrapping it if
 * necessary.
 */
object WrapInc
{
  // "n" is the number of increments, so we wrap at n-1.
  def apply(value: UInt, n: Int): UInt = {
    if (isPow2(n)) {
      (value + 1.U)(log2Ceil(n)-1,0)
    } else {
      val wrap = (value === (n-1).U)
      Mux(wrap, 0.U, value + 1.U)
    }
  }
}

/**
 * Object to decrement an input value, wrapping it if
 * necessary.
 */
object WrapDec
{
  // "n" is the number of increments, so we wrap at n-1.
  def apply(value: UInt, n: Int): UInt = {
    if (isPow2(n)) {
      (value - 1.U)(log2Ceil(n)-1,0)
    } else {
      val wrap = (value === 0.U)
      Mux(wrap, (n-1).U, value - 1.U)
    }
  }
}

/**
 * Object to mask off lower bits of a PC to align to a "b"
 * Byte boundary.
 */
object AlignPCToBoundary
{
  def apply(pc: UInt, b: Int): UInt = {
    // Invert for scenario where pc longer than b
    //   (which would clear all bits above size(b)).
    ~(~pc | (b-1).U)
  }
}

/**
 * Object to rotate a signal left by one
 */
object RotateL1
{
  def apply(signal: UInt): UInt = {
    val w = signal.getWidth
    val out = Cat(signal(w-2,0), signal(w-1))

    return out
  }
}

/**
 * Object to sext a value to a particular length.
 */
object Sext
{
  def apply(x: UInt, length: Int): UInt = {
    if (x.getWidth == length) return x
    else return Cat(Fill(length-x.getWidth, x(x.getWidth-1)), x)
  }
}

/**
 * Object to translate from BOOM's special "packed immediate" to a 32b signed immediate
 * Asking for U-type gives it shifted up 12 bits.
 */
object ImmGen
{
  import boom.v3.common.{LONGEST_IMM_SZ, IS_B, IS_I, IS_J, IS_S, IS_U}
  def apply(ip: UInt, isel: UInt): SInt = {
    val sign = ip(LONGEST_IMM_SZ-1).asSInt
    val i30_20 = Mux(isel === IS_U, ip(18,8).asSInt, sign)
    val i19_12 = Mux(isel === IS_U || isel === IS_J, ip(7,0).asSInt, sign)
    val i11    = Mux(isel === IS_U, 0.S,
                 Mux(isel === IS_J || isel === IS_B, ip(8).asSInt, sign))
    val i10_5  = Mux(isel === IS_U, 0.S, ip(18,14).asSInt)
    val i4_1   = Mux(isel === IS_U, 0.S, ip(13,9).asSInt)
    val i0     = Mux(isel === IS_S || isel === IS_I, ip(8).asSInt, 0.S)

    return Cat(sign, i30_20, i19_12, i11, i10_5, i4_1, i0).asSInt
  }
}

/**
 * Object to get the FP rounding mode out of a packed immediate.
 */
object ImmGenRm { def apply(ip: UInt): UInt = { return ip(2,0) } }

/**
 * Object to get the FP function fype from a packed immediate.
 * Note: only works if !(IS_B or IS_S)
 */
object ImmGenTyp { def apply(ip: UInt): UInt = { return ip(9,8) } }

/**
 * Object to see if an instruction is a JALR.
 */
object DebugIsJALR
{
  def apply(inst: UInt): Bool = {
    // TODO Chisel not sure why this won't compile
//    val is_jalr = rocket.DecodeLogic(inst, List(Bool(false)),
//                                     Array(
//                                     JALR -> Bool(true)))
    inst(6,0) === "b1100111".U
  }
}

/**
 * Object to take an instruction and output its branch or jal target. Only used
 * for a debug assert (no where else would we jump straight from instruction
 * bits to a target).
 */
object DebugGetBJImm
{
  def apply(inst: UInt): UInt = {
    // TODO Chisel not sure why this won't compile
    //val csignals =
    //rocket.DecodeLogic(inst,
    //                    List(Bool(false), Bool(false)),
    //      Array(
    //         BEQ     -> List(Bool(true ), Bool(false)),
    //         BNE     -> List(Bool(true ), Bool(false)),
    //         BGE     -> List(Bool(true ), Bool(false)),
    //         BGEU    -> List(Bool(true ), Bool(false)),
    //         BLT     -> List(Bool(true ), Bool(false)),
    //         BLTU    -> List(Bool(true ), Bool(false))
    //      ))
    //val is_br :: nothing :: Nil = csignals

    val is_br = (inst(6,0) === "b1100011".U)

    val br_targ = Cat(Fill(12, inst(31)), Fill(8,inst(31)), inst(7), inst(30,25), inst(11,8), 0.U(1.W))
    val jal_targ= Cat(Fill(12, inst(31)), inst(19,12), inst(20), inst(30,25), inst(24,21), 0.U(1.W))

    Mux(is_br, br_targ, jal_targ)
  }
}

/**
 * Object to return the lowest bit position after the head.
 */
object AgePriorityEncoder
{
  def apply(in: Seq[Bool], head: UInt): UInt = {
    val n = in.size
    val width = log2Ceil(in.size)
    val n_padded = 1 << width
    val temp_vec = (0 until n_padded).map(i => if (i < n) in(i) && i.U >= head else false.B) ++ in
    val idx = PriorityEncoder(temp_vec)
    idx(width-1, 0) //discard msb
  }
}

/**
  * Object to determine whether queue
  * index i0 is older than index i1.
 */
object IsOlder
{
  def apply(i0: UInt, i1: UInt, head: UInt) = ((i0 < i1) ^ (i0 < head) ^ (i1 < head))
}

/**
 * Set all bits at or below the highest order '1'.
 */
object MaskLower
{
  def apply(in: UInt) = {
    val n = in.getWidth
    (0 until n).map(i => in >> i.U).reduce(_|_)
  }
}

/**
 * Set all bits at or above the lowest order '1'.
 */
object MaskUpper
{
  def apply(in: UInt) = {
    val n = in.getWidth
    (0 until n).map(i => (in << i.U)(n-1,0)).reduce(_|_)
  }
}

/**
 * Transpose a matrix of Chisel Vecs.
 */
object Transpose
{
  def apply[T <: chisel3.Data](in: Vec[Vec[T]]) = {
    val n = in(0).size
    VecInit((0 until n).map(i => VecInit(in.map(row => row(i)))))
  }
}

/**
  * N-wide one-hot priority encoder.
 */
object SelectFirstN
{
  def apply(in: UInt, n: Int) = {
    val sels = Wire(Vec(n, UInt(in.getWidth.W)))
    var mask = in

    for (i <- 0 until n) {
      sels(i) := PriorityEncoderOH(mask)
      mask = mask & ~sels(i)
    }

    sels
  }
}

/**
 * Connect the first k of n valid input interfaces to k output interfaces.
 */
class Compactor[T <: chisel3.Data](n: Int, k: Int, gen: T) extends Module
{
  require(n >= k)

  val io = IO(new Bundle {
    val in  = Vec(n, Flipped(DecoupledIO(gen)))
    val out = Vec(k,         DecoupledIO(gen))
  })

  if (n == k) {
    io.out <> io.in
  } else {
    val counts = io.in.map(_.valid).scanLeft(1.U(k.W)) ((c,e) => Mux(e, (c<<1)(k-1,0), c))
    val sels = Transpose(VecInit(counts map (c => VecInit(c.asBools)))) map (col =>
                 (col zip io.in.map(_.valid)) map {case (c,v) => c && v})
    val in_readys = counts map (row => (row.asBools zip io.out.map(_.ready)) map {case (c,r) => c && r} reduce (_||_))
    val out_valids = sels map (col => col.reduce(_||_))
    val out_data = sels map (s => Mux1H(s, io.in.map(_.bits)))

    in_readys zip io.in foreach {case (r,i) => i.ready := r}
    out_valids zip out_data zip io.out foreach {case ((v,d),o) => o.valid := v; o.bits := d}
  }
}

/**
 * Create a queue that can be killed with a branch kill signal.
 * Assumption: enq.valid only high if not killed by branch (so don't check IsKilled on io.enq).
 */
class BranchKillableQueue[T <: boom.v3.common.HasBoomUOP](gen: T, entries: Int, flush_fn: boom.v3.common.MicroOp => Bool = u => true.B, flow: Boolean = true)
  (implicit p: org.chipsalliance.cde.config.Parameters)
  extends boom.v3.common.BoomModule()(p)
  with boom.v3.common.HasBoomCoreParameters
{
  val io = IO(new Bundle {
    val enq     = Flipped(Decoupled(gen))
    val deq     = Decoupled(gen)

    val brupdate  = Input(new BrUpdateInfo())
    val flush   = Input(Bool())

    val empty   = Output(Bool())
    val count   = Output(UInt(log2Ceil(entries).W))
  })

  val ram     = Mem(entries, gen)
  val valids  = RegInit(VecInit(Seq.fill(entries) {false.B}))
  val uops    = Reg(Vec(entries, new MicroOp))

  val enq_ptr = Counter(entries)
  val deq_ptr = Counter(entries)
  val maybe_full = RegInit(false.B)

  val ptr_match = enq_ptr.value === deq_ptr.value
  io.empty := ptr_match && !maybe_full
  val full = ptr_match && maybe_full
  val do_enq = WireInit(io.enq.fire)
  val do_deq = WireInit((io.deq.ready || !valids(deq_ptr.value)) && !io.empty)

  for (i <- 0 until entries) {
    val mask = uops(i).br_mask
    val uop  = uops(i)
    valids(i)  := valids(i) && !IsKilledByBranch(io.brupdate, mask) && !(io.flush && flush_fn(uop))
    when (valids(i)) {
      uops(i).br_mask := GetNewBrMask(io.brupdate, mask)
    }
  }

  when (do_enq) {
    ram(enq_ptr.value)          := io.enq.bits
    valids(enq_ptr.value)       := true.B //!IsKilledByBranch(io.brupdate, io.enq.bits.uop)
    uops(enq_ptr.value)         := io.enq.bits.uop
    uops(enq_ptr.value).br_mask := GetNewBrMask(io.brupdate, io.enq.bits.uop)
    enq_ptr.inc()
  }

  when (do_deq) {
    valids(deq_ptr.value) := false.B
    deq_ptr.inc()
  }

  when (do_enq =/= do_deq) {
    maybe_full := do_enq
  }

  io.enq.ready := !full

  val out = Wire(gen)
  out             := ram(deq_ptr.value)
  out.uop         := uops(deq_ptr.value)
  io.deq.valid            := !io.empty && valids(deq_ptr.value) && !IsKilledByBranch(io.brupdate, out.uop) && !(io.flush && flush_fn(out.uop))
  io.deq.bits             := out
  io.deq.bits.uop.br_mask := GetNewBrMask(io.brupdate, out.uop)

  // For flow queue behavior.
  if (flow) {
    when (io.empty) {
      io.deq.valid := io.enq.valid //&& !IsKilledByBranch(io.brupdate, io.enq.bits.uop)
      io.deq.bits := io.enq.bits
      io.deq.bits.uop.br_mask := GetNewBrMask(io.brupdate, io.enq.bits.uop)

      do_deq := false.B
      when (io.deq.ready) { do_enq := false.B }
    }
  }
  private val ptr_diff = enq_ptr.value - deq_ptr.value
  if (isPow2(entries)) {
    io.count := Cat(maybe_full && ptr_match, ptr_diff)
  }
  else {
    io.count := Mux(ptr_match,
                    Mux(maybe_full,
                        entries.asUInt, 0.U),
                    Mux(deq_ptr.value > enq_ptr.value,
                        entries.asUInt + ptr_diff, ptr_diff))
  }
}

// ------------------------------------------
// Printf helper functions
// ------------------------------------------

object BoolToChar
{
  /**
   * Take in a Chisel Bool and convert it into a Str
   * based on the Chars given
   *
   * @param c_bool Chisel Bool
   * @param trueChar Scala Char if bool is true
   * @param falseChar Scala Char if bool is false
   * @return UInt ASCII Char for "trueChar" or "falseChar"
   */
  def apply(c_bool: Bool, trueChar: Char, falseChar: Char = '-'): UInt = {
    Mux(c_bool, Str(trueChar), Str(falseChar))
  }
}

object CfiTypeToChars
{
  /**
   * Get a Vec of Strs that can be used for printing
   *
   * @param cfi_type specific cfi type
   * @return Vec of Strs (must be indexed to get specific char)
   */
  def apply(cfi_type: UInt) = {
    val strings = Seq("----", "BR  ", "JAL ", "JALR")
    val multiVec = VecInit(for(string <- strings) yield { VecInit(for (c <- string) yield { Str(c) }) })
    multiVec(cfi_type)
  }
}

object BpdTypeToChars
{
  /**
   * Get a Vec of Strs that can be used for printing
   *
   * @param bpd_type specific bpd type
   * @return Vec of Strs (must be indexed to get specific char)
   */
  def apply(bpd_type: UInt) = {
    val strings = Seq("BR  ", "JUMP", "----", "RET ", "----", "CALL", "----", "----")
    val multiVec = VecInit(for(string <- strings) yield { VecInit(for (c <- string) yield { Str(c) }) })
    multiVec(bpd_type)
  }
}

object RobTypeToChars
{
  /**
   * Get a Vec of Strs that can be used for printing
   *
   * @param rob_type specific rob type
   * @return Vec of Strs (must be indexed to get specific char)
   */
  def apply(rob_type: UInt) = {
    val strings = Seq("RST", "NML", "RBK", " WT")
    val multiVec = VecInit(for(string <- strings) yield { VecInit(for (c <- string) yield { Str(c) }) })
    multiVec(rob_type)
  }
}

object XRegToChars
{
  /**
   * Get a Vec of Strs that can be used for printing
   *
   * @param xreg specific register number
   * @return Vec of Strs (must be indexed to get specific char)
   */
  def apply(xreg: UInt) = {
    val strings = Seq(" x0", " ra", " sp", " gp",
                      " tp", " t0", " t1", " t2",
                      " s0", " s1", " a0", " a1",
                      " a2", " a3", " a4", " a5",
                      " a6", " a7", " s2", " s3",
                      " s4", " s5", " s6", " s7",
                      " s8", " s9", "s10", "s11",
                      " t3", " t4", " t5", " t6")
    val multiVec = VecInit(for(string <- strings) yield { VecInit(for (c <- string) yield { Str(c) }) })
    multiVec(xreg)
  }
}

object FPRegToChars
{
  /**
   * Get a Vec of Strs that can be used for printing
   *
   * @param fpreg specific register number
   * @return Vec of Strs (must be indexed to get specific char)
   */
  def apply(fpreg: UInt) = {
    val strings = Seq(" ft0", " ft1", " ft2", " ft3",
                      " ft4", " ft5", " ft6", " ft7",
                      " fs0", " fs1", " fa0", " fa1",
                      " fa2", " fa3", " fa4", " fa5",
                      " fa6", " fa7", " fs2", " fs3",
                      " fs4", " fs5", " fs6", " fs7",
                      " fs8", " fs9", "fs10", "fs11",
                      " ft8", " ft9", "ft10", "ft11")
    val multiVec = VecInit(for(string <- strings) yield { VecInit(for (c <- string) yield { Str(c) }) })
    multiVec(fpreg)
  }
}

object BoomCoreStringPrefix
{
  /**
  * Add prefix to BOOM strings (currently only adds the hartId)
  *
  * @param strs list of strings
  * @return String combining the list with the prefix per line
  */
  def apply(strs: String*)(implicit p: Parameters) = {
    val prefix = "[C" + s"${p(TileKey).tileId}" + "] "
    strs.map(str => prefix + str + "\n").mkString("")
  }
}
