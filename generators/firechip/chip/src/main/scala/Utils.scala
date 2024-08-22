// See LICENSE for license details.

package firechip.chip

import chisel3._

import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4EdgeParameters}

import firesim.lib.bridges.{AXI4EdgeSummary, AddressSet}
import firesim.lib.nasti.{NastiIO, NastiParameters}

// Chipyard's RC version of AXI4EdgeParameters to AXI4EdgeSummary
object CreateAXI4EdgeSummary {
  // Returns max ID reuse; None -> unbounded
  private def getIDReuseFromEdge(e: AXI4EdgeParameters): Option[Int] = {
    val maxFlightPerMaster = e.master.masters.map(_.maxFlight)
    maxFlightPerMaster.reduce( (_,_) match {
      case (Some(prev), Some(cur)) => Some(scala.math.max(prev, cur))
      case _ => None
    })
  }
  // Returns (maxReadLength, maxWriteLength)
  private def getMaxTransferFromEdge(e: AXI4EdgeParameters): (Int, Int) = {
    val beatBytes = e.slave.beatBytes
    val readXferSize  = e.slave.slaves.head.supportsRead.max
    val writeXferSize = e.slave.slaves.head.supportsWrite.max
    ((readXferSize + beatBytes - 1) / beatBytes, (writeXferSize + beatBytes - 1) / beatBytes)
  }

  // Sums up the maximum number of requests that can be inflight across all masters
  // None -> unbounded
  private def getMaxTotalFlightFromEdge(e: AXI4EdgeParameters): Option[Int] = {
    val maxFlightPerMaster = e.master.masters.map(_.maxFlight)
    maxFlightPerMaster.reduce( (_,_) match {
      case (Some(prev), Some(cur)) => Some(prev + cur)
      case _ => None
    })
  }

  def apply(e: AXI4EdgeParameters, idx: Int = 0): AXI4EdgeSummary = {
    val slave = e.slave.slaves(idx)
    AXI4EdgeSummary(
      getMaxTransferFromEdge(e)._1,
      getMaxTransferFromEdge(e)._2,
      getIDReuseFromEdge(e),
      getMaxTotalFlightFromEdge(e),
      slave.address.map(as => AddressSet(as.base, as.mask)))
  }
}

// Chipyard's RC version of AXI4Bundle to/from NastiIO
object AXI4NastiAssigner {
  def toNasti(nasti: NastiIO, axi4: AXI4Bundle): Unit = {
    // TODO: The following should ensure sizes are equivalent.
    // HACK: Nasti and Diplomatic have diverged to the point where it's no
    // longer safe to emit a partial connect leaf fields. Onus is on the
    // invoker to check widths.
    nasti.aw.valid       := axi4.aw.valid
    nasti.aw.bits.id     := axi4.aw.bits.id
    nasti.aw.bits.addr   := axi4.aw.bits.addr
    nasti.aw.bits.len    := axi4.aw.bits.len
    nasti.aw.bits.size   := axi4.aw.bits.size
    nasti.aw.bits.burst  := axi4.aw.bits.burst
    nasti.aw.bits.lock   := axi4.aw.bits.lock
    nasti.aw.bits.cache  := axi4.aw.bits.cache
    nasti.aw.bits.prot   := axi4.aw.bits.prot
    nasti.aw.bits.qos    := axi4.aw.bits.qos
    nasti.aw.bits.user   := DontCare
    nasti.aw.bits.region := 0.U
    axi4.aw.ready := nasti.aw.ready

    nasti.ar.valid  := axi4.ar.valid
    nasti.ar.bits.id     := axi4.ar.bits.id
    nasti.ar.bits.addr   := axi4.ar.bits.addr
    nasti.ar.bits.len    := axi4.ar.bits.len
    nasti.ar.bits.size   := axi4.ar.bits.size
    nasti.ar.bits.burst  := axi4.ar.bits.burst
    nasti.ar.bits.lock   := axi4.ar.bits.lock
    nasti.ar.bits.cache  := axi4.ar.bits.cache
    nasti.ar.bits.prot   := axi4.ar.bits.prot
    nasti.ar.bits.qos    := axi4.ar.bits.qos
    nasti.ar.bits.user   := DontCare
    nasti.ar.bits.region := 0.U
    axi4.ar.ready := nasti.ar.ready

    nasti.w.valid  := axi4.w.valid
    nasti.w.bits.data  := axi4.w.bits.data
    nasti.w.bits.strb  := axi4.w.bits.strb
    nasti.w.bits.last  := axi4.w.bits.last
    nasti.w.bits.user  := DontCare
    nasti.w.bits.id    := DontCare // We only use AXI4, not AXI3
    axi4.w.ready := nasti.w.ready

    axi4.r.valid     := nasti.r.valid
    axi4.r.bits.id   := nasti.r.bits.id
    axi4.r.bits.data := nasti.r.bits.data
    axi4.r.bits.resp := nasti.r.bits.resp
    axi4.r.bits.last := nasti.r.bits.last
    axi4.r.bits.user := DontCare
    // Echo is not a AXI4 standard signal.
    axi4.r.bits.echo := DontCare
    nasti.r.ready := axi4.r.ready

    axi4.b.valid     := nasti.b.valid
    axi4.b.bits.id   := nasti.b.bits.id
    axi4.b.bits.resp := nasti.b.bits.resp
    axi4.b.bits.user := DontCare
    // Echo is not a AXI4 standard signal.
    axi4.b.bits.echo := DontCare
    nasti.b.ready := axi4.b.ready
  }

