package tracegen

import chisel3._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.coreplex._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.groundtest.{GroundTestTilesKey, DebugCombiner}
import freechips.rocketchip.tile.{TileKey, SharedMemoryTLEdge}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{DontTouch, HeterogeneousBag}
import testchipip._

class TracegenTop(implicit p: Parameters) extends BareCoreplex {

  private val sbusParams = p(SystemBusKey)
  val sbus = LazyModule(new SystemBus(sbusParams))

  val tiles = p(GroundTestTilesKey).zipWithIndex.map { case (c, i) =>
    LazyModule(c.build(i, p.alterPartial {
      case TileKey => c
      case SharedMemoryTLEdge => sbus.busView
    }))
  }

  tiles.flatMap(_.dcacheOpt).foreach { dc =>
    sbus.fromTile(None) { implicit p => TileMasterPortParams(addBuffers = 1).adapt(null)(dc.node) }
  }

  private val mbusParams = p(MemoryBusKey)
  private val l2Params = p(BankedL2Key)

  val MemoryBusParams(memBusBeatBytes, memBusBlockBytes, _, _) = mbusParams
  val BankedL2Params(nMemoryChannels, nBanksPerChannel, _) = l2Params
  val BroadcastParams(nTrackers, bufferless) = p(BroadcastKey)

  val nBanks = l2Params.nBanks
  val cacheBlockBytes = memBusBlockBytes

  private val mask = ~BigInt((nBanks-1) * memBusBlockBytes)
  val memBuses = Seq.tabulate(nMemoryChannels) { channel =>
    val mbus = LazyModule(new MemoryBus(mbusParams))
    for (bank <- 0 until nBanksPerChannel) {
      val offset = (bank * nMemoryChannels) + channel
      val bhub = LazyModule(new TLBroadcast(memBusBlockBytes, nTrackers, bufferless))

      ForceFanout(a = true) { implicit p => bhub.node := sbus.toMemoryBus }

      mbus.fromCoherenceManager :=
        TLFilter(TLFilter.Mmask(AddressSet(offset * memBusBlockBytes, mask))) :=
        TLWidthWidget(sbusParams.beatBytes) := bhub.node
    }
    mbus
  }

  private val memParams = p(ExtMem)
  val mem_axi4 = AXI4SlaveNode(Seq.tabulate(nMemoryChannels) { channel =>
    val base = AddressSet(memParams.base, memParams.size-1)
    val filter = AddressSet(channel * cacheBlockBytes, ~((nMemoryChannels-1) * cacheBlockBytes))

    AXI4SlavePortParameters(
      slaves = Seq(AXI4SlaveParameters(
        address       = base.intersect(filter).toList,
        regionType    = RegionType.UNCACHED,   // cacheable
        executable    = true,
        supportsWrite = TransferSizes(1, cacheBlockBytes),
        supportsRead  = TransferSizes(1, cacheBlockBytes),
        interleavedId = Some(0))),             // slave does not interleave read responses
      beatBytes = memParams.beatBytes)
  })

  val converter = LazyModule(new TLToAXI4())
  val trim = LazyModule(new AXI4IdIndexer(memParams.idBits))
  val yank = LazyModule(new AXI4UserYanker)
  val buffer = LazyModule(new AXI4Buffer)

  memBuses.map(_.toDRAMController).foreach { case node =>
    mem_axi4 := buffer.node := yank.node := trim.node := converter.node := node
  }

  override lazy val module = new TracegenTopModule(this)
}

class TracegenTopModule[+L <: TracegenTop](l: L) extends BareCoreplexModule(l) {
  val io = IO(new Bundle {
    val mem_axi4 = HeterogeneousBag.fromNode(outer.mem_axi4.in)
    val success = Output(Bool())
  })

  (io.mem_axi4 zip outer.mem_axi4.in) foreach { case (i, (o, _)) => i <> o }
  val nMemoryChannels = outer.nMemoryChannels

  val status = DebugCombiner(outer.tiles.map(_.module.status))
  io.success := status.finished

  outer.tiles.zipWithIndex.map { case (tile, i) =>
    tile.module.constants.hartid := i.U
  }
}
