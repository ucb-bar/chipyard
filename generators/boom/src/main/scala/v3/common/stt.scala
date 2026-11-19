//------------------------------------------------------------------------------
// [boom-ustt] Shared STT helpers: transmitter classification and YRoT safety
// against the visibility point (VP). Contract: project-docs/spec-interface.md.
//------------------------------------------------------------------------------

package boom.v3.common

import chisel3._
import chisel3.util._

import boom.v3.util.IsOlder

/**
 * The STT transmitter set (spec section 3). Classify the decoded instruction,
 * never a rewritten issue opcode, so split stores keep the original flag.
 */
object STTIsTransmitter
{
  def apply(uop: MicroOp): Bool = uop.uses_ldq || (uop.uses_stq && !uop.is_fence) || uop.is_br || uop.is_jalr
}

/**
 * A tag is safe when it is invalid, there is no unresolved branch, or its root
 * is strictly older than the VP. Equality is unsafe.
 */
object STTIsSafe
{
  def apply(yrot: Valid[UInt], vp: Valid[UInt], head: UInt): Bool =
    !yrot.valid || !vp.valid || IsOlder(yrot.bits, vp.bits, head)
}

/** An invalid tag with a zero payload. */
object STTUntainted
{
  def apply(robAddrSz: Int): Valid[UInt] = {
    val t = Wire(Valid(UInt(robAddrSz.W)))
    t.valid := false.B
    t.bits  := 0.U
    t
  }
}

/** Drop a tag that has become safe, so a released transmitter stays released. */
object STTClearSafe
{
  def apply(yrot: Valid[UInt], vp: Valid[UInt], head: UInt): Valid[UInt] = {
    val t = Wire(Valid(UInt(yrot.bits.getWidth.W)))
    t.valid := yrot.valid && !STTIsSafe(yrot, vp, head)
    t.bits  := Mux(t.valid, yrot.bits, 0.U)
    t
  }
}

/** The younger of two tags in circular ROB order; invalid tags contribute nothing. */
object STTYoungest
{
  def apply(a: Valid[UInt], b: Valid[UInt], head: UInt): Valid[UInt] = {
    val take_b = b.valid && (!a.valid || IsOlder(a.bits, b.bits, head))
    Mux(take_b, b, a)
  }
}
