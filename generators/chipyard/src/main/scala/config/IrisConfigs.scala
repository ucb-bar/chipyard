package chipyard

import chisel3._
import chisel3.util._
import chisel3.reflect.DataMirror

import org.chipsalliance.cde.config.Config
import freechips.rocketchip.amba.axi4.{AXI4Bundle}
import testchipip.util.{ClockedIO}
import testchipip.soc._
import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import sifive.blocks.devices.uart._
import testchipip._
import testchipip.serdes._
import testchipip.boot._
import scala.collection.immutable.ListMap
import constellation.channel._
import constellation.routing._
import constellation.router._
import constellation.topology._
import constellation.noc._
import constellation.soc.{GlobalNoCParams}
import shuttle.common._
import saturn.common.{VectorParams}
import freechips.rocketchip.util.{AsyncQueueParams}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.util._
import sifive.blocks.inclusivecache.{InclusiveCachePortParameters}

import freechips.rocketchip.devices.debug.{DebugModuleKey}
import chipyard.clocking.{ChipyardPRCIControlKey}

// ============================================================================
// Shared configuration
//
// IrisConfig and IrisDryRunConfig differ only in
//   * the SBUS NoC node mapping, and
//   * which optional blocks are present: the Rocket Saturn DMA vector unit, the
//     two Shuttle tiles with their Saturn OPU, and the UCIe chiplet router.
// Everything else is identical and lives in the fragments below.
//
// These are several fragments rather than one block because CDE composition is
// order-sensitive and the chip-specific fragments interleave with the shared
// ones:
//   * WithRocketVectorUnit rewrites the tiles WithNBigCores creates, so it must
//     sit to the LEFT of WithIrisRocketCore.
//   * The Shuttle fragments must sit to the RIGHT of WithIrisRocketCore. Tile
//     ids are handed out from `up(NumTiles)`, so this ordering is what makes the
//     Shuttle tiles harts 0-1 and Rocket hart 2 -- which the NoC mapping keys
//     off by name ("Core 0 ICache", "Core 2 DCache").
// ============================================================================

/** SBUS NoC parameters. Only the node mapping differs between the two chips;
  * topology, channel/router params, routing and the B/E width division are
  * shared.
  */
object IrisNoCParams {
  def apply(
      nodeMapping: constellation.protocol.DiplomaticNetworkNodeMapping
  ): constellation.protocol.SplitACDxBETLNoCParams =
    constellation.protocol.SplitACDxBETLNoCParams(
      nodeMapping,
      acdNoCParams = NoCParams(
        topology = BidirectionalTorus1D(7),
        channelParamGen = (a, b) =>
          UserChannelParams(
            Seq.fill(6) { UserVirtualChannelParams(5) },
            unifiedBuffer = false
          ),
        routerParams =
          (i) => UserRouterParams(combineRCVA = true, combineSAST = true),
        routingRelation = BlockingVirtualSubnetworksRouting(
          BidirectionalTorus1DShortestRouting(),
          3,
          2
        )
      ),
      beNoCParams = NoCParams(
        topology = UnidirectionalTorus1D(7),
        channelParamGen = (a, b) =>
          UserChannelParams(
            Seq.fill(4) { UserVirtualChannelParams(5) },
            unifiedBuffer = false
          ),
        routerParams =
          (i) => UserRouterParams(combineRCVA = true, combineSAST = true),
        routingRelation = BlockingVirtualSubnetworksRouting(
          UnidirectionalTorus1DDatelineRouting(),
          2,
          2
        )
      ),
      beDivision = 8
    )
}

/** Tacit trace encoder and trace sinks. */
class WithIrisTacitTrace
    extends Config(
      new tacit.WithTraceSinkDMA(1) ++
        new tacit.WithTraceSinkAlways(0) ++
        new chipyard.WithTacitTraceArbiterMonitor ++
        new chipyard.WithTacitParallelEncoder
    )

/** Widens the SBUS to 256b and drops the per-bus error devices. Sits on top of
  * the chip-specific WithSbusNoC.
  */
class WithIrisBusTweaks
    extends Config(
      new Config((site, here, up) => { case SystemBusKey =>
        up(SystemBusKey, site).copy(beatBytes = 256 / 8)
      }) ++
        new Config((site, here, up) => {
          case SystemBusKey    => up(SystemBusKey).copy(errorDevice = None)
          case ControlBusKey   => up(ControlBusKey).copy(errorDevice = None)
          case PeripheryBusKey => up(PeripheryBusKey).copy(errorDevice = None)
          case MemoryBusKey    => up(MemoryBusKey).copy(errorDevice = None)
          case FrontBusKey     => up(FrontBusKey).copy(errorDevice = None)
        })
    )

