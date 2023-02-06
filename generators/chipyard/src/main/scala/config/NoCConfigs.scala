package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import freechips.rocketchip.subsystem.{SBUS, MBUS}

import constellation.channel._
import constellation.routing._
import constellation.topology._
import constellation.noc._
import constellation.soc.{GlobalNoCParams}

import scala.collection.immutable.ListMap

/*
 * This config demonstrates a SoC architecture with three different
 * independent interconnects. The SBus interconnect is the main
 * backbone, handling core traffic to the L2s and peripherals.
 * The MBus handles requests to the DRAM and external memory.
 * The CBus handles requests to control devices.
 *
 * Topologies:
 *
 * 12 - 13 - 14 - 15
 *  |    |    |    |
 *  8 -- 9 - 10 - 11    0 -- 1 -- 2 -- 3
 *  |    |    |    |    |              |
 *  4 -- 5 -- 6 -- 7    7 -- 6 -- 5 -- 4
 *  |    |    |    |
 *  0 -- 1 -- 2 -- 3
 *
 * This table describes the mappings of each edge onto the network
 *
 * SI/SO: Inward/outward names into sbus
 * MI/MO: Inward/outward names into mbus
 *
 *   |(12)__________|(13)__________|(14)__________|(15)__________|
 *   |              | Core 6       | Core 7       |              |
 *   |              | SI:Core 6    | SI:Core 7    |              |
 *   |(8)___________|(9)___________|(10)__________|(11)__________|
 *   | Core 4       | L2 2         | L2 3         | Core 5       |
 *   | SI:Core 4    | S0:system[2] | SO:system[3] | SI:Core 5    |
 *   |(4)___________|(5)___________|(6)___________|(7)___________|
 *   | Core 2       | L2 0         | L2 1         | Core 3       |
 *   | SI:Core 2    | SO:system[0] | SO:system[1] | SI:Core 3    |
 *   |(0)___________|(1)___________|(2)___________|(3)___________|
 *   | FBus         | Core 0       | Core 1       | Pbus         |
 *   | SI:serial-tl | SI:Core 0    | SI:Core 1    | SO:pbus      |
 *   |______________|______________|______________|______________|
 *
 *   |(0)___________|(1)___________|(2)___________|(3)___________|
 *   | DRAM 0       | L2 0         | L2 1         | DRAM 1       |
 *   | M0:system[0] | MI:L2[0]     | MI:L2[1]     | M0:system[1] |
 *   | M0:serdesser |              |              |              |
 *   |______________|______________|______________|______________|
 *    ||||||||||||||                               ||||||||||||||
 *   |(7)___________|(6)___________|(5)___________|(4)___________|
 *   | DRAM 2       | L2 2         | L2 3         | DRAM 3       |
 *   | M0:system[2] | MI:L2[2]     | MI:L2[3]     | M0:system[3] |
 *   |              |              |              |              |
 *   |______________|______________|______________|______________|
 */
// DOC include start: MultiNoCConfig
class MultiNoCConfig extends Config(
  new constellation.soc.WithCbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "serial-tl" -> 0),
      outNodeMapping = ListMap(
        "error" -> 1, "l2[0]" -> 2, "pbus" -> 3, "plic" -> 4,
        "clint" -> 5, "dmInner" -> 6, "bootrom" -> 7, "tileClockGater" -> 8, "tileResetSetter" -> 9)),
    NoCParams(
      topology = TerminalRouter(BidirectionalLine(10)),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(5) { UserVirtualChannelParams(4) }),
      routingRelation = NonblockingVirtualSubnetworksRouting(TerminalRouterRouting(BidirectionalLineRouting()), 5, 1))
  )) ++
  new constellation.soc.WithMbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "L2 InclusiveCache[0]" -> 1, "L2 InclusiveCache[1]" -> 2,
        "L2 InclusiveCache[2]" -> 5, "L2 InclusiveCache[3]" -> 6),
      outNodeMapping = ListMap(
        "system[0]" -> 0, "system[1]" -> 3,  "system[2]" -> 4 , "system[3]" -> 7,
        "serdesser" -> 0)),
    NoCParams(
      topology        = TerminalRouter(BidirectionalTorus1D(8)),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(10) { UserVirtualChannelParams(4) }),
      routingRelation = BlockingVirtualSubnetworksRouting(TerminalRouterRouting(BidirectionalTorus1DShortestRouting()), 5, 2))
  )) ++
  new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "Core 0" -> 1, "Core 1" -> 2,  "Core 2" -> 4 , "Core 3" -> 7,
        "Core 4" -> 8, "Core 5" -> 11, "Core 6" -> 13, "Core 7" -> 14,
        "serial-tl" -> 0),
      outNodeMapping = ListMap(
        "system[0]" -> 5, "system[1]" -> 6, "system[2]" -> 9, "system[3]" -> 10,
        "pbus" -> 3)),
    NoCParams(
      topology        = TerminalRouter(Mesh2D(4, 4)),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(8) { UserVirtualChannelParams(4) }),
      routingRelation = BlockingVirtualSubnetworksRouting(TerminalRouterRouting(Mesh2DEscapeRouting()), 5, 1))
  )) ++
  new freechips.rocketchip.subsystem.WithNBigCores(8) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(4) ++
  new chipyard.config.AbstractConfig
)
// DOC include end: MultiNoCConfig

