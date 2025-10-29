package testchipip.soc

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink.{TLBusWrapperTopology}
import freechips.rocketchip.diplomacy.{BufferParams, AddressSet}

//-------------------------
// Scratchpad Configs
//-------------------------

// Attach a non-cacheable read/write general-purpose SRAM-backed bankable memory to sum bus in the system
class WithScratchpad(
  base: BigInt = 0x80000000L,
  size: BigInt = (4 << 20),
  banks: Int = 1,
  partitions: Int = 1,
  busWhere: TLBusWrapperLocation = SBUS,
  subBanks: Int = 1,
  buffer: BufferParams = BufferParams.default,
  outerBuffer: BufferParams = BufferParams.default
) extends Config((site, here, up) => {
  case BankedScratchpadKey => up(BankedScratchpadKey) ++ (0 until partitions).map { pa => BankedScratchpadParams(
    base + pa * (size / partitions),
    size / partitions,
    busWhere = busWhere,
    name = s"${busWhere.name}-scratchpad",
    banks = banks,
    buffer = buffer,
    outerBuffer = outerBuffer,
    subBanks = subBanks
  )}
  case SubsystemInjectorKey => up(SubsystemInjectorKey) + BankedScratchpadInjector
})

class WithMbusScratchpad(base: BigInt = 0x80000000L, size: BigInt = (4 << 20), banks: Int = 1, partitions: Int = 1, subBanks: Int = 1) extends
    WithScratchpad(base, size, banks, partitions, MBUS, subBanks)

class WithSbusScratchpad(base: BigInt = 0x80000000L, size: BigInt = (4 << 20), banks: Int = 1, partitions: Int = 1, subBanks: Int = 1) extends
    WithScratchpad(base, size, banks, partitions, SBUS, subBanks)

// Remove TL monitors from the Scratchpads. This is used to enable deduplication for VLSI flows
class WithNoScratchpadMonitors extends Config((site, here, up) => {
  case BankedScratchpadKey => up(BankedScratchpadKey).map(_.copy(disableMonitors=true))
})

// Remove all TL scratchpads from the system
class WithNoScratchpads extends Config((site, here, up) => {
  case BankedScratchpadKey => Nil
})

//-------------------------
// OffchipBus Configs
//-------------------------

class WithOffchipBus extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) => up(TLNetworkTopologyLocated(InSubsystem)) :+
    OffchipBusTopologyParams(site(OffchipBusKey))
  case OffchipBusKey => up(OffchipBusKey).copy(beatBytes = 8, blockBytes = site(CacheBlockBytes))
})

class WithOffchipBusClient(
  location: TLBusWrapperLocation,
  blockRange: Seq[AddressSet] = Nil,
  replicationBase: Option[BigInt] = None) extends Config((site, here, up) => {
    case TLNetworkTopologyLocated(InSubsystem) => up(TLNetworkTopologyLocated(InSubsystem)) :+
      OffchipBusTopologyConnectionParams(location, blockRange, replicationBase)
})

//-------------------------
// ChipIdPin Configs
//-------------------------

class WithChipIdPin(params: ChipIdPinParams = ChipIdPinParams()) extends Config((site, here, up) => {
  case ChipIdPinKey => Some(params)
})

// Used for setting pin width
class WithChipIdPinWidth(width: Int) extends Config((site, here, up) => {
  case ChipIdPinKey => up(ChipIdPinKey, site).map(p => p.copy(width = width))
})

// Deprecated: use Constellation's network-on-chip generators instead of this
class WithRingSystemBus(
    buffer: TLNetworkBufferParams = TLNetworkBufferParams.default)
    extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) =>
    up(TLNetworkTopologyLocated(InSubsystem), site).map(topo =>
      topo match {
        case j: JustOneBusTopologyParams =>
          new TLBusWrapperTopology(j.instantiations.map(inst => inst match {
            case (SBUS, sbus_params: SystemBusParams) => (SBUS, RingSystemBusParams(sbus_params, buffer))
            case a => a
          }
        ), j.connections)
        case x => x
      }
    )
})
