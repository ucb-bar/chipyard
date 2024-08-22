// See LICENSE for license details.

package firechip.bridgestubs

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firechip.bridgeinterfaces._

class BlockDevDUT(implicit val p: Parameters) extends Module {
  val params = BlockDeviceConfig(nTrackers=1)

  val rd = Module(new BlockDevBridge(params))
  rd.io.clock := clock
  rd.io.reset := reset
  val rdev = rd.io.bdev

  val wr = Module(new BlockDevBridge(params))
  wr.io.clock := clock
  wr.io.reset := reset
  val wdev = wr.io.bdev

  // Data is copied from the read device to the write device. Ensure it fits.
  assert(wdev.info.nsectors >= rdev.info.nsectors, "write device must be larger than read device");

  // Buffer responses between the read and write ends.
  wdev.data <> Queue(rdev.resp, 5)

  // Maintain a single pair of read-write requests to the same sector.
  val sector = RegInit(0.U(32.W))

  // Fire off a read and wait until its corresponding write is finished.
  val read_pending = RegInit(false.B)
  rdev.req.bits.write  := false.B
  rdev.req.bits.offset := sector
  rdev.req.bits.len    := 1.U
  rdev.req.bits.tag    := 0.U
  rdev.req.valid       := sector < rdev.info.nsectors && !read_pending
  when(rdev.req.fire) {
    read_pending := true.B
  }
  rdev.data.valid      := false.B // reads don't present data
  rdev.data.bits       := DontCare

  // After the read is fired, queue up a write as well.
  val write_pending = RegInit(false.B)
  wdev.req.bits.write  := true.B
  wdev.req.bits.offset := sector
  wdev.req.bits.len    := 1.U
  wdev.req.bits.tag    := 0.U
  wdev.req.valid       := sector < rdev.info.nsectors && read_pending && !write_pending
  when(wdev.req.fire) {
    write_pending := true.B
  }
  wdev.resp.ready      := true.B

  // Count writes and mark the read-write requests as finished once a full sector is transferred.
  val write_offset = RegInit(0.U(6.W))
  when(wdev.data.fire) {
    write_offset := write_offset + 1.U
    when(write_offset.andR) {
      write_pending := false.B
      read_pending  := false.B
      sector        := sector + 1.U
    }
  }
}

class BlockDevModule(implicit p: Parameters) extends firesim.lib.testutils.PeekPokeHarness(() => new BlockDevDUT)