/*
 * 10 - 11 - 12 - 13 - 14
 *            |
 *      0 --- 1 --- 2 --- 3
 *      |                 |
 *      9                 4
 *      |                 |
 *      8 --- 7 --- 6 --- 5
 *            |
 * 15 - 16 - 17 - 18 - 19
 *
 * SI/SO: Inward/outward names into sbus
 * MI/MO: Inward/outward names into mbus
 *
 * Agent  | Bus | String     | node
 * ================================
 * Core 0 | SI  | Core 0     |    2
 * Core 1 | SI  | Core 1     |   10
 * Core 2 | SI  | Core 2     |   11
 * Core 3 | SI  | Core 3     |   13
 * Core 4 | SI  | Core 4     |   14
 * Core 5 | SI  | Core 5     |   15
 * Core 6 | SI  | Core 6     |   16
 * Core 7 | SI  | Core 7     |   18
 * Core 8 | SI  | Core 8     |   19
 * fbus   | SI  | serial-tl  |    9
 * pbus   | SO  | pbus       |    4
 * L2 0   | SO  | system[0]  |    0
 * L2 1   | SO  | system[1]  |    2
 * L2 2   | SO  | system[2]  |    8
 * L2 3   | SO  | system[3]  |    6
 * L2 0   | MI  | Cache[0]   |    0
 * L2 1   | MI  | Cache[1]   |    2
 * L2 2   | MI  | Cache[2]   |    8
 * L2 3   | MI  | Cache[3]   |    6
 * DRAM 0 | MO  | system[0]  |    3
 * DRAM 1 | MO  | system[1]  |    5
 * extram | MO  | serdesser  |    9
 */
// DOC include start: SharedNoCConfig
class SharedNoCConfig extends Config(
  new constellation.soc.WithGlobalNoC(GlobalNoCParams(
    NoCParams(
      topology        = TerminalRouter(HierarchicalTopology(
        base     = UnidirectionalTorus1D(10),
        children = Seq(HierarchicalSubTopology(1, 2, BidirectionalLine(5)),
                       HierarchicalSubTopology(7, 2, BidirectionalLine(5))))),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(22) { UserVirtualChannelParams(4) }),
      routingRelation = NonblockingVirtualSubnetworksRouting(TerminalRouterRouting(HierarchicalRouting(
        baseRouting = UnidirectionalTorus1DDatelineRouting(),
        childRouting = Seq(BidirectionalLineRouting(),
                           BidirectionalLineRouting()))), 10, 2)
    )
  )) ++
  new constellation.soc.WithMbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "Cache[0]" -> 0, "Cache[1]" -> 2, "Cache[2]" -> 8, "Cache[3]" -> 6),
      outNodeMapping = ListMap(
        "system[0]" -> 3, "system[1]" -> 5,
        "serdesser" -> 9))
  ), true) ++
  new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "serial-tl" -> 9, "Core 0" -> 2,
        "Core 1" -> 10, "Core 2" -> 11, "Core 3" -> 13, "Core 4" -> 14,
        "Core 5" -> 15, "Core 6" -> 16, "Core 7" -> 18, "Core 8" -> 19),
      outNodeMapping = ListMap(
        "system[0]" -> 0, "system[1]" -> 2, "system[2]" -> 8, "system[3]" -> 6,
        "pbus" -> 4))
  ), true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(8) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new chipyard.config.AbstractConfig
)
// DOC include end: SharedNoCConfig

class SbusRingNoCConfig extends Config(
  new constellation.soc.WithSbusNoC(constellation.protocol.TLNoCParams(
    constellation.protocol.DiplomaticNetworkNodeMapping(
      inNodeMapping = ListMap(
        "Core 0" -> 0,
        "Core 1" -> 1,
        "Core 2" -> 2,
        "Core 3" -> 3,
        "Core 4" -> 4,
        "Core 5" -> 5,
        "Core 6" -> 6,
        "Core 7" -> 7,
        "serial-tl" -> 8),
      outNodeMapping = ListMap(
        "system[0]" -> 9,
        "system[1]" -> 10,
        "system[2]" -> 11,
        "system[3]" -> 12,
        "pbus" -> 8)), // TSI is on the pbus, so serial-tl and pbus should be on the same node
    NoCParams(
      topology        = UnidirectionalTorus1D(13),
      channelParamGen = (a, b) => UserChannelParams(Seq.fill(10) { UserVirtualChannelParams(4) }),
      routingRelation = NonblockingVirtualSubnetworksRouting(UnidirectionalTorus1DDatelineRouting(), 5, 2))
  )) ++
  new freechips.rocketchip.subsystem.WithNBigCores(8) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig
)