/** The Rocket tile and its L1 caches.
  *
  * NOTE: the DCache fragments sit to the RIGHT of WithNBigCores, so they apply
  * to `up(TilesLocated)` -- i.e. to tiles created below them -- and never reach
  * the Rocket tile WithNBigCores itself creates. The elaborated dcache tag array
  * is 64 sets (the DCacheParams default), not the 128 requested here; the ICache
  * fragments are to the LEFT and do take effect (128-set icache tag array).
  * Ordering preserved as-is -- moving WithNBigCores below the DCache fragments
  * would change the cache size and hence the SRAM count.
  */
class WithIrisRocketCore
    extends Config(
      // ICache
      new freechips.rocketchip.rocket.WithL1ICacheWays(2) ++
        new freechips.rocketchip.rocket.WithL1ICacheSets(128) ++
        new freechips.rocketchip.rocket.WithL1ICacheBlockBytes(64) ++
        new freechips.rocketchip.rocket.WithNBigCores(1) ++
        // DCache
        new freechips.rocketchip.rocket.WithL1DCacheBlockBytes(64) ++
        new freechips.rocketchip.rocket.WithL1DCacheSets(128) ++
        new freechips.rocketchip.rocket.WithL1DCacheWays(4)
    )

/** Everything downstream of the tiles: off-chip address range, serial TL, L2,
  * SBUS scratchpad, clocking, peripherals, debug and boot.
  *
  * @param sim
  *   expands the serial-TL phit/flit to 32b so simulation can load binaries
  *   faster, and keeps the TileLink monitors.
  */
class WithIrisUncore(sim: Boolean = false) extends Config(
      // TileLink monitors are simulation-only assertion logic; keep them out of
      // anything headed for synthesis.
      (if (sim) Parameters.empty
       else new freechips.rocketchip.subsystem.WithoutTLMonitors) ++
        new testchipip.soc.WithOffchipAddressRange(AddressSet.misaligned(0x800000000L, 0x2000000000L)) ++
        // 1 serial tilelink port
        new testchipip.serdes.WithSerialTL(
          Seq(
            testchipip.serdes.SerialTLParams(
              // port acts as a manager of offchip memory
              manager = Some(
                testchipip.serdes.SerialTLManagerParams(
                  memParams = Seq(
                    testchipip.serdes.ManagerRAMParams(
                      address = BigInt("80000000", 16),
                      size = BigInt("100000000", 16)
                    )
                  ),
                  isMemoryDevice = true,
                  slaveWhere = MBUS
                )
              ),
              // Allow an external manager to probe this chip
              client = Some(testchipip.serdes.SerialTLClientParams()),
              // 16-bit bidir interface, synced to an external clock
              phyParams = {
                val (phitWidth, flitWidth) = if (sim) {
                  (32, 32)
                } else {
                  (16, 16)
                }
                testchipip.serdes.DecoupledExternalSyncSerialPhyParams(
                  phitWidth = phitWidth,
                  flitWidth = flitWidth
                )
              }
            )
          )
        ) ++
        // Remove axi4 mem port
        new freechips.rocketchip.subsystem.WithNoMemPort ++

        // ==================================
        // Set up memory
        // ==================================
        // Adds buffers on the inclusive LLC, to improve PD
        new chipyard.config.WithInclusiveCacheInteriorBuffer ++
        new chipyard.config.WithInclusiveCacheExteriorBuffer ++

        new freechips.rocketchip.subsystem.WithInclusiveCache(
          nWays = 4,
          capacityKB = 512,
          outerLatencyCycles = 4
        ) ++
        new freechips.rocketchip.subsystem.WithNBanks(4) ++
        new testchipip.soc.WithNoScratchpadMonitors ++
        new testchipip.soc.WithScratchpad(
          base = 0x580000000L,
          size = (1L << 17), // 128KB
          banks = 2,
          partitions = 1,
          buffer = BufferParams.default,
          outerBuffer = BufferParams.default
        ) ++

      new testchipip.soc.WithNoScratchpads ++
      new chipyard.config.AbstractConfig
    )


/** Digital chip configuration.
  *
  * Simulation flag expands tilelink bus to allow faster binary loading.
  */
