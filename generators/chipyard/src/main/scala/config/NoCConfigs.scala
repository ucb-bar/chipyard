package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{SBUS, MBUS}
import constellation.channel.{UserVirtualChannelParams}
import constellation.routing._
import constellation.topology._


/*
 * SI/SO: Inward/outward names into sbus
 * MI/MO: Inward/otward names into mbus
 *
 *   DRAM 0       | DRAM 1       | DRAM 2       | DRAM 3
 *   MO:system[0] | MO:system[1] | MO:system[2] | MO:system[3]
 *   MO:serdesser |              |              |
 *   _____________|______________|______________|_____________
 *                |              |              |
 *                |              |              |
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
  new constellation.rc.WithNbusNoC(7, i => i % 16, Some(16)) ++
  new constellation.routing.WithNNonblockingVirtualNetworks(5) ++
  new constellation.channel.WithUniformNVirtualChannels(5, UserVirtualChannelParams(4)) ++
  new constellation.routing.WithTerminalPlaneRouting ++
  new constellation.routing.WithRoutingRelation(new Mesh2DDimensionOrderedRouting(4, 4)) ++
  new constellation.topology.WithTerminalPlane ++
  new constellation.topology.WithTopology(new Mesh2D(4, 4)) ++

  new constellation.rc.WithGlobalNoCWidth(300) ++
  new constellation.rc.WithMbusGlobalNoC ++
  new constellation.rc.WithSbusGlobalNoC ++
  new constellation.noc.WithNoParamValidation ++

  new constellation.channel.WithUniformNVirtualChannels(13, UserVirtualChannelParams(5)) ++
  new constellation.routing.WithNNonblockingVirtualNetworksWithSharing(10, 3) ++
  new constellation.routing.WithTerminalPlaneRouting ++
  new constellation.routing.WithRoutingRelation(new Mesh2DEscapeRouting(4, 4)) ++
  new constellation.topology.WithTerminalPlane ++
  new constellation.topology.WithTopology(new Mesh2D(4, 4)) ++


  new constellation.rc.WithMbusNoCOutNodeMapping("serdesser", 12) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[0]", 12) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[1]", 13) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[2]", 14) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[3]", 15) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[3]", 2) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[2]", 1) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[1]", 6) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[0]", 5) ++
  new constellation.rc.WithMbusNoC ++

  new constellation.rc.WithSbusNoCOutNodeMapping("system[3]", 2) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[2]", 1) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[1]", 6) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[0]", 5) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("pbus"     , 7) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 4"   , 2) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 3"   , 1) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 2"   , 6) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 1"   , 5) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 0"   , 7) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("serial-tl", 0) ++
  new constellation.rc.WithSbusNoC ++

  new chipyard.config.WithSystemBusWidth(256) ++
  // Cores 1-4 are interior
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
