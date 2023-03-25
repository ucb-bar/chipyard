package chipyard

import freechips.rocketchip.config.{Config}
import constellation.channel._
import constellation.routing._
import constellation.topology._
import constellation.noc._
import constellation.soc.{GlobalNoCParams}
import scala.collection.immutable.ListMap

// This file is designed to accompany a live tutorial, with slides.
// For each of 4 phases, participants will customize and build a
// small demonstration config.

// This file is designed to be used after running chipyard/scripts/tutorial-setup.sh,
// which removes the SHA3 accelerator RTL, and provides participants
// the experience of integrating external RTL.

// This file was originally developed for the cancelled ASPLOS-2020
// Chipyard tutorial. While the configs here work, the corresponding
// slideware has not yet been created.

// NOTE: Configs should be read bottom-up, since they are applied bottom-up

// NOTE: The TutorialConfigs build off of the AbstractConfig defined in AbstractConfig.scala
//       Users should try to understand the functionality of the AbstractConfig before proceeding
//       with the TutorialConfigs below

// Tutorial Phase 1: Configure the cores, caches
class TutorialStarterConfig extends Config(
  // CUSTOMIZE THE CORE
  // Uncomment out one (or multiple) of the lines below, and choose
  // how many cores you want.
  // new freechips.rocketchip.subsystem.WithNBigCores(1) ++    // Specify we want some number of Rocket cores
  // new boom.common.WithNSmallBooms(1) ++                     // Specify we want some number of BOOM cores

  // CUSTOMIZE the L2
  // Uncomment this line, and specify a size if you want to have a L2
  // new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=1, nWays=4, capacityKB=128) ++

  new chipyard.config.AbstractConfig
)

// Tutorial Phase 2: Integrate a TileLink or AXI4 MMIO device
class TutorialMMIOConfig extends Config(

  // Attach either a TileLink or AXI4 version of GCD
  // Uncomment one of the below lines
  // new chipyard.example.WithGCD(useAXI4=false) ++ // Use TileLink version
  // new chipyard.example.WithGCD(useAXI4=true) ++  // Use AXI4 version

  // For this demonstration we assume the base system is a single-core Rocket, for fast elaboration
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig
)

// Tutorial Phase 3: Integrate a SHA3 RoCC accelerator
class TutorialSha3Config extends Config(
  // Uncomment this line once you added SHA3 to the build.sbt, and cloned the SHA3 repo
  // new sha3.WithSha3Accel ++

  // For this demonstration we assume the base system is a single-core Rocket, for fast elaboration
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig
)

// Tutorial Phase 4: Integrate a Black-box verilog version of the SHA3 RoCC accelerator
class TutorialSha3BlackBoxConfig extends Config(
  // Uncomment these lines once SHA3 is integrated
  // new sha3.WithSha3BlackBox ++ // Specify we want the Black-box verilog version of Sha3 Ctrl
  // new sha3.WithSha3Accel ++

  // For this demonstration we assume the base system is a single-core Rocket, for fast elaboration
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig
)

// Tutorial Phase 5: Map a multicore heterogeneous SoC with multiple cores and memory-mapped accelerators
class TutorialNoCConfig extends Config(
  // Try changing the dimensions of the Mesh topology
  new constellation.soc.WithGlobalNoC(constellation.soc.GlobalNoCParams(
    NoCParams(
      topology        = TerminalRouter(Mesh2D(3, 4)),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(12) { UserVirtualChannelParams(4) }),
      routingRelation = NonblockingVirtualSubnetworksRouting(TerminalRouterRouting(
        Mesh2DEscapeRouting()), 10, 1)
    )
  )) ++
  // The inNodeMapping and outNodeMapping values are the physical identifiers of
  // routers on the topology to map the agents to. Try changing these to any
  // value within the range [0, topology.nNodes)
  new constellation.soc.WithPbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap("Core" -> 7),
      outNodeMapping = ListMap(
        "pbus" -> 8, "uart" -> 9, "control" -> 10, "gcd" -> 11,
        "writeQueue[0]" -> 0, "writeQueue[1]" -> 1, "tailChain[0]" -> 2))
  ), true) ++
  new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "Core 0" -> 0, "Core 1" -> 1,
        "serial-tl" -> 2),
      outNodeMapping = ListMap(
        "system[0]" -> 3, "system[1]" -> 4, "system[2]" -> 5, "system[3]" -> 6,
        "pbus" -> 7))
  ), true) ++
  new chipyard.example.WithGCD ++
  new chipyard.harness.WithLoopbackNIC ++
  new icenet.WithIceNIC ++
  new fftgenerator.WithFFTGenerator(numPoints=8) ++
  new chipyard.example.WithStreamingFIR ++
  new chipyard.example.WithStreamingPassthrough ++

  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig
)

