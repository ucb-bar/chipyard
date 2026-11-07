//******************************************************************************
// Copyright (c) 2017 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Transformable SeqReadMem
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v3.util

import chisel3._
import chisel3.util._

/**
 * Implements a realizable SeqMem that is in a square aspect ratio (rounded
 * to a pow2 for the depth). Used for transforming a tall, skinny aspect
 * ratio memory into a more rectangular shape. Currently, only supports
 * a single R/W port.
 *
 * @param lDepth logical depth of the memory
 * @param lWidth logical width of the memory
 */
class SeqMem1rwTransformable (
  lDepth: Int,
  lWidth: Int) extends Module
{
  val pDepth = 1 << log2Ceil(scala.math.sqrt(lDepth*lWidth).toInt)
  val pWidth = lDepth*lWidth/pDepth

  require (lDepth*lWidth == pDepth*pWidth)
  require (pDepth > 0)
  require (pWidth > 0)
  require (pWidth % lWidth == 0)

  val lIdxSz = log2Ceil(lDepth)
  val pIdxSz = log2Ceil(pDepth)
  val lOffSz = log2Ceil(lWidth)
  val pOffSz = log2Ceil(pWidth/lWidth)
  require (pOffSz > 0)

  println("\tSeqMem transformed from ("+ lDepth +" x "+ lWidth +") to ("+ pDepth +" x "+ pWidth +")")

  val io = IO(new Bundle {
    val wen   = Input(Bool())
    val waddr = Input(UInt(lIdxSz.W))
    val wmask = Input(UInt(lWidth.W))
    val wdata = Input(UInt(lWidth.W))

    val ren   = Input(Bool())           // valid cycle s0
    val raddr = Input(UInt(lIdxSz.W)) // input cycle s0
    val rout  = Output(UInt(lWidth.W)) // returned cycle s1
  })

  val smem = SyncReadMem(pDepth, Vec(pWidth, Bool()))

  private def getIdx(addr:UInt) = addr >> pOffSz

  // must compute offset from address but then factor in the lWidth.
  private def getOffset(addr:UInt) = addr(pOffSz-1,0) << lOffSz

  assert (!(io.wen && io.ren), "[SMUtil] writer and reader fighting over the single port.")
  when (io.wen && !io.ren) {
    val waddr = getIdx(io.waddr)
    val wdata = (io.wdata << getOffset(io.waddr))(pWidth-1, 0)
    val wmask = (io.wmask << getOffset(io.waddr))(pWidth-1, 0)
    smem.write(waddr, VecInit(wdata.asBools), wmask.asBools)
  }

  // read
  val ridx = getIdx(io.raddr)
  val roff = getOffset(io.raddr)
  val r_offset = RegEnable(roff, io.ren)
  // returned cycle s1
  val s1_rrow = smem.read(ridx, io.ren).asUInt
  io.rout := (s1_rrow >> r_offset)(lWidth-1, 0)
}
