package shuttle.util

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util.{Str}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tile.{TileKey}

// Set all bits at or below the highest order '1'.
object MaskLower
{
  def apply(in: UInt) = {
    val n = in.getWidth
    (0 until n).map(i => in >> i.U).reduce(_|_)
  }
}

// Set all bits at or above the lowest order '1'.
object MaskUpper
{
  def apply(in: UInt) = {
    val n = in.getWidth
    (0 until n).map(i => (in << i.U)(n-1,0)).reduce(_|_)
  }
}


object Sext
{
  def apply(x: UInt, length: Int): UInt = {
    if (x.getWidth == length) return x
    else return Cat(Fill(length-x.getWidth, x(x.getWidth-1)), x)
  }
}