// Tutorial Phase 6: Gemmini Config
class TutorialLeanGemminiConfig extends Config(
  // Step 1: Customize gemmini - set a config option for the accelerator: use_dedicated_tl_port=false
  new gemmini.DefaultGemminiConfig(gemmini.GemminiConfigs.leanConfig.copy(use_dedicated_tl_port=true )) ++

  // Step 2: Specify some number of Rocket + Boom cores
  //         For this step, the total number of Rocket + Boom cores should <= 8
  new boom.common.WithNMediumBooms(1) ++
  new freechips.rocketchip.subsystem.WithNBigCores(3) ++

  // : Some number of L2 cache banks (keep this <= 4 as well)
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig
)

// Tutorial Phase 6: Many Core SoC on a NoC
class TutorialManyCoreNoCConfig extends Config(
  new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      // inNodeMappings map master agents onto the NoC
      inNodeMapping = ListMap(
        "Core 0 " ->  1, "Core 1 " ->  2, "Core 2 " ->  3, "Core 3 " ->  4,

        "Core 4 " ->  7, "Core 5 " ->  7, "Core 6 " ->  8, "Core 7 " ->  8,
        "Core 8 " ->  9, "Core 9 " ->  9, "Core 10" -> 10, "Core 11" -> 10,
        "Core 12" -> 13, "Core 13" -> 13, "Core 14" -> 14, "Core 15" -> 14,
        "Core 16" -> 15, "Core 17" -> 15, "Core 18" -> 16, "Core 19" -> 16,

        "Core 20" ->  0, "Core 21" ->  6, "Core 22" -> 12, "Core 23" -> 18,
        "Core 24" ->  5, "Core 25" -> 11, "Core 26" -> 17, "Core 27" -> 23,
        "serial-tl" -> 0),
      // outNodeMappings map client agents (L2 banks) onto the NoC
      outNodeMapping = ListMap(
        "system[0]"  ->  7, "system[1]"  ->  8, "system[2]"  ->  9, "system[3]"  -> 10,
        "system[4]"  -> 13, "system[5]"  -> 14, "system[6]"  -> 15, "system[7]"  -> 16,
        "pbus" -> 5)),
    NoCParams(
      topology        = TerminalRouter(Mesh2D(6, 4)),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(8) { UserVirtualChannelParams(4) }),
      routingRelation = BlockingVirtualSubnetworksRouting(TerminalRouterRouting(Mesh2DEscapeRouting()), 5, 1),
      skipValidationChecks = true
    )
  )) ++
  // ==========================================
  // DO NOT change below this line without    |
  // carefully adjusting the NoC config above |
  // ==========================================

  // add LeanGemmini to Rocket-cores 0-3 (along the bottom edge of the topology)
  new chipyard.config.WithMultiRoCC ++
  new chipyard.config.WithMultiRoCCFromBuildRoCC(0, 1, 2, 3) ++
  new gemmini.DefaultGemminiConfig(gemmini.GemminiConfigs.leanConfig.copy(use_dedicated_tl_port=false)) ++

  // Add 8 duplicated 10-wide "Mega" SonicBoom cores along the left/right edges
  new boom.common.WithCloneBoomTiles(7, 20) ++
  new boom.common.WithNMegaBooms(1) ++

  // Add 16 duplicated simple RocketCores the the center region
  new freechips.rocketchip.subsystem.WithCloneRocketTiles(15, 4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++

  // Add 4 duplicated RocketCores along the bottom edge (these will hold the LeanGemmini accelerators)
  new freechips.rocketchip.subsystem.WithCloneRocketTiles(3, 0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++

  // Use 8 banks of L2 cache
  new freechips.rocketchip.subsystem.WithNBanks(8) ++

  new chipyard.config.AbstractConfig
)