  def toAXI4Slave(axi4: AXI4Bundle, nasti: NastiIO): Unit = {
    // HACK: Nasti and Diplomatic have diverged to the point where it's no
    // longer safe to emit a partial connect leaf fields. Onus is on the
    // invoker to check widths.
    axi4.aw.valid       := nasti.aw.valid
    axi4.aw.bits.id     := nasti.aw.bits.id
    axi4.aw.bits.addr   := nasti.aw.bits.addr
    axi4.aw.bits.len    := nasti.aw.bits.len
    axi4.aw.bits.size   := nasti.aw.bits.size
    axi4.aw.bits.burst  := nasti.aw.bits.burst
    axi4.aw.bits.lock   := nasti.aw.bits.lock
    axi4.aw.bits.cache  := nasti.aw.bits.cache
    axi4.aw.bits.prot   := nasti.aw.bits.prot
    axi4.aw.bits.qos    := nasti.aw.bits.qos
    axi4.aw.bits.user   := DontCare
    axi4.aw.bits.echo   := DontCare
    nasti.aw.ready := axi4.aw.ready

    axi4.ar.valid       := nasti.ar.valid
    axi4.ar.bits.id     := nasti.ar.bits.id
    axi4.ar.bits.addr   := nasti.ar.bits.addr
    axi4.ar.bits.len    := nasti.ar.bits.len
    axi4.ar.bits.size   := nasti.ar.bits.size
    axi4.ar.bits.burst  := nasti.ar.bits.burst
    axi4.ar.bits.lock   := nasti.ar.bits.lock
    axi4.ar.bits.cache  := nasti.ar.bits.cache
    axi4.ar.bits.prot   := nasti.ar.bits.prot
    axi4.ar.bits.qos    := nasti.ar.bits.qos
    axi4.ar.bits.user   := DontCare
    axi4.ar.bits.echo   := DontCare
    nasti.ar.ready := axi4.ar.ready

    axi4.w.valid      := nasti.w.valid
    axi4.w.bits.data  := nasti.w.bits.data
    axi4.w.bits.strb  := nasti.w.bits.strb
    axi4.w.bits.last  := nasti.w.bits.last
    axi4.w.bits.user  := DontCare
    nasti.w.ready := axi4.w.ready

    nasti.r.valid     := axi4.r.valid
    nasti.r.bits.id   := axi4.r.bits.id
    nasti.r.bits.data := axi4.r.bits.data
    nasti.r.bits.resp := axi4.r.bits.resp
    nasti.r.bits.last := axi4.r.bits.last
    nasti.r.bits.user := DontCare
    axi4.r.ready := nasti.r.ready

    nasti.b.valid     := axi4.b.valid
    nasti.b.bits.id   := axi4.b.bits.id
    nasti.b.bits.resp := axi4.b.bits.resp
    nasti.b.bits.user := DontCare
    // Echo is not a AXI4 standard signal.
    axi4.b.ready := nasti.b.ready
  }

  def toAXI4Master(axi4: AXI4Bundle, nasti: NastiIO): Unit = {
    // HACK: Nasti and Diplomatic have diverged to the point where it's no
    // longer safe to emit a partial connect leaf fields. Onus is on the
    // invoker to check widths.
    nasti.aw.valid      := axi4.aw.valid
    nasti.aw.bits.id    := axi4.aw.bits.id
    nasti.aw.bits.addr  := axi4.aw.bits.addr
    nasti.aw.bits.len   := axi4.aw.bits.len
    nasti.aw.bits.size  := axi4.aw.bits.size
    nasti.aw.bits.burst := axi4.aw.bits.burst
    nasti.aw.bits.lock  := axi4.aw.bits.lock
    nasti.aw.bits.cache := axi4.aw.bits.cache
    nasti.aw.bits.prot  := axi4.aw.bits.prot
    nasti.aw.bits.qos   := axi4.aw.bits.qos
    nasti.aw.bits.user  := DontCare
    nasti.aw.bits.region := DontCare
    //nasti.aw.bits.echo  := DontCare
    axi4.aw.ready       := nasti.aw.ready

    nasti.ar.valid      := axi4.ar.valid
    nasti.ar.bits.id    := axi4.ar.bits.id
    nasti.ar.bits.addr  := axi4.ar.bits.addr
    nasti.ar.bits.len   := axi4.ar.bits.len
    nasti.ar.bits.size  := axi4.ar.bits.size
    nasti.ar.bits.burst := axi4.ar.bits.burst
    nasti.ar.bits.lock  := axi4.ar.bits.lock
    nasti.ar.bits.cache := axi4.ar.bits.cache
    nasti.ar.bits.prot  := axi4.ar.bits.prot
    nasti.ar.bits.qos   := axi4.ar.bits.qos
    nasti.ar.bits.user  := DontCare
    nasti.ar.bits.region := DontCare
    //nasti.ar.bits.echo  := DontCare
    axi4.ar.ready       := nasti.ar.ready

    nasti.w.valid     := axi4.w.valid
    nasti.w.bits.data := axi4.w.bits.data
    nasti.w.bits.strb := axi4.w.bits.strb
    nasti.w.bits.last := axi4.w.bits.last
    nasti.w.bits.user := DontCare
    nasti.w.bits.id   := DontCare
    axi4.w.ready      := nasti.w.ready

    axi4.r.valid      := nasti.r.valid
    axi4.r.bits.id    := nasti.r.bits.id
    axi4.r.bits.data  := nasti.r.bits.data
    axi4.r.bits.resp  := nasti.r.bits.resp
    axi4.r.bits.last  := nasti.r.bits.last
    nasti.r.bits.user := DontCare
    nasti.r.ready     := axi4.r.ready

    axi4.b.valid     := nasti.b.valid
    axi4.b.bits.id   := nasti.b.bits.id
    axi4.b.bits.resp := nasti.b.bits.resp
    nasti.b.bits.user := DontCare
    // Echo is not a AXI4 standard signal.
    nasti.b.ready := axi4.b.ready
  }
}
