//******************************************************************************
// Copyright (c) 2018 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package boom.v3.util

import chisel3._
import chisel3.util._

/**
 * Bundle serving as the write port to the ESram.
 *
 * @param idxSz the size of the idx to index into the memory
 * @param gen the type of Data object to store in the memory
 */
class ESramWritePort[T <: Data](val idxSz: Int, private val gen: T) extends Bundle
{
  val idx = UInt(idxSz.W)
  val data = gen
}

/**
 * Implements a decoupled SRAM which can be back-pressured on the read response side,
 * and thus will in turn back-pressure the read request side.
 *
 * Assumptions:
 *   - If banked for 1rw, allowed to drop write updates
 *
 * @param numEntries the number of logical entries in the memory
 * @param gen the type of Data to store in the memory
 * @param dualported use 1r1w SeqMem or use banking with two 1rw SeqMems
 */
class ElasticSeqMem[T <: Data](
  numEntries: Int,
  gen: T,
  dualported: Boolean = true) extends Module
{
  private val idxSz = log2Ceil(numEntries)

  val io = IO(new Bundle {
    // read request on cycle S0 (pass in read address)
    val rreq = Flipped(Decoupled(UInt(idxSz.W)))
    // read response on cycle S1 (receive read output)
    val rresp = Decoupled(gen)

    val write = Flipped(Valid(new ESramWritePort(idxSz, gen)))

    // clear out queued inflight requests, but allow current response to be valid.
    val flush = Input(Bool())
  })

  private val ram = SyncReadMem(numEntries, gen)

  when (io.write.valid) {
    ram.write(io.write.bits.idx, io.write.bits.data)
  }

  // Shadow flop.
  //   we provide a shadow flop to decouple the not-ready read response from the
  //   read request.

  // Replay s0 onto s1 if s1_resp is not ready,
  //   as we need to maintain the same read index as the last cycle.
  val s1_replay = io.rresp.ready

  val s0_valid = Wire(Bool())
  val s0_ridx = Wire(UInt(idxSz.W))
  val last_val = RegNext(s0_valid)
  val last_idx = RegNext(s0_ridx)
  s0_valid := Mux(s1_replay, last_val, io.rreq.valid)
  s0_ridx := Mux(s1_replay, last_idx, io.rreq.bits)

  val s1_valid = RegNext(s0_valid)
  io.rresp.valid := s1_valid
  io.rresp.bits := ram.read(s0_ridx, s0_valid)

  when (io.flush) {
    // TODO XXX
    last_val := false.B
  }
}
