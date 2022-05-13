package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{SBUS, MBUS}
import constellation.channel.{UserVirtualChannelParams}
import constellation.routing._
import constellation.topology._


/*
 * This config demonstrates a 4x4 Mesh NoC topology implemented
 * with Constellation. The SystemBus and MemoryBus are mapped to a
 * shared network, while the ControlBus is mapped to an independent
 * narrow network.
 * Mapping of nodes onto the topology is performed via string-matching
 * on the diplomatic names for the edges into each bus. Unfortunately
 * these strings are not generated with obvious names, so the mapping
 * is a little obtuse.
 *
 * As with all other CDE Configs, the configuration is built up from
 * bottom-up.
 *
 * This table describes the mappings of each edge onto the network
 *
 * SI/SO: Inward/outward names into sbus
 * MI/MO: Inward/otward names into mbus
 *
 *   DRAM 0       | DRAM 1       | DRAM 2       | DRAM 3
 *   MO:system[0] | MO:system[1] | MO:system[2] | MO:system[3]
 *   MO:serdesser |              |              |
 *   _____________|______________|______________|_____________
 *   Core1        | Core2        | Core3        | Core4
 *   SI:Core 1    | SI:Core 2    | SI:Core 3    | SI:Core 4
 *                |              |              |
 *   _____________|______________|______________|_____________
 *                | L2_0         | L2_1         | Core0+Pbus
 *                | SO:system[0] | SO:system[1] | SI:Core 0
 *                | MI:L2[0]     | MI:L2[1]     | SO:pbus
 *   _____________|______________|______________|_____________
 *   FBus         | L2_4         | L2_3         |
 *   SI:serial-tl | SO:System[2] | SO:system[3] |
 *                | MI:L2[2]     | MI:L2[3]     |
 */
class BigNoCConfig extends Config(
  // Map the inwards and outwards edges of the cbus to cbus NoC nodes
  new constellation.rc.WithCBusNoCGlobalNoCCtrlMapping((i) => i % 16) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("chipyardPRCI[1]", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("chipyardPRCI[0]", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("bootrom", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("dmInner", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("clint", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("plic", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("pbus", 7) ++
  new constellation.rc.WithCbusNoCOutNodeMapping("l2", 7) ++ // TODO fix this should be per L2 bank
  new constellation.rc.WithCbusNoCOutNodeMapping("error", 7) ++
  new constellation.rc.WithCbusNoCInNodeMapping("", 7) ++

  // Replce the Xbar-based cbus with a NoC based cbus (and force a narrow width NoC)
  new constellation.rc.WithCbusNoC(explicitWidth = Some(16)) ++

  // Configure the physical resources of the lightweight control interconnect
  // 4x4 2D mesh with support for 5 nonblocking virtual subnetworks
  // Cbus -> 5 subnets for TL ABCDE protocol channels (BCE are optimized away)
  new constellation.routing.WithNNonblockingVirtualNetworks(5) ++
  new constellation.channel.WithUniformNVirtualChannels(5, UserVirtualChannelParams(4)) ++
  new constellation.routing.WithTerminalPlaneRouting ++
  new constellation.routing.WithRoutingRelation(new Mesh2DDimensionOrderedRouting(4, 4)) ++
  new constellation.topology.WithTerminalPlane ++
  new constellation.topology.WithTopology(new Mesh2D(4, 4)) ++

  // Indicate that the mbus and sbus nocs should be implemented
  // by the shared global noc. Global noc payload width needs to be
  // wide enough to to support 256-b wide sbus width
  new constellation.rc.WithGlobalNoCWidth(300) ++
  new constellation.rc.WithMbusGlobalNoC ++
  new constellation.rc.WithSbusGlobalNoC ++
  new constellation.noc.WithNoParamValidation ++

  // Configure the physical resources of the main global data interconnect
  // 4x4 2D mesh with escape-channel routing + support for 10 nonblocking virtual subnetworks
  // Sbus -> 5 subnets for TL ABCDE protocol channels
  // Mbus -> 5 subnets for TL ABCDE protocol channels (BCE are optimized away)
  new constellation.channel.WithUniformNVirtualChannels(13, UserVirtualChannelParams(7)) ++
  new constellation.routing.WithNNonblockingVirtualNetworksWithSharing(10, 3) ++
  new constellation.routing.WithTerminalPlaneRouting ++
  new constellation.routing.WithRoutingRelation(new Mesh2DEscapeRouting(4, 4)) ++
  new constellation.topology.WithTerminalPlane ++
  new constellation.topology.WithTopology(new Mesh2D(4, 4)) ++


  // Map the inwards and outwards edges of the mbus to topology nodes
  new constellation.rc.WithMbusNoCOutNodeMapping("serdesser", 12) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[0]", 12) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[1]", 13) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[2]", 14) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[3]", 15) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[3]", 2) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[2]", 1) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[1]", 6) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[0]", 5) ++

  // Replace the Xbar-based mbus with a NoC based mbus
  new constellation.rc.WithMbusNoC ++

  // Map the inwards and outwards edges of the sbus to topology nodes
  new constellation.rc.WithSbusNoCOutNodeMapping("system[3]", 2) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[2]", 1) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[1]", 6) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[0]", 5) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("pbus"     , 7) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 4"   , 11) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 3"   , 10) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 2"   , 9) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 1"   , 8) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 0"   , 7) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("serial-tl", 0) ++

  // Replace the XBar-based sbus with a NoC based sbus
  new constellation.rc.WithSbusNoC ++

  new chipyard.config.WithSystemBusWidth(256) ++

  // Cores 1-4 are standard processing cores
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++

  // Core 0 is small control core, minimize cache
  new freechips.rocketchip.subsystem.WithL1ICacheSets(64) ++
  new freechips.rocketchip.subsystem.WithL1ICacheWays(1) ++
  new freechips.rocketchip.subsystem.WithL1DCacheSets(64) ++
  new freechips.rocketchip.subsystem.WithL1DCacheWays(1) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++

  // 4 L2 banks
  new freechips.rocketchip.subsystem.WithNBanks(4)++


  // 4 DRAM channels
  new freechips.rocketchip.subsystem.WithNMemoryChannels(4) ++

  new chipyard.config.AbstractConfig)

