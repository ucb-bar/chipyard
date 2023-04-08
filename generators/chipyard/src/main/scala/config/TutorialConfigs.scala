package chipyard

import org.chipsalliance.cde.config.{Config}
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
  new chipyard.iobinders.WithDontTouchIOBinders(false) ++
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
