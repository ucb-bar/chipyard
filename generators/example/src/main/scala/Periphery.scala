package example

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import testchipip._

import hbwif._
import hbwif.tilelink._

case object TLSwitcherIdBits extends Field[Int]
case object HbwifMemBufferStages extends Field[Int]
case object HbwifConfigBufferStages extends Field[Int]
case object LbwifSinkIds extends Field[Int]

trait HasHbwif {

  // Build the HBWIF module
  val hbwif = LazyModule(p(BuildHbwif)(p))

  // Create the base external memory region that we'll segment
  val base = AddressSet.misaligned(extParams.base, extParams.size)
  // Create address filters to divide the memory space into a region per HBWIF lane
  val filters = (0 until p(HbwifNumLanes)).map { case id =>
    AddressSet(id * p(CacheBlockBytes) * p(CacheBlockStriping), ~((p(HbwifNumLanes)-1) * p(CacheBlockBytes) * p(CacheBlockStriping)))
  }
  // Create the address sets for the filters
  val addresses = filters.map { case filt =>
    base.flatMap(_.intersect(filt))
  }
  // Connect the configuration port
  for (i <- 0 until p(HbwifNumLanes)) {
    // Connect the config port to the pbus
    pbus.toVariableWidthSlave(Some(s"hbwif_config$i")) { hbwif.configNodes(i) := TLBuffer.chainNode(p(HbwifConfigBufferStages)) := TLWidthWidget(pbus.beatBytes) }
  }
  // Connect the client port if it exists
  // TODO

  // Note: At this point the memory port(s) is/are still disconnected
}

trait HasLbwif {

  // Set up some parameters to pass to LBWIF
  val memParams = TLManagerParameters(
    address = AddressSet.misaligned(extParams.base, extParams.size),
      resources = (new MemoryDevice).reg,
      regionType = RegionType.UNCACHED, // cacheable
      executable = true,
      fifoId = Some(0),
      supportsGet = TransferSizes(1, p(CacheBlockBytes)),
      supportsPutFull = TransferSizes(1, p(CacheBlockBytes)),
      supportsAcquireT   = TransferSizes(1, p(CacheBlockBytes)),
      supportsAcquireB   = TransferSizes(1, p(CacheBlockBytes)),
      supportsArithmetic = TransferSizes(1, p(CacheBlockBytes)),
      supportsLogical    = TransferSizes(1, p(CacheBlockBytes)),
      supportsPutPartial = TransferSizes(1, p(CacheBlockBytes)),
      supportsHint       = TransferSizes(1, p(CacheBlockBytes)))
  val ctrlParams = TLClientParameters(
      name = "tl_serdes_control",
      sourceId = IdRange(0,128),
      requestFifo = true) //TODO: how many outstanding xacts
  // Build the LBWIF module
  val lbwif = LazyModule(new TLSerdesser(4, ctrlParams, memParams, extParams.beatBytes, p(LbwifSinkIds)))

  val lbwifClientXingSource = LazyModule(new TLAsyncCrossingSource)
  val lbwifManagerXingSink = LazyModule(new TLAsyncCrossingSink)

  // The client port connects to the fbus
  // TODO We may actually want a multi-stage buffer here.
  (fbus.fromMaster()()
    := TLBuffer()
    := TLWidthWidget(extParams.beatBytes)
    := TLAsyncCrossingSink()
    := lbwifClientXingSource.node
    := lbwif.clientNode)

  // Note: At this point the memory port(s) is/are still disconnected
}

trait HasPeripheryOnlyHbwif extends HasHbwif {
  // Connect the manager node to the mbus
  for (i <- 0 until p(HbwifNumLanes)) {
    // TODO Does this really need to be called N times?
    (hbwif.managerNode
      := TLBuffer.chainNode(p(HbwifMemBufferStages))
      := mbus.coupleTo(s"hbwifPort_$i") { TLBuffer() := _ })
  }
}

