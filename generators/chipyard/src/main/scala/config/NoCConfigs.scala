package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{SBUS, MBUS}
import constellation.channel.{UserVirtualChannelParams}
import constellation.routing.{RoutingRelation}


class DualRocketNoCConfig extends Config(
  new constellation.rc.WithNbusNoC(1, i => i % 10) ++
  new constellation.routing.WithNNonblockingVirtualNetworks(5) ++
  new constellation.noc.WithTerminalPlane ++
  new constellation.channel.WithUniformNVirtualChannels(5, UserVirtualChannelParams(5)) ++
  new constellation.topology.WithMesh2DTopology(2, 5, RoutingRelation.mesh2DDimensionOrdered()) ++

  new constellation.rc.WithMbusGlobalNoC ++
  new constellation.rc.WithSbusGlobalNoC ++
  new constellation.routing.WithNNonblockingVirtualNetworksWithSharing(10, 3) ++
  new constellation.noc.WithTerminalPlane ++
  new constellation.channel.WithUniformNVirtualChannels(13, UserVirtualChannelParams(5)) ++
  new constellation.topology.WithMesh2DTopology(2, 5, RoutingRelation.mesh2DEscapeRouter) ++


  new constellation.rc.WithMbusNoCOutNodeMapping("serdesser", 9) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system", 8) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[3]", 7) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[2]", 6) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[1]", 5) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[0]", 4) ++
  new constellation.rc.WithMbusNoC ++

  new constellation.rc.WithSbusNoCOutNodeMapping("system[3]", 7) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[2]", 6) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[1]", 5) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[0]", 4) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("pbus"     , 1) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 1"   , 3) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 0"   , 2) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("serial-tl", 0) ++
  new constellation.rc.WithSbusNoC ++

  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)

class DecaRocketNoCConfig extends Config(
  new constellation.rc.WithNbusNoC(11, i => i % 16) ++
  new constellation.routing.WithNNonblockingVirtualNetworks(5) ++
  new constellation.noc.WithTerminalPlane ++
  new constellation.channel.WithUniformNVirtualChannels(5, UserVirtualChannelParams(5)) ++
  new constellation.topology.WithMesh2DTopology(4, 4, RoutingRelation.mesh2DDimensionOrdered()) ++

  new constellation.rc.WithMbusGlobalNoC ++
  new constellation.rc.WithSbusGlobalNoC ++
  new constellation.noc.WithNoParamValidation ++
  new constellation.routing.WithNNonblockingVirtualNetworksWithSharing(10, 3) ++
  new constellation.noc.WithTerminalPlane ++
  new constellation.channel.WithUniformNVirtualChannels(13, UserVirtualChannelParams(5)) ++
  new constellation.topology.WithMesh2DTopology(4, 4, RoutingRelation.mesh2DEscapeRouter) ++


  new constellation.rc.WithMbusNoCOutNodeMapping("serdesser",  9) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[3]", 15) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[2]", 14) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[1]", 13) ++
  new constellation.rc.WithMbusNoCOutNodeMapping("system[0]", 12) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[7]", 7) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[6]", 6) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[5]", 5) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[4]", 4) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[3]", 3) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[2]", 2) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[1]", 1) ++
  new constellation.rc.WithMbusNoCInNodeMapping("L2 InclusiveCache[0]", 0) ++
  new constellation.rc.WithMbusNoC ++

  new constellation.rc.WithSbusNoCOutNodeMapping("system[7]",  7) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[6]",  6) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[5]",  5) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[4]",  4) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[3]",  3) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[2]",  2) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[1]",  1) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("system[0]",  0) ++
  new constellation.rc.WithSbusNoCOutNodeMapping("pbus"     , 11) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 9"   ,  7) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 8"   ,  6) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 7"   ,  5) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 6"   ,  4) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 5"   ,  3) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 4"   ,  2) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 3"   ,  1) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 2"   ,  0) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 1"   , 10) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("Core 0"   ,  9) ++
  new constellation.rc.WithSbusNoCInNodeMapping ("serial-tl",  8) ++

  new constellation.rc.WithSbusNoC ++

  new freechips.rocketchip.subsystem.WithNBigCores(10) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(4) ++
  new freechips.rocketchip.subsystem.WithNBanks(8) ++
  new chipyard.config.AbstractConfig
)