class IrisConfig extends Config(
      new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
      new WithIrisTacitTrace ++

        // ==================================
        // Set up buses
        // ==================================
        new constellation.soc.WithSbusNoC(
          IrisNoCParams(
            constellation.protocol.DiplomaticNetworkNodeMapping(
              inNodeMapping = ListMap(
                "Core 0 ICache" -> 0, // Shuttle 0 (left)
                "Core 1 ICache" -> 2, // Shuttle 1 (right)
                "serial_tl_0_0[0]" -> 6, // Front BUS
                "Core 2 DCache" -> 4, // RocketTile
                "ucie-client" -> 5
              ),
              outNodeMapping = ListMap(
                "Core 0 TCM" -> 0, // Shuttle 0 TCM (left)
                "Core 1 TCM" -> 2, // Shuttle 1 TCM (right)
                "ctrls[0]" -> 6, // PBUS
                "serdesser[2]|" -> 3, // L2   (top)
                "serdesser[3]|" -> 3, // L2   (top)
                "serdesser[1]|" -> 3, // L2   (bottom)
                "serdesser[0]|" -> 3, // L2   (bottom)
                "ucie[0]" -> 5, // UCie 0
                "ram[0]|" -> 1, // SBUS SPAD (?)
                "ram[1]|" -> 1 // Also SBUS SPAD?
              )
            )
          ),
          inlineNoC = true
        ) ++
        // new WithIrisBusTweaks ++

        // ==================================
        // Rocket
        // ==================================
        // Saturn DMA
        new saturn.rocket.WithRocketVectorUnit(256, 256, VectorParams.dmaParams) ++
        new WithIrisRocketCore ++

        // ==================================
        // Shuttle Tile + Saturn Cores
        // ==================================
        // new shuttle.common.WithAsynchronousShuttleTiles(3, 3, location=InCluster(0)) ++ // Add async crossings between RocketTile and uncore
        new saturn.shuttle.WithShuttleVectorUnit(
          256,
          128,
          VectorParams.opuMxParams
        ) ++
        new shuttle.common.WithShuttleTileBeatBytes(32) ++
        new shuttle.common.WithTCM(size = 128L << 10, banks = 2) ++
        new shuttle.common.WithShuttleTileBoundaryBuffers() ++
        // ICache
        new shuttle.common.WithL1ICacheWays(2) ++
        new shuttle.common.WithL1ICacheSets(64) ++
        // DCache
        new shuttle.common.WithL1DCacheWays(2) ++
        // new shuttle.common.WithL1DCacheSets(256) ++
        new shuttle.common.WithL1DCacheBanks(1) ++
        new shuttle.common.WithL1DCacheTagBanks(1) ++
        new shuttle.common.WithNShuttleCores(2) ++

        // Chiplet Router with two D2D SerialTL ports and two D2D UCIe ports
        // new testchipip.soc.WithChipletRouting(testchipip.soc.ChipletRoutingParams(
        //   routerParams = testchipip.soc.OffchipRouterParams(tableEntries = 4),
        //   ports = Seq(
        //     // PD hardens the two UCIe links together, so UcieComplexPort puts
        //     // both instances inside one UcieComplex module. They are identical
        //     // logic and share a `moduleId`, so they dedup into a single UcieTL
        //     // underneath it; splitting them again is a matter of giving one its
        //     // own `moduleId`.
        //     UcieComplexPort(edu.berkeley.cs.uciedigital.tilelink.UcieTLParams(
        //       address = 0x200000,
        //       managerWhere = SBUS,
        //       numLanes = 16,
        //       includeDefaultModels = true
        //     )),
        //     UcieComplexPort(edu.berkeley.cs.uciedigital.tilelink.UcieTLParams(
        //       address = 0x208000,
        //       managerWhere = SBUS,
        //       numLanes = 16,
        //       includeDefaultModels = true
        //     ))
        // ))) ++
        new WithIrisUncore
    )
class nonFSWithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map(_.copy(clockGate = false))
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey).copy(enableTileClockGating = false)
})

class IrisAlmostFiresimConfig extends Config(
  // new WithFireSimHarnessClockBridgeInstantiator ++
  // new chipyard.harness.WithHarnessBinderClockFreqMHz(1000.0) ++
  new chipyard.harness.WithClockFromHarness ++
  new chipyard.harness.WithResetFromHarness ++
  new chipyard.config.WithNoClockTap ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  // Required: Existing FAME-1 transform cannot handle black-box clock gates
  new nonFSWithoutClockGating ++
  // Optional: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
  new chipyard.config.WithNoDebug ++
  new chipyard.config.WithNoClockTap ++
  // new chipyard.config.WithUART(
  //   baudrate=BigInt(3686400L),
  //   txEntries=256, rxEntries=256) ++        // FireSim requires a larger UART FIFO buffer,
  // new chipyard.config.WithNoUART() ++       // so we overwrite the default one

  new chipyard.IrisConfig
)