trait HasPeripheryOnlyLbwif extends HasLbwif {
  // Connect the manager node to the mbus
  (lbwif.managerNode
    := lbwifManagerXingSink.node
    := TLAsyncCrossingSource()
    := mbus.coupleTo("lbwifPort") { TLBuffer() := _})
}

trait HasPeripheryHbwifAndLbwif extends HasHbwif with HasLbwif {
  require(p(TLSwitcherIdBits) > log2Ceil(p(HbwifNumLanes)), "There must be, at a minimum, more switcher ID bits than log2(hbwif lanes), but you likely need even more than that. Check your diplomacy graph.")
  // This module intentionally breaks the diplomacy graph to allow two paths to share a memory region.
  // This is not the ideal way to solve this problem, so at some point we'll figure out a better way.
  // This switcher configuration creates one input node with N ports and two ouput nodes: one with N ports and one with 1 port.
  // The N-port output node is for the HBWIF connection, and the 1-port output node is for the LBWIF connection.
  val switcher = LazyModule(new TLSwitcher(
    inPortN = p(HbwifNumLanes),
    outPortN = Seq(p(HbwifNumLanes), 1),
    address = addresses,
    beatBytes = extParams.beatBytes,
    lineBytes = p(CacheBlockBytes),
    // Ideally this shouldn't need to be set, but because we break the diplomacy graph, it is needed.
    idBits = p(TLSwitcherIdBits)
  ))

  // Connect the N-port switcher nodes (in: mbus, out: HBWIF)
  for (i <- 0 until p(HbwifNumLanes)) {
    // TODO Does this really need to be called N times?
    (hbwif.managerNode
      := TLBuffer.chainNode(p(HbwifMemBufferStages))
      := switcher.outnodes(0))
    switcher.innode := mbus.coupleTo(s"switcherPort_$i") { TLBuffer() := _}
  }

  // Connect the narrow switcher node (out: LBWIF)
  (lbwif.managerNode
    := lbwifManagerXingSink.node
    := TLAsyncCrossingSource()
    := switcher.outnodes(1))

  // Note: switcher.module.io.switcherSel is still disconnected. It is recommended to connect this to a memory-mapped register that resets to 1 (LBWIF).
}

trait HasPeripheryHbwifModuleImp {
  val outer: HasLbwif
  // Create the top-level RX/TX IO signals and the reference clocks
  val hbwifRefClocks = IO(Input(Vec(p(HbwifNumBanks), Clock())))
  val hbwifTx = IO(chiselTypeOf(outer.hbwif.module.tx))
  val hbwifRx = IO(chiselTypeOf(outer.hbwif.module.rx))

  outer.hbwif.module.hbwifRefClocks := hbwifRefClocks
  hbwifTx <> outer.hbwif.module.tx
  hbwifRx <> outer.hbwif.module.rx

  // Note: outer.hbwif.module.hbwifResets is still disconnected. It is recommended to connect these to a memory-mapped register with a reset value of all 1s.
  // Note: outer.hbwif.module.resetAsync is still disconnected. It is recommended to connect this to the top-level asynchronous reset if active-high, or ~reset_n if active-low.
}

trait HasPeripheryLbwifModuleImp {
  val outer: HasHbwif
  // Create the top-level tlSerial IO and connect it
  val tlSerial = IO(chiselTypeOf(outer.lbwif.module.io.ser))
  tlSerial <> outer.lbwif.module.io.ser
  // Create the clock divider and hook everything up (except the divisor)
  val lbwifClockDiv = Module(new testchipip.ClockDivider(outer.scrParams.lbwifDividerBits))
  val lbwifClock = lbwifClockDiv.io.clockOut
  val lbwifReset = ResetCatchAndSync(lbwifClock, reset.toBool)
  Seq(outer.lbwif, outer.lbwifClientXingSource, outer.lbwifManagerXingSink).foreach { m =>
    m.module.clock := lbwifClock
    m.module.reset := lbwifReset
  }

  // Note: lbwifClockDiv.io.divisor is still disconnected. It is recommended to connect this to a memory-mapped register with a large reset value.
}

